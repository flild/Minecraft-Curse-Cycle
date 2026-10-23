package com.cursecycle;

import com.cursecycle.command.CurseCycleCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurseCycleCommandTest {
    // These checks only inspect command permissions; no server or world is accessed.
    private static final class PermissionSource extends ServerCommandSource {
        PermissionSource(int level) {
            super(null, null, null, null, level, "test", null, null, null,
                    false, null, null, null, null);
        }
    }

    @Test
    void changingCommandsRequireOperatorPermissionsForBothAliases() {
        var dispatcher = new CommandDispatcher<ServerCommandSource>();
        CurseCycleCommand.register(dispatcher);
        for (String alias : List.of("fdc", "cursecycle")) {
            var root = dispatcher.getRoot().getChild(alias);
            for (String command : List.of("activate", "reset", "setday", "trigger_spin")) {
                var node = root.getChild(command);
                assertFalse(node.canUse(new PermissionSource(0)), command);
                assertFalse(node.canUse(new PermissionSource(1)), command);
                assertTrue(node.canUse(new PermissionSource(2)), command);
            }
            for (String command : List.of("active", "next", "list", "history")) {
                assertTrue(root.getChild(command).canUse(new PermissionSource(0)), command);
            }
        }
    }
}
