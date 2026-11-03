package ca.emilybelnavis.mcmods.economy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** First-join setup is held open until the elected player chooses the world currency. */
public final class WorldCurrencyMenu extends AbstractContainerMenu {
    private final Player owner;
    public boolean busy;
    public String error="";
    public WorldCurrencyMenu(int id, Inventory inventory, RegistryFriendlyByteBuf ignored) { this(id,inventory); }
    public WorldCurrencyMenu(int id, Inventory inventory) { super(Economy.WORLD_CURRENCY_MENU.get(),id);owner=inventory.player; }
    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new WorldCurrencyMenu(id,inventory),Component.translatable("finance.economy.world_currency_title")));
    }
    public void receive(FinanceAction request) {
        if (busy || !(owner instanceof ServerPlayer player) || !stillValid(player) || !request.action().equals("world_select")) return;
        try {
            var args=com.google.gson.JsonParser.parseString(request.json()).getAsJsonObject();
            BankingConfig.Currency currency=BankingConfig.Currency.valueOf(args.get("currency").getAsString());
            busy=true; WorldCurrencies.select(player,currency,this);
        } catch (RuntimeException failure) { error=failure.getMessage();busy=false; update(); }
    }
    public void update() {
        if (owner instanceof ServerPlayer p) {
            var state = new com.google.gson.JsonObject(); state.addProperty("busy",busy); state.addProperty("error",error);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new FinanceSnapshot(containerId,state.toString()));
        }
    }
    @Override public boolean stillValid(Player player) {
        return player==owner && player.isAlive() && (!(player instanceof ServerPlayer p) || WorldCurrencies.isChooser(p));
    }
    @Override public ItemStack quickMoveStack(Player player,int index) { return ItemStack.EMPTY; }
}
