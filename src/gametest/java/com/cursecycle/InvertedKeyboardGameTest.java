package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.InvertedKeyboardChallenge;
import com.cursecycle.network.CurseCycleSyncPayload;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

/** Dedicated-server coverage of authoritative activation and the actual S2C input gate. */
public class InvertedKeyboardGameTest implements FabricGameTest {
    private static final String ID = InvertedKeyboardChallenge.ID;

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "inverted_keyboard")
    public void activationRecipientsPersistenceAndLifecycle(TestContext context) throws Exception {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer();
            var first = s.player();
            var second = s.player();
            var firstChannel = s.channel(first);
            s.activate(ID);
            assertIds(context, firstChannel, List.of(ID));
            assertIds(context, s.channel(second), List.of(ID));
            var late = s.player();
            assertIds(context, s.channel(late), List.of(ID));
            // The client's current mode gates the transform; the active ID survives mode changes.
            for (var mode : GameMode.values()) {
                first.changeGameMode(mode);
                CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
                assertIds(context, firstChannel, List.of(ID));
            }
            first.changeGameMode(GameMode.SURVIVAL);
            first.setHealth(0);
            first = s.respawn(first, false);
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertIds(context, firstChannel, List.of(ID));
            for (var dimension : List.of(World.NETHER, World.END, World.OVERWORLD)) {
                first.teleport(server.getWorld(dimension), .5, 80, .5, 0, 0);
                first.onTeleportationDone();
                CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
                assertIds(context, firstChannel, List.of(ID));
            }
            s.activate("double_challenge:inverted_keyboard,no_sprint");
            var both = List.of(ID, "no_sprint");
            assertIds(context, firstChannel, both);
            first.setSprinting(true);
            context.assertFalse(first.isSprinting(), "Companion sprint restriction remains authoritative");
            var persistence = server.getOverworld().getPersistentStateManager();
            persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    SharedConstants.getGameVersion().getSaveVersion().getId());
            var restored = CurseCycleState.createFromNbt(saved.getCompound("data"), context.getWorld().getRegistryManager());
            context.assertEquals(both, restored.getActiveChallengeIds(), "Active IDs are actually saved to disk");
            persistence.set(CurseCycleState.STATE_KEY, restored);
            CycleManager.syncState(server, restored, CurseCycleMod.getConfig());
            assertIds(context, firstChannel, both);
            var uuid = second.getUuid();
            disconnect(second);
            second = s.player(uuid);
            assertIds(context, s.channel(second), both);
            disconnect(second);
            CycleManager.activateChallenge(server, restored, "no_sprint");
            assertIds(context, firstChannel, List.of("no_sprint"));
            second = s.player(uuid);
            assertIds(context, s.channel(second), List.of("no_sprint"));
            second.setSprinting(true);
            context.assertFalse(second.isSprinting(), "Stopping input inversion does not remove no_sprint");
            var source = server.getCommandSource().withLevel(2);
            for (var command : List.of("fdc list", "fdc activate inverted_keyboard", "fdc active"))
                context.assertTrue(server.getCommandManager().getDispatcher().execute(command, source) > 0,
                        "Command succeeds: " + command);
            assertIds(context, s.channel(second), List.of(ID));
            context.assertEquals(1L, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count(),
                    "Exactly one registered challenge");
        }
        context.waitAndRun(5, context::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "inverted_keyboard")
    public void personalIdsDeduplicateAndDisabledServerGateReachesClients(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer();
            var owner = s.player();
            var other = s.player();
            s.activate("no_sprint");
            s.state.getPersonalActiveChallenges().put(owner.getUuid(), new ArrayList<>(List.of(ID, ID)));
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertIds(context, s.channel(owner), List.of("no_sprint", ID));
            assertIds(context, s.channel(other), List.of("no_sprint"));
            s.state.getActiveChallengeIds().add(ID);
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertIds(context, s.channel(owner), List.of("no_sprint", ID));
            assertIds(context, s.channel(other), List.of("no_sprint", ID));
            var challenge = ChallengeRegistry.get(ID);
            try {
                challenge.setEnabled(false);
                CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
                assertIds(context, s.channel(owner), List.of("no_sprint"));
                assertIds(context, s.channel(other), List.of("no_sprint"));
            } finally {
                challenge.setEnabled(true);
            }
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertIds(context, s.channel(owner), List.of("no_sprint", ID));
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "inverted_keyboard_disk")
    public void repeatedActivationsDiskReloadAndOfflineEndPreserveForeignData(TestContext context) throws Exception {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer();
            var player = s.player(); var uuid = player.getUuid();
            var cargo = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 17);
            cargo.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("foreign-input-review"));
            player.getInventory().clear(); player.getInventory().setStack(8, cargo.copy());
            player.experienceLevel = 7; player.experienceProgress = .625F; player.setHealth(17);
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE, 1200, 0));
            for (int cycle = 0; cycle < 32; cycle++) {
                long before = s.state.getActivationSerial();
                s.activate("double_challenge:inverted_keyboard,inverted_keyboard");
                context.assertEquals(before + 1, s.state.getActivationSerial(), "New activation serial");
                assertIds(context, s.channel(player), List.of(ID));
                s.activate("double_challenge:inverted_keyboard,no_sprint");
                assertIds(context, s.channel(player), List.of(ID, "no_sprint"));
                s.activate("no_sprint"); assertIds(context, s.channel(player), List.of("no_sprint"));
            }
            s.activate("double_challenge:inverted_keyboard,no_sprint");
            disconnect(player);
            context.assertTrue(server.getPlayerManager().getPlayerList().isEmpty(), "Actual empty server save");
            var loaded = reload(context);
            player = s.player(uuid); assertForeign(context, player, cargo);
            assertIds(context, s.channel(player), List.of(ID, "no_sprint"));
            // Repeat activation after real disk load and then persist its end.
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            disconnect(player); loaded = reload(context);
            player = s.player(uuid); assertForeign(context, player, cargo);
            assertIds(context, s.channel(player), List.of("no_sprint"));
            CycleManager.activateChallenge(server, loaded, ID);
            disconnect(player); loaded = reload(context);
            CycleManager.activateChallenge(server, loaded, "no_sprint"); reload(context);
            player = s.player(uuid); assertForeign(context, player, cargo);
            assertIds(context, s.channel(player), List.of("no_sprint"));
            player.setSprinting(true); context.assertFalse(player.isSprinting(), "Offline end preserves no_sprint");
            disconnect(player);
        }
        context.complete();
    }

    private static CurseCycleState reload(TestContext context) throws Exception {
        var manager = context.getWorld().getServer().getOverworld().getPersistentStateManager();
        CurseCycleState.getServerState(context.getWorld().getServer()).markDirty(); manager.save();
        var disk = manager.readNbt(CurseCycleState.STATE_KEY, null,
                SharedConstants.getGameVersion().getSaveVersion().getId());
        var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), context.getWorld().getRegistryManager());
        context.assertTrue(loaded.getChallengeData().isEmpty(), "No input/keybinding snapshots in persistent state");
        manager.set(CurseCycleState.STATE_KEY, loaded);
        return loaded;
    }

    private static void assertForeign(TestContext context, net.minecraft.server.network.ServerPlayerEntity player,
                                      net.minecraft.item.ItemStack cargo) {
        context.assertTrue(net.minecraft.item.ItemStack.areEqual(cargo, player.getInventory().getStack(8)), "Named items preserved");
        context.assertEquals(7, player.experienceLevel, "XP level preserved");
        context.assertEquals(.625F, player.experienceProgress, "XP progress preserved");
        context.assertEquals(17F, player.getHealth(), "HP preserved");
        context.assertTrue(player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE), "Foreign effect preserved");
    }

    private static void disconnect(net.minecraft.server.network.ServerPlayerEntity player) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                .onPlayDisconnect(player.networkHandler, player.getServer());
        player.getServer().getPlayerManager().remove(player);
    }

    private static void assertIds(TestContext context, EmbeddedChannel channel, List<String> expected) {
        channel.runPendingTasks();
        channel.flush();
        CurseCycleSyncPayload last = null;
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof CustomPayloadS2CPacket custom && custom.payload() instanceof CurseCycleSyncPayload sync)
                last = sync;
        }
        context.assertTrue(last != null, "A real S2C state packet reaches the recipient");
        context.assertEquals(expected, last.activeChallengeIds(), "Client receives only current effective enabled IDs");
    }
}
