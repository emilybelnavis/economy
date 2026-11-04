package dev.emily.economy.foundation;

import com.mojang.brigadier.CommandDispatcher;
import dev.emily.economy.banking.BankingController;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Operator shortcut to the terminal settings screen; ATM placers also have empty-hand access. */
public final class AdminConfigCommands {
    private AdminConfigCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("economy").then(Commands.literal("admin")
                .requires(EconomyCommands::privileged)
                .then(Commands.literal("config").executes(context -> open(context.getSource())))));
    }

    private static int open(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null || source.source != player || !(player.pick(5, 0, false) instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || !BankingController.openAdmin(player, hit.getBlockPos())) {
            source.sendFailure(Component.literal("Look at a ready ATM, teller, or register within five blocks."));
            return 0;
        }
        return 1;
    }
}
