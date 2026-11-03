package ca.emilybelnavis.mcmods.economy;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import ca.emilybelnavis.mcmods.economy.api.EconomyApi;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import static net.minecraft.commands.Commands.*;

public final class EconomyCommands {
    private interface Action { void run(ServerPlayer player) throws Exception; }
    private static int execute(CommandContext<CommandSourceStack> ctx, Action action) {
        try { action.run(ctx.getSource().getPlayerOrException()); return 1; }
        catch (Exception ex) { ctx.getSource().sendFailure(Messages.error(ex.getMessage())); return 0; }
    }
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("economy")
            .executes(ctx -> execute(ctx, EconomyCommands::showAtm))
            .then(literal("worldcurrency").requires(source -> source.hasPermission(2))
                .then(argument("currency", StringArgumentType.word()).suggests((context,builder) -> {
                    for (var code : BankingConfig.Currency.values()) builder.suggest(code.name());
                    return builder.buildFuture();
                }).executes(ctx -> WorldCurrencies.change(ctx.getSource(),StringArgumentType.getString(ctx,"currency")))))
            .then(literal("balance").executes(ctx -> execute(ctx,p -> FinanceService.complete(p, EconomyApi.get(p.server).balance(p.getUUID()), (balance, error) -> {
                if (error != null) { failure(p, error); return; }
                p.sendSystemMessage(Component.translatable("message.economy.balance", Money.format(balance,FinanceService.get(p.server).currency().name())));
            }))))
            .then(literal("history").executes(ctx -> execute(ctx,p -> {
                FinanceService.complete(p, EconomyApi.get(p.server).history(p.getUUID(), 0, 10), (history, error) -> {
                    if (error != null) { failure(p, error); return; }
                    if (history.isEmpty()) p.sendSystemMessage(Component.translatable("message.economy.text_27"));
                    history.forEach(t -> p.sendSystemMessage(Component.translatable("message.economy.entry", t.operation(), t.delta(), t.balance(), t.memo())));
                });
            })))
            .then(literal("pay").then(argument("player",EntityArgument.player()).then(argument("amount",LongArgumentType.longArg(1)).executes(ctx -> execute(ctx,p -> {
                var target=EntityArgument.getPlayer(ctx,"player"); long amount=LongArgumentType.getLong(ctx,"amount");
                FinanceService.complete(p, EconomyApi.get(p.server).transfer(p.getUUID(),target.getUUID(),amount,"Player transfer"), (ignored, error) -> {
                    if (error != null) { failure(p, error); return; }
                    p.sendSystemMessage(Component.translatable("message.economy.paid", Money.format(amount,FinanceService.get(p.server).currency().name()), target.getScoreboardName()));
                    target.sendSystemMessage(Component.translatable("message.economy.received", Money.format(amount,FinanceService.get(p.server).currency().name()), p.getScoreboardName()));
                });
            })))))
            .then(literal("shop").then(literal("price").then(argument("amount",LongArgumentType.longArg(1)).executes(ctx -> execute(ctx,p -> {
                HitResult hit=p.pick(5,0,false);
                if (!(hit instanceof BlockHitResult block) || !(p.level().getBlockEntity(block.getBlockPos()) instanceof RegisterEntity register)) throw new IllegalStateException("Look at your cash register within five blocks.");
                register.setPrice(p,LongArgumentType.getLong(ctx,"amount")); p.sendSystemMessage(Component.translatable("message.economy.text_28"));
            })))))
        );
    }
    private static void failure(ServerPlayer player, Throwable error) {
        player.sendSystemMessage(Messages.error(error.getMessage() == null ? "Economy operation failed" : error.getMessage()));
    }
    public static void showAtm(ServerPlayer p) {
        FinanceService.complete(p, EconomyApi.get(p.server).balance(p.getUUID()), (balance, error) -> {
            if (error != null) failure(p, error);
            else p.sendSystemMessage(Component.translatable("message.economy.heading", Money.format(balance,FinanceService.get(p.server).currency().name())));
        });
        for (String[] link : new String[][]{{"[Balance]","/economy balance"},{"[History]","/economy history"}})
            p.sendSystemMessage(Messages.error(link[0]).withStyle(s->s.withColor(net.minecraft.ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,link[1]))));
        p.sendSystemMessage(Component.translatable("banking.economy.help"));
        p.sendSystemMessage(Component.translatable("message.economy.text_29"));
    }
}
