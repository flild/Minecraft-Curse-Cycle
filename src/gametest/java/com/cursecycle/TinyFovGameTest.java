package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.TinyFovChallenge;
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

/** Dedicated-server coverage of activation, saved IDs and the S2C camera gate (rendering itself is client-only). */
public class TinyFovGameTest implements FabricGameTest {
    private static final String ID = TinyFovChallenge.ID;

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tiny_fov")
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
            s.activate("double_challenge:tiny_fov,no_sprint");
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
            server.getPlayerManager().remove(second);
            second = s.player(uuid);
            assertIds(context, s.channel(second), both);
            server.getPlayerManager().remove(second);
            CycleManager.activateChallenge(server, restored, "no_sprint");
            assertIds(context, firstChannel, List.of("no_sprint"));
            persistence.save();
            var endedNbt = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    SharedConstants.getGameVersion().getSaveVersion().getId());
            var ended = CurseCycleState.createFromNbt(endedNbt.getCompound("data"), context.getWorld().getRegistryManager());
            context.assertEquals(List.of("no_sprint"), ended.getActiveChallengeIds(),
                    "Ending while a player is offline is saved to disk");
            persistence.set(CurseCycleState.STATE_KEY, ended);
            second = s.player(uuid);
            assertIds(context, s.channel(second), List.of("no_sprint"));
            second.setSprinting(true);
            context.assertFalse(second.isSprinting(), "Stopping tiny FOV does not remove no_sprint");
            var source = server.getCommandSource().withLevel(2);
            for (var command : List.of("fdc list", "fdc activate tiny_fov", "fdc active"))
                context.assertTrue(server.getCommandManager().getDispatcher().execute(command, source) > 0,
                        "Command succeeds: " + command);
            assertIds(context, s.channel(second), List.of(ID));
            context.assertEquals(1L, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count(),
                    "Exactly one registered challenge");
        }
        context.waitAndRun(5, context::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tiny_fov")
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

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tiny_fov_reactivation")
    public void repeatedActivationAndEmptyServerDoNotStoreSettings(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer();
            var player = s.player();
            player.getInventory().setStack(8, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 17));
            player.setHealth(17);
            player.experienceLevel = 7;
            long serial = s.state.getActivationSerial();
            for (int cycle = 0; cycle < 32; cycle++) {
                s.activate("double_challenge:tiny_fov,no_sprint");
                context.assertTrue(s.state.getActivationSerial() > serial, "New activation serial");
                serial = s.state.getActivationSerial();
                assertIds(context, s.channel(player), List.of(ID, "no_sprint"));
                s.activate("no_sprint");
                assertIds(context, s.channel(player), List.of("no_sprint"));
                player.setSprinting(true);
                context.assertFalse(player.isSprinting(), "Companion remains after every end");
                context.assertEquals(17, player.getInventory().getStack(8).getCount(), "Foreign cargo unchanged");
                context.assertEquals(17F, player.getHealth(), "Foreign health unchanged");
                context.assertEquals(7, player.experienceLevel, "Foreign XP unchanged");
                context.assertFalse(s.state.getChallengeData().contains(ID), "No persisted FOV/settings snapshot");
            }
            var uuid = player.getUuid();
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                    .onPlayDisconnect(player.networkHandler, server);
            server.getPlayerManager().remove(player);
            context.assertTrue(server.getPlayerManager().getPlayerList().isEmpty(), "Empty-server fixture");
            s.activate(ID);
            player = s.player(uuid);
            assertIds(context, s.channel(player), List.of(ID));
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                    .onPlayDisconnect(player.networkHandler, server);
            server.getPlayerManager().remove(player);
            s.activate("no_sprint");
            player = s.player(uuid);
            assertIds(context, s.channel(player), List.of("no_sprint"));
        }
        context.complete();
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
        var buffer = new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            CurseCycleSyncPayload.CODEC.encode(buffer, last);
            var decoded = CurseCycleSyncPayload.CODEC.decode(buffer);
            context.assertEquals(expected, decoded.activeChallengeIds(), "Actual S2C codec retains effective IDs");
            context.assertEquals(last.currentDay(), decoded.currentDay(), "Codec retains day");
            context.assertEquals(last.ticksUntilNext(), decoded.ticksUntilNext(), "Codec retains deadline");
            context.assertEquals(0, buffer.readableBytes(), "Codec consumes exact packet");
        } finally { buffer.release(); }
    }
}
