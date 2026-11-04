package dev.emily.economy.banking;

import dev.emily.economy.Economy;
import dev.emily.economy.cash.CashCatalog;
import dev.emily.economy.cash.CashStackIdentity;
import dev.emily.economy.foundation.ServerServices;
import dev.emily.economy.journal.TerminalPolicy;
import dev.emily.economy.terminal.TerminalLifecycle;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Server-issued identity and bounded snapshot for one live physical terminal session. */
public final class BankingMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final TerminalPolicy.Anchor anchor;
    private final UUID generation;
    private final UUID session;
    private final boolean remote;
    private final boolean worldSetup;
    private final SimpleContainer inputs = new SimpleContainer(24);
    private long revision;
    private long lastActionTick = -10;
    private String page = "home";
    private List<String> lines = List.of();
    private String status = "";
    private boolean busy;

    public BankingMenu(int id, Inventory inventory, TerminalPolicy.Anchor anchor, UUID generation, UUID session) {
        this(id, inventory, anchor, generation, session, false);
    }
    public BankingMenu(int id, Inventory inventory, TerminalPolicy.Anchor anchor, UUID generation, UUID session, boolean remote) {
        this(id, inventory, anchor, generation, session, remote, false);
    }
    public BankingMenu(int id, Inventory inventory, TerminalPolicy.Anchor anchor, UUID generation, UUID session, boolean remote, boolean worldSetup) {
        super(BankingCatalog.BANKING.get(), id);
        this.remote = remote; this.worldSetup = worldSetup;
        this.inventory = Objects.requireNonNull(inventory); this.anchor = Objects.requireNonNull(anchor);
        this.generation = Objects.requireNonNull(generation); this.session = Objects.requireNonNull(session);
        for (int index = 0; index < 24; index++) {
            addSlot(new Slot(inputs, index, 12 + (index % 8) * 18, 74 + (index / 8) * 18) {
                @Override public boolean isActive() { return page.equals("deposit"); }
                @Override public boolean mayPlace(ItemStack stack) {
                    return isActive() && !busy && (!(inventory.player instanceof ServerPlayer serverPlayer) || BankingController.canInput(serverPlayer, stack)) && CashCatalog.find(stack).isPresent() && stack.has(CashStackIdentity.CASH_LOT.get());
                }
                @Override public boolean mayPickup(Player player) { return isActive() && !busy; }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 12 + column * 18, 144 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 12 + column * 18, 202));
        inputs.addListener(container -> {
            if (inventory.player instanceof ServerPlayer player) BankingController.inputsChanged(player, this);
        });
    }
    static BankingMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        var anchor = new TerminalPolicy.Anchor(buffer.readUtf(96), buffer.readInt(), buffer.readInt(), buffer.readInt());
        return new BankingMenu(id, inventory, anchor, buffer.readUUID(), buffer.readUUID(), buffer.readBoolean(), buffer.readBoolean());
    }
    public void writeOpening(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(anchor.dimension(), 96); buffer.writeInt(anchor.x()); buffer.writeInt(anchor.y()); buffer.writeInt(anchor.z());
        buffer.writeUUID(generation); buffer.writeUUID(session); buffer.writeBoolean(remote); buffer.writeBoolean(worldSetup);
    }
    public TerminalPolicy.Anchor anchor() { return anchor; }
    public UUID generation() { return generation; }
    public UUID session() { return session; }
    public boolean remote() { return remote; }
    public boolean worldSetup() { return worldSetup; }
    public long revision() { return revision; }
    public String page() { return page; }
    public List<String> lines() { return lines; }
    public String status() { return status; }
    public boolean busy() { return busy; }
    public void setBusy(boolean value) { busy = value; }
    public SimpleContainer depositInputs() { return inputs; }
    public Player player() { return inventory.player; }

    public boolean valid(ServerPlayer player) {
        if (player != inventory.player || player.containerMenu != this || !player.isAlive()) return false;
        if (worldSetup) return Economy.canChooseWorld(player, generation);
        if (remote) {
            var services = Economy.serverServices(player.serverLevel().getServer());
            return services != null && services.state() == ServerServices.State.READY
                    && (CashCatalog.hasBankingCard(player.getMainHandItem()) || CashCatalog.hasBankingCard(player.getOffhandItem()));
        }
        if (!player.serverLevel().dimension().location().toString().equals(anchor.dimension())) return false;
        var position = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        var services = Economy.serverServices(player.serverLevel().getServer());
        return services != null && services.state() == ServerServices.State.READY
                && player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) <= 36
                && TerminalLifecycle.matches(player.serverLevel(), position, generation);
    }
    public boolean acceptAction(ServerPlayer player, UUID candidateSession, long candidateRevision, String action) {
        if (!session.equals(candidateSession) || candidateRevision != revision || !valid(player)) return false;
        if (busy && !action.equals("retry") && !action.equals("bank_retry") && !action.equals("cancel")) return false;
        long now = player.serverLevel().getServer().getTickCount();
        if (now - lastActionTick < 2) return false;
        lastActionTick = now; return true;
    }
    public void publish(String page, List<String> lines, String status) {
        var snapshot = new BankingNetwork.Snapshot(containerId, session, Math.addExact(revision, 1), page, lines, status, busy);
        this.revision = snapshot.revision(); this.page = snapshot.page(); this.lines = snapshot.lines(); this.status = snapshot.status();
        if (inventory.player instanceof ServerPlayer player) BankingNetwork.send(player, snapshot);
        broadcastChanges();
    }
    public void applyClientSnapshot(long revision, String page, List<String> lines, String status, boolean busy) {
        if (revision <= this.revision) return;
        this.revision = revision; this.page = page; this.lines = List.copyOf(lines); this.status = status; this.busy = busy;
    }
    @Override public boolean stillValid(Player player) {
        return player instanceof ServerPlayer serverPlayer ? valid(serverPlayer) : player == inventory.player;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (busy || (slot >= 0 && slot < 24 && !page.equals("deposit"))) return;
        super.clicked(slot, button, type, player);
    }
    @Override public boolean canDragTo(Slot slot) { return !busy && (slot.container != inputs || page.equals("deposit")); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (busy || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var original = slot.getItem(); var copy = original.copy();
        if (index < 24) {
            if (!page.equals("deposit") || !moveItemStackTo(original, 24, slots.size(), true)) return ItemStack.EMPTY;
        } else if (page.equals("deposit") && slots.getFirst().mayPlace(original)) {
            if (!moveItemStackTo(original, 0, 24, false)) return ItemStack.EMPTY;
        } else if (index < 51) {
            if (!moveItemStackTo(original, 51, 60, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(original, 24, 51, false)) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, original); return copy;
    }
    @Override public void removed(Player player) {
        if (player instanceof ServerPlayer serverPlayer) BankingController.closed(serverPlayer, this);
        super.removed(player); // Vanilla carried player item only; deposit inputs remain under controller custody.
    }
}
