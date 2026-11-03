package ca.emilybelnavis.mcmods.economy;

import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** First-player election and a short-lived, same-command confirmation for operators. */
public final class WorldCurrencies {
    private static final Map<MinecraftServer,UUID> CHOOSERS=new IdentityHashMap<>();
    private static final Map<MinecraftServer,CurrencyChangeConfirmation> CONFIRMATIONS=new IdentityHashMap<>();
    public static boolean isChooser(ServerPlayer player) {
        return player.getUUID().equals(CHOOSERS.get(player.server)) && FinanceService.get(player.server).currency()==null;
    }
    public static void joined(ServerPlayer player) {
        var service=FinanceService.get(player.server);
        // Elect synchronously in join-event order, not in future-completion order.
        if(service.currency()==null)CHOOSERS.putIfAbsent(player.server,player.getUUID());
        FinanceService.complete(player,service.ready(),(ignored,error)->{
            if(error!=null){player.sendSystemMessage(Component.literal("Economy initialization failed; contact an operator."));return;}
            if(service.currency()==null){
                CHOOSERS.putIfAbsent(player.server,player.getUUID());
                if(isChooser(player))WorldCurrencyMenu.open(player);
            } else {CHOOSERS.remove(player.server);service.database.accounts(player.getUUID());}
        });
    }
    public static void tick(MinecraftServer server) {
        var service=FinanceService.get(server);
        if(service.currency()!=null||!service.ready().isDone()||service.ready().isCompletedExceptionally()||service.changingCurrency())return;
        UUID chooser=CHOOSERS.get(server);
        ServerPlayer player=chooser==null?null:server.getPlayerList().getPlayer(chooser);
        if(player==null&&!server.getPlayerList().getPlayers().isEmpty()){
            player=server.getPlayerList().getPlayers().getFirst();CHOOSERS.put(server,player.getUUID());
        }
        if(player!=null&&player.isAlive()&&!(player.containerMenu instanceof WorldCurrencyMenu))WorldCurrencyMenu.open(player);
    }
    static void select(ServerPlayer player,BankingConfig.Currency currency,WorldCurrencyMenu menu) {
        if(!isChooser(player)){menu.busy=false;return;}
        var service=FinanceService.get(player.server);
        FinanceService.complete(player,service.database.selectWorldCurrency(currency.name()),(ignored,error)->{
            menu.busy=false;
            if(error!=null){menu.error=FinanceService.unwrap(error).getMessage();menu.update();return;}
            service.currencyCommitted(currency);
            CHOOSERS.remove(player.server);
            player.closeContainer();
            for(var online:player.server.getPlayerList().getPlayers())service.database.accounts(online.getUUID());
            player.sendSystemMessage(Component.translatable("finance.economy.world_currency_chosen",currency.name()));
        });
    }
    public static int change(CommandSourceStack source,String requested) {
        if(!source.hasPermission(2)){source.sendFailure(Component.literal("Operator permission or singleplayer cheats are required."));return 0;}
        var server=source.getServer();var service=FinanceService.get(server);
        if(service.currency()==null){source.sendFailure(Component.literal("Choose the initial world currency in the setup screen first."));return 0;}
        if(service.changingCurrency()){source.sendFailure(Component.literal("A world currency conversion is already in progress."));return 0;}
        BankingConfig.Currency target;
        try{target=BankingConfig.Currency.valueOf(requested.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){source.sendFailure(Component.literal("Unknown currency."));return 0;}
        String key=source.getEntity() instanceof ServerPlayer p?p.getUUID().toString():"CONSOLE";
        var pending=CONFIRMATIONS.computeIfAbsent(server,s->new CurrencyChangeConfirmation());
        if(!pending.confirm(key,service.currency().name(),target.name(),server.getTickCount())){
            source.sendFailure(Component.literal("WARNING: Changing world currency is strongly discouraged. Back up the world first. This converts world-currency bank balances, trading balances and stock prices, applies rounding, and merges any duplicate target-currency bank account. Optional foreign currencies remain unchanged. Run /economy worldcurrency "+target.name()+" again within 60 seconds to confirm."));
            return 0;
        }
        service.setChangingCurrency(true);
        for(var player:server.getPlayerList().getPlayers())if(player.containerMenu instanceof BankingMenu||player.containerMenu instanceof TradingMenu)player.closeContainer();
        service.ready().thenCompose(v->service.database.changeWorldCurrency(target.name(),false))
            .whenComplete((ignored,error)->server.execute(()->{
                service.setChangingCurrency(false);
                if(error!=null)source.sendFailure(Component.literal("Currency change failed. Check server logs before retrying: "+FinanceService.unwrap(error).getMessage()));
                else {service.currencyCommitted(target);pending.clear();source.sendSuccess(()->Component.literal("World currency changed to "+target.name()+". Foreign-currency accounts were preserved."),true);}
            }));
        return 1;
    }
    public static void stopped(MinecraftServer server){CHOOSERS.remove(server);CONFIRMATIONS.remove(server);}
    private WorldCurrencies(){}
}
