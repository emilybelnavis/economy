package ca.emilybelnavis.mcmods.economy.banking;

import ca.emilybelnavis.mcmods.economy.Economy;
import ca.emilybelnavis.mcmods.economy.cash.CashCatalog;
import ca.emilybelnavis.mcmods.economy.cash.CashStackIdentity;
import ca.emilybelnavis.mcmods.economy.foundation.ServerServices;
import ca.emilybelnavis.mcmods.economy.journal.TerminalPolicy;
import ca.emilybelnavis.mcmods.economy.terminal.TerminalLifecycle;
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
    public enum Access {
        ATM, TELLER, KIOSK, REGISTER, CARD, ADMIN, WORLD_SETUP;

        private static final Set<String> BANKING = Set.of("home", "manage_accounts", "setup", "accounts", "accounts_next", "account_select", "account_open", "account_close_preview", "account_close_confirm",
                "history", "history_next", "fx", "exchange_preview", "exchange_confirm", "bank_retry", "deposit",
                "deposit_preview", "deposit_confirm", "withdraw", "withdraw_preview", "withdraw_confirm", "withdraw_mix", "withdraw_mix_change", "withdraw_mix_review", "withdraw_mix_reset", "claims",
                "claims_next", "collect", "retry", "cancel", "cards", "card_select", "card_lost", "credit_profile", "credit_history", "credit_next", "credit_facilities", "credit_facilities_next", "credit_facility", "credit_statements", "credit_statements_next");
        private static final Set<String> CARD_ACTIONS = Set.of("home", "manage_accounts", "setup", "accounts", "accounts_next", "account_select",
                "history", "history_next", "fx", "exchange_preview", "exchange_confirm", "bank_retry", "claims",
                "claims_next", "collect", "retry", "cancel", "cards", "card_select", "card_lost", "credit_profile", "credit_history", "credit_next", "credit_facilities", "credit_facilities_next", "credit_facility", "credit_statements", "credit_statements_next");
        private static final Set<String> REGISTER_ACTIONS = Set.of("home", "shop", "buy_preview", "buy_confirm", "price", "claims",
                "claims_next", "collect", "retry", "cancel");
        private static final Set<String> ADMIN_ACTIONS = Set.of("kiosk_upgrade", "settings", "configure", "bank_retry", "cancel", "banking", "brand_preview", "brand_confirm");

        public boolean allows(String action) {
            if (FinanceActions.handles(action)) return FinanceActions.allowed(this, action);
            return switch (this) {
                case ATM -> BANKING.contains(action) && !action.startsWith("account_close_") && !action.equals("account_open") && !action.equals("setup");
                case TELLER -> BANKING.contains(action) && !action.startsWith("account_close_") && !action.equals("account_open") && !action.equals("setup");
                case KIOSK -> Set.of("home", "manage_accounts", "accounts", "accounts_next", "account_select", "history", "history_next", "bank_retry", "cancel", "branch_open", "branch_assign", "branch_switch", "branch_accept", "branch_close", "account_close_confirm", "branch_products", "cards", "card_select", "card_preview", "card_confirm", "card_lost", "claims", "claims_next", "collect", "retry", "credit_profile", "credit_history", "credit_next", "credit_facilities", "credit_facilities_next", "credit_facility", "credit_statements", "credit_statements_next").contains(action);
                case REGISTER -> REGISTER_ACTIONS.contains(action);
                case CARD -> CARD_ACTIONS.contains(action);
                case ADMIN -> ADMIN_ACTIONS.contains(action);
                case WORLD_SETUP -> action.equals("setup");
            };
        }
    }
    private final Inventory inventory;
    private final TerminalPolicy.Anchor anchor;
    private final UUID generation;
    private final UUID session;
    private final Access access;
    private final SimpleContainer inputs = new SimpleContainer(24);
    private UUID issuedToken;
    public UUID issuedToken(){return issuedToken;}
    public void setIssuedToken(UUID token){if(!remote())throw new IllegalStateException("Remote card menu required");issuedToken=Objects.requireNonNull(token);}
    private long revision;
    private long lastActionTick = -10;
    private String page = "home";
    private List<String> lines = List.of();
    private List<BankingNetwork.Choice> choices = List.of();
    private String status = "";
    private boolean busy;
    private boolean depositSlotsVisible = true;

    public BankingMenu(int id, Inventory inventory, TerminalPolicy.Anchor anchor, UUID generation, UUID session) {
        this(id, inventory, anchor, generation, session, Access.ATM);
    }
    public BankingMenu(int id, Inventory inventory, TerminalPolicy.Anchor anchor, UUID generation, UUID session, boolean remote) {
        this(id, inventory, anchor, generation, session, remote ? Access.CARD : Access.ATM);
    }
    public BankingMenu(int id, Inventory inventory, TerminalPolicy.Anchor anchor, UUID generation, UUID session, boolean remote, boolean worldSetup) {
        this(id, inventory, anchor, generation, session, worldSetup ? Access.WORLD_SETUP : remote ? Access.CARD : Access.ATM);
    }
    public BankingMenu(int id, Inventory inventory, TerminalPolicy.Anchor anchor, UUID generation, UUID session, Access access) {
        super(BankingCatalog.BANKING.get(), id);
        this.access = Objects.requireNonNull(access);
        this.inventory = Objects.requireNonNull(inventory); this.anchor = Objects.requireNonNull(anchor);
        this.generation = Objects.requireNonNull(generation); this.session = Objects.requireNonNull(session);
        for (int index = 0; index < 24; index++) {
            addSlot(new Slot(inputs, index, 12 + (index % 8) * 18, 74 + (index / 8) * 18) {
                @Override public boolean isActive() { return page.equals("deposit") && depositSlotsVisible; }
                @Override public boolean mayPlace(ItemStack stack) {
                    return isActive() && !busy && (!(inventory.player instanceof ServerPlayer serverPlayer) || BankingController.canInput(serverPlayer, stack))
                            && CashCatalog.find(stack).isPresent();
                }
                @Override public boolean mayPickup(Player player) { return isActive() && !busy; }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(playerSlot(inventory, column + row * 9 + 9, 12 + column * 18, 144 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(playerSlot(inventory, column, 12 + column * 18, 202));
        inputs.addListener(container -> {
            if (inventory.player instanceof ServerPlayer player) BankingController.inputsChanged(player, this);
        });
    }
    private Slot playerSlot(Inventory inventory, int index, int x, int y) {
        return new Slot(inventory, index, x, y) {
            @Override public boolean isActive() { return page.equals("deposit") && depositSlotsVisible; }
            @Override public boolean mayPlace(ItemStack stack) { return isActive() && !busy; }
            @Override public boolean mayPickup(Player player) { return isActive() && !busy; }
        };
    }
    static BankingMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        var anchor = new TerminalPolicy.Anchor(buffer.readUtf(96), buffer.readInt(), buffer.readInt(), buffer.readInt());
        return new BankingMenu(id, inventory, anchor, buffer.readUUID(), buffer.readUUID(), buffer.readEnum(Access.class));
    }
    public void writeOpening(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(anchor.dimension(), 96); buffer.writeInt(anchor.x()); buffer.writeInt(anchor.y()); buffer.writeInt(anchor.z());
        buffer.writeUUID(generation); buffer.writeUUID(session); buffer.writeEnum(access);
    }
    public TerminalPolicy.Anchor anchor() { return anchor; }
    public UUID generation() { return generation; }
    public UUID session() { return session; }
    public Access access() { return access; }
    public boolean remote() { return access == Access.CARD; }
    public boolean worldSetup() { return access == Access.WORLD_SETUP; }
    public boolean allows(String action) { return access.allows(action); }
    public long revision() { return revision; }
    public String page() { return page; }
    public List<String> lines() { return lines; }
    public List<BankingNetwork.Choice> choices() { return choices; }
    public String status() { return status; }
    public boolean busy() { return busy; }
    public void setBusy(boolean value) { busy = value; }
    public SimpleContainer depositInputs() { return inputs; }
    /** Client presentation only; server inventory admission still checks the current page. */
    public void setDepositSlotsVisible(boolean visible) { depositSlotsVisible = visible; }
    public Player player() { return inventory.player; }

    public boolean valid(ServerPlayer player) {
        if (player != inventory.player || player.containerMenu != this || !player.isAlive()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player) return false;
        if (worldSetup()) return Economy.canChooseWorld(player, generation);
        if (remote()) {
            var services = Economy.serverServices(player.serverLevel().getServer());
            return services != null && services.state() == ServerServices.State.READY
                    && issuedToken != null && java.util.stream.Stream.of(player.getMainHandItem(),player.getOffhandItem()).anyMatch(stack->ca.emilybelnavis.mcmods.economy.card.CardStackIdentity.mark(stack,services.worldId()).filter(mark->mark.token().equals(issuedToken)).isPresent());
        }
        if (!player.serverLevel().dimension().location().toString().equals(anchor.dimension())) return false;
        var position = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        var services = Economy.serverServices(player.serverLevel().getServer());
        return services != null && services.state() == ServerServices.State.READY
                && (access != Access.ADMIN || player.serverLevel().getBlockEntity(position) instanceof ca.emilybelnavis.mcmods.economy.terminal.TerminalBlockEntity entity
                        && BankingController.mayConfigure(entity.owner(), player.getUUID(), player.hasPermissions(2)))
                && player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) <= 36
                && TerminalLifecycle.matches(player.serverLevel(), position, generation);
    }
    public boolean acceptAction(ServerPlayer player, UUID candidateSession, long candidateRevision, String action) {
        if (!session.equals(candidateSession) || candidateRevision != revision || !valid(player) || !allows(action)) return false;
        if (busy && !action.equals("retry") && !action.equals("bank_retry") && !action.equals("cancel")) return false;
        long now = player.serverLevel().getServer().getTickCount();
        if (now - lastActionTick < 2) return false;
        lastActionTick = now; return true;
    }
    public void publish(String page, List<String> lines, String status) {
        publish(page, lines, status, this.page.equals(page) ? choices : List.of());
    }
    public void publish(String page, List<String> lines, String status, List<BankingNetwork.Choice> choices) {
        boolean debug = inventory.player instanceof ServerPlayer player && BankingController.services(player).debugGui();
        var visibleLines = debug ? List.copyOf(lines) : BankingPresentation.lines(lines);
        var visibleStatus = debug ? status : BankingPresentation.status(status);
        var snapshot = new BankingNetwork.Snapshot(containerId, session, Math.addExact(revision, 1), page, visibleLines, visibleStatus, busy, choices);
        this.revision = snapshot.revision(); this.page = snapshot.page(); this.lines = snapshot.lines(); this.status = snapshot.status(); this.choices = snapshot.choices();
        if (inventory.player instanceof ServerPlayer player) BankingNetwork.send(player, snapshot);
        broadcastChanges();
    }
    public void applyClientSnapshot(long revision, String page, List<String> lines, String status, boolean busy, List<BankingNetwork.Choice> choices) {
        if (revision <= this.revision) return;
        this.revision = revision; this.page = page; this.lines = List.copyOf(lines); this.status = status; this.busy = busy; this.choices = List.copyOf(choices);
    }
    @Override public boolean stillValid(Player player) {
        return player instanceof ServerPlayer serverPlayer ? valid(serverPlayer) : player == inventory.player;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (busy || !page.equals("deposit")) return;
        super.clicked(slot, button, type, player);
    }
    @Override public boolean canDragTo(Slot slot) { return !busy && page.equals("deposit"); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (busy || !page.equals("deposit") || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
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
        ca.emilybelnavis.mcmods.economy.institution.KioskAdmission.closed(this);
        if (player instanceof ServerPlayer serverPlayer) BankingController.closed(serverPlayer, this);
        super.removed(player); // Vanilla carried player item only; deposit inputs remain under controller custody.
    }
}
