package ca.emilybelnavis.mcmods.economy;

import java.util.*;
import ca.emilybelnavis.mcmods.economy.api.EconomyApi;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class RegisterEntity extends BlockEntity {
    private static final Set<UUID> BUYERS = new HashSet<>();
    private boolean busy;
    private UUID owner;
    private ItemStack product = ItemStack.EMPTY;
    private long price;
    public static void clearReservations() { BUYERS.clear(); }
    public RegisterEntity(BlockPos pos, BlockState state) { super(Economy.REGISTER_ENTITY.get(), pos, state); }
    public void setOwner(UUID owner) { this.owner = owner; setChanged(); }
    private void requireOwner(ServerPlayer player) {
        if (!player.getUUID().equals(owner)) throw new IllegalStateException("Only the shop owner can configure this register.");
    }
    public void configure(ServerPlayer player) {
        requireOwner(player);
        if (busy) throw new IllegalStateException("A purchase is still being processed.");
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) { product = held.copy(); price = 0; setChanged(); }
        player.sendSystemMessage(Component.translatable("message.economy.shop_config", product.isEmpty() ? Component.translatable("message.economy.unset") : Component.translatable("message.economy.product", product.getCount(), product.getHoverName())));
    }
    public void setPrice(ServerPlayer player, long price) {
        requireOwner(player);
        if (busy) throw new IllegalStateException("A purchase is still being processed.");
        if (product.isEmpty()) throw new IllegalStateException("Sneak-use the register holding the product stack first.");
        if (price <= 0) throw new IllegalArgumentException("Price must be positive.");
        this.price = price; setChanged();
    }
    public void buy(ServerPlayer buyer) {
        if (busy || BUYERS.contains(buyer.getUUID())) throw new IllegalStateException("A purchase is still being processed.");
        if (owner == null || product.isEmpty() || price <= 0) throw new IllegalStateException("Shop is not configured. Owner: sneak-use with the product, then /economy shop price <units>.");
        if (buyer.getUUID().equals(owner)) {
            buyer.sendSystemMessage(Component.translatable("message.economy.your_shop", product.getCount(), product.getHoverName(), price)); return;
        }
        if (!(level.getBlockEntity(worldPosition.below()) instanceof Container stock)) throw new IllegalStateException("No stock container below the register.");
        // Validate capacity and reserve stock on the main thread before queuing payment.
        List<ItemStack> original = new ArrayList<>();
        List<ItemStack> remaining = new ArrayList<>();
        int needed = product.getCount();
        for (int i=0;i<stock.getContainerSize();i++) {
            ItemStack stack = stock.getItem(i).copy();
            original.add(stack.copy());
            if (ItemStack.isSameItemSameComponents(stack, product)) { int take=Math.min(needed, stack.getCount()); stack.shrink(take); needed-=take; }
            remaining.add(stack);
        }
        if (needed != 0) throw new IllegalStateException("Shop is out of stock.");
        var inv = buyer.getInventory();
        List<ItemStack> destination = new ArrayList<>();
        for (int i=0;i<36;i++) destination.add(inv.getItem(i).copy());
        int left = product.getCount();
        for (int pass=0;pass<2;pass++) for (int i=0;i<36 && left>0;i++) {
            ItemStack stack = destination.get(i);
            if (pass==0 && !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, product)) {
                int add=Math.min(left, stack.getMaxStackSize()-stack.getCount()); stack.grow(add); left-=add;
            } else if (pass==1 && stack.isEmpty()) { int add=Math.min(left,product.getMaxStackSize()); destination.set(i,product.copyWithCount(add)); left-=add; }
        }
        if (left != 0) throw new IllegalStateException("Make room in your inventory first.");
        ItemStack purchased = product.copy();
        long purchasePrice = price;
        UUID seller = owner;
        busy = true;
        BUYERS.add(buyer.getUUID());
        for (int i=0;i<remaining.size();i++) stock.setItem(i,remaining.get(i));
        stock.setChanged();
        // SQLite and Minecraft inventories have separate crash boundaries. A process crash between
        // reservation/payment/delivery cannot be atomically recovered without a durable inventory outbox.
        try {
            FinanceService.complete(buyer, EconomyApi.get(buyer.server).transfer(buyer.getUUID(), seller,
                purchasePrice, "Shop purchase at " + worldPosition.toShortString()), (ignored, error) -> {
                try {
                    if (error != null) {
                        // Restore exactly the reserved quantity; never overwrite hopper/player changes.
                        if (level.getBlockEntity(worldPosition.below()) == stock && sameContents(stock, remaining)) {
                            for (int i=0;i<original.size();i++) stock.setItem(i, original.get(i));
                            stock.setChanged();
                        } else restore(stock, purchased.copy());
                        buyer.sendSystemMessage(Messages.error(error.getMessage() == null ? "Purchase failed" : error.getMessage()));
                        return;
                    }
                    ServerPlayer connected = buyer.server.getPlayerList().getPlayer(buyer.getUUID());
                    if (connected != null) {
                        ItemStack delivery = purchased.copy();
                        connected.getInventory().add(delivery);
                        if (!delivery.isEmpty()) connected.drop(delivery, false);
                        connected.getInventory().setChanged();
                        connected.containerMenu.broadcastChanges();
                        connected.sendSystemMessage(Component.translatable("message.economy.bought", purchased.getCount(), purchased.getHoverName(), purchasePrice));
                    } else drop(purchased.copy());
                } finally {
                    busy = false;
                    BUYERS.remove(buyer.getUUID());
                }
            });
        } catch (RuntimeException error) {
            restore(stock, purchased.copy());
            busy = false;
            BUYERS.remove(buyer.getUUID());
            throw error;
        }
    }
    private static boolean sameContents(Container container, List<ItemStack> expected) {
        if (container.getContainerSize() != expected.size()) return false;
        for (int i=0;i<expected.size();i++) if (!ItemStack.matches(container.getItem(i), expected.get(i))) return false;
        return true;
    }
    private void restore(Container stock, ItemStack returned) {
        // A removed/replaced stock container is no longer a safe restoration destination.
        if (level.getBlockEntity(worldPosition.below()) != stock) { drop(returned); return; }
        for (int pass=0;pass<2;pass++) for (int i=0;i<stock.getContainerSize() && !returned.isEmpty();i++) {
            ItemStack existing = stock.getItem(i);
            if (!stock.canPlaceItem(i, returned)) continue;
            int limit = Math.min(stock.getMaxStackSize(), returned.getMaxStackSize());
            if (pass == 0 && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, returned)) {
                int add = Math.min(returned.getCount(), Math.max(0, limit-existing.getCount()));
                if (add > 0) { ItemStack merged = existing.copy(); merged.grow(add); stock.setItem(i, merged); returned.shrink(add); }
            } else if (pass == 1 && existing.isEmpty()) {
                int add = Math.min(returned.getCount(), limit);
                stock.setItem(i, returned.copyWithCount(add)); returned.shrink(add);
            }
        }
        stock.setChanged();
        if (!returned.isEmpty()) drop(returned);
    }
    private void drop(ItemStack stack) {
        net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX()+0.5,
            worldPosition.getY()+1.0, worldPosition.getZ()+0.5, stack);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        if (owner != null) tag.putUUID("Owner",owner);
        if (!product.isEmpty()) tag.put("Product",product.save(registries));
        tag.putLong("Price",price); tag.putInt("DataVersion",1);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        owner=tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        product=tag.contains("Product") ? ItemStack.parseOptional(registries,tag.getCompound("Product")) : ItemStack.EMPTY;
        price=Math.max(0,tag.getLong("Price"));
    }
}
