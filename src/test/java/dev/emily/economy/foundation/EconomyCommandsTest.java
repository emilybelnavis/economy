package dev.emily.economy.foundation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.rcon.RconConsoleSource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class EconomyCommandsTest {
    @Test void dispatcherKeepsDetailsFromUnprivilegedSources() throws Exception {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        EconomyCommands.register(dispatcher, (source, view) -> List.of(
                view.privileged() ? (view.detailed() ? "diagnostics" : "operator status") : "availability"));
        var nonOperator = new TestSource(CommandSource.NULL, 0);
        assertEquals(1, dispatcher.execute("economy status", nonOperator));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("economy diagnostics", nonOperator));
        assertEquals(List.of("availability"), nonOperator.messages);

        var automation = new TestSource(CommandSource.NULL, 2);
        assertEquals(1, dispatcher.execute("economy status", automation));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("economy diagnostics", automation));
        assertEquals(List.of("availability"), automation.messages);

        var console = new TestSource(new RconConsoleSource(null), 2);
        assertEquals(1, dispatcher.execute("economy status", console));
        assertEquals(1, dispatcher.execute("economy diagnostics", console));
        assertEquals(List.of("operator status", "diagnostics"), console.messages);
    }

    private static final class TestSource extends CommandSourceStack {
        private final List<String> messages = new ArrayList<>();

        private TestSource(CommandSource source, int level) {
            super(source, Vec3.ZERO, Vec2.ZERO, null, level, "fixture", Component.empty(), null, null);
        }

        @Override public void sendSuccess(Supplier<Component> message, boolean broadcast) {
            assertFalse(broadcast);
            messages.add(message.get().getString());
        }
    }
}
