package dev.emily.economy.foundation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.emily.economy.api.Identifiers.OperationId;
import dev.emily.economy.api.Identifiers.PlayerId;
import dev.emily.economy.api.Identifiers.RequestId;
import dev.emily.economy.fx.FxService;
import dev.emily.economy.journal.Journal;
import dev.emily.economy.journal.JournalService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Server-thread admission for authenticated in-game FX policy edits. */
public final class FxAdminCommands {
    private FxAdminCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Function<MinecraftServer, ServerServices> sessions, Clock wall) {
        var policy = Commands.literal("policy").requires(FxAdminCommands::authorized)
                .then(Commands.argument("operation", UuidArgument.uuid())
                .then(Commands.argument("currency", StringArgumentType.word())
                .then(Commands.argument("baseline", StringArgumentType.word())
                .then(Commands.argument("variation", IntegerArgumentType.integer(0, 1_000_000))
                .then(Commands.argument("pull", IntegerArgumentType.integer(0, 1_000_000))
                .then(Commands.argument("bounds", IntegerArgumentType.integer(0, 999_999))
                        .executes(context -> edit(context, sessions, wall))))))));
        dispatcher.register(Commands.literal("economy").then(Commands.literal("fx").then(policy)));
    }

    private static boolean authorized(CommandSourceStack source) {
        return source.getPlayer() != null && EconomyCommands.privileged(source);
    }

    private static int edit(CommandContext<CommandSourceStack> context,
                            Function<MinecraftServer, ServerServices> sessions, Clock wall) {
        var source = context.getSource();
        if (!authorized(source)) return 0;
        var server = source.getServer();
        var services = sessions.apply(server);
        if (services == null || services.state() != ServerServices.State.READY) {
            source.sendFailure(Component.literal("FX policy service unavailable"));
            return 0;
        }

        var player = source.getPlayer();
        var actor = new PlayerId(player.getUUID());
        var operation = new OperationId(UuidArgument.getUuid(context, "operation"));
        var fields = Map.of("currency", StringArgumentType.getString(context, "currency"),
                "baseline", StringArgumentType.getString(context, "baseline"),
                "variation", Integer.toString(IntegerArgumentType.getInteger(context, "variation")),
                "pull", Integer.toString(IntegerArgumentType.getInteger(context, "pull")),
                "bounds", Integer.toString(IntegerArgumentType.getInteger(context, "bounds")));
        if (fields.get("baseline").length() > 20 || fields.get("currency").length() != 3) {
            source.sendFailure(Component.literal("Invalid FX currency or baseline"));
            return 0;
        }
        var intent = new Journal.Intent(operation, new RequestId(UUID.randomUUID()), actor, FxService.EDIT,
                new Journal.CanonicalRequest(1, fields), null, null);
        var journal = services.journal((requestActor, kind) -> actor.equals(requestActor) && FxService.EDIT.equals(kind),
                Map.of(FxService.EDIT, FxService.editing(wall)), new JournalService.Publisher() {
                    public void invalidate(Journal.Outcome committed) {}
                    public void publish(Journal.Outcome terminal) {}
                });
        var submission = journal.submit(intent);
        submission.completion().thenAccept(delivery -> {
            var live = server.getPlayerList().getPlayer(actor.value());
            if (sessions.apply(server) != services || live != player || !authorized(live.createCommandSourceStack())) return;
            var result = delivery.outcome().result();
            String suffix = result.payload().isEmpty() ? "" : " (" + result.payload() + ")";
            live.sendSystemMessage(Component.literal(result.code() + suffix + " [" + operation.value() + "]"));
        });
        return submission.receipt() == Journal.Receipt.ACCEPTED_PENDING ? 1 : 0;
    }
}
