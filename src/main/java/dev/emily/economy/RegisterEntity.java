package dev.emily.economy;

import java.util.*;
import dev.emily.economy.api.EconomyApi;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class RegisterEntity extends BlockEntity {
    private UUID owner;
    private ItemStack product = ItemStack.EMPTY;
    private long price;
    public RegisterEntity(BlockPos pos, BlockState state) { super(Economy.REGISTER_ENTITY.get(), pos, state); }
    public void setOwner(UUID owner) { this.owner = owner; setChanged(); }
    private void requireOwner(ServerPlayer player) {
        if (!player.getUUID().equals(owner)) throw new IllegalStateException("Only the shop owner can configure this register.");
    }
    public void configure(ServerPlayer player) {
        requireOwner(player);
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) { product = held.copy(); price = 0; setChanged(); }
        player.sendSystemMessage(Component.translatable("message.economy.shop_config", product.isEmpty() ? Component.translatable("message.economy.unset") : Component.translatable("message.economy.product", product.getCount(), product.getHoverName())));
    }
    public void setPrice(ServerPlayer player, long price) {
        requireOwner(player);
        if (product.isEmpty()) throw new IllegalStateException("Sneak-use the register holding the product stack first.");
        if (price <= 0) throw new IllegalArgumentException("Price must be positive.");
        this.price = price; setChanged();
    }
    public void buy(ServerPlayer buyer) {
        if (owner == null || product.isEmpty() || price <= 0) throw new IllegalStateException("Shop is not configured. Owner: sneak-use with the product, then /economy shop price <units>.");
        if (buyer.getUUID().equals(owner)) {
            buyer.sendSystemMessage(Component.translatable("message.economy.your_shop", product.getCount(), product.getHoverName(), price)); return;
        }
        if (!(level.getBlockEntity(worldPosition.below()) instanceof Container stock)) throw new IllegalStateException("No stock container below the register.");
        // Plan both inventories before moving money. No drops or partial purchases.
        List<ItemStack> remaining = new ArrayList<>();
        int needed = product.getCount();
        for (int i=0;i<stock.getContainerSize();i++) {
            ItemStack stack = stock.getItem(i).copy();
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
        EconomyApi.get(buyer.server).transfer(buyer.getUUID(), owner, price, "Shop purchase at " + worldPosition.toShortString());
        for (int i=0;i<remaining.size();i++) stock.setItem(i,remaining.get(i));
        for (int i=0;i<36;i++) inv.setItem(i,destination.get(i));
        stock.setChanged(); inv.setChanged(); buyer.containerMenu.broadcastChanges();
        buyer.sendSystemMessage(Component.translatable("message.economy.bought", product.getCount(), product.getHoverName(), price));
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
