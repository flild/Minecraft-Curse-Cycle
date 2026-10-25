package com.cursecycle;

import com.cursecycle.challenge.impl.ForcedThirdPersonFrontChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.cursecycle.network.CurseCycleSyncPayload;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.UUID;

public class ForcedThirdPersonFrontGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void immediateSyncPersistenceAndReconnect(TestContext context) throws Exception {
        var server = context.getWorld().getServer();
        var state = CurseCycleState.getServerState(server);
        var first = connect(context, UUID.randomUUID());
        var second = connect(context, UUID.randomUUID());
        drainLastSync(first);
        drainLastSync(second);

        // No server tick or command-level sync: activation itself must inform every client.
        CycleManager.activateChallenge(server, state, ForcedThirdPersonFrontChallenge.ID);
        assertIds(context, first, List.of(ForcedThirdPersonFrontChallenge.ID));
        assertIds(context, second, List.of(ForcedThirdPersonFrontChallenge.ID));

        CycleManager.activateChallenge(server, state, "double_challenge:forced_third_person_front,no_sprint");
        List<String> both = List.of(ForcedThirdPersonFrontChallenge.ID, "no_sprint");
        assertIds(context, first, both);
        assertIds(context, second, both);

        var late = connect(context, UUID.randomUUID());
        assertIds(context, late, both);
        server.getPlayerManager().remove(late.player());

        // Read the actual compressed file written by PersistentStateManager, not unused Challenge hooks.
        var persistence = server.getOverworld().getPersistentStateManager();
        persistence.save();
        var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                SharedConstants.getGameVersion().getSaveVersion().getId());
        var restored = CurseCycleState.createFromNbt(saved.getCompound("data"), context.getWorld().getRegistryManager());
        context.assertEquals(both, restored.getActiveChallengeIds(), "Both challenge IDs must be saved to disk");
        persistence.set(CurseCycleState.STATE_KEY, restored);
        CycleManager.syncState(server, restored, CurseCycleMod.getConfig());
        assertIds(context, first, both);
        assertIds(context, second, both);

        UUID reconnectId = first.player().getUuid();
        server.getPlayerManager().remove(first.player());
        first = connect(context, reconnectId);
        assertIds(context, first, both);

        // Stop while a player is offline, keeping the companion challenge active for everyone.
        UUID offlineId = second.player().getUuid();
        server.getPlayerManager().remove(second.player());
        CycleManager.activateChallenge(server, restored, "no_sprint");
        assertIds(context, first, List.of("no_sprint"));
        first.player().setSprinting(true);
        context.assertFalse(first.player().isSprinting(), "The companion restriction must remain active");

        // Rejoining must not receive the stale camera restriction.
        second = connect(context, offlineId);
        assertIds(context, second, List.of("no_sprint"));

        server.getPlayerManager().remove(first.player());
        server.getPlayerManager().remove(second.player());
        CycleManager.activateChallenge(server, restored, "test_easy");
        context.complete();
    }

    private record ConnectedPlayer(ServerPlayerEntity player, EmbeddedChannel channel) {}

    private static ConnectedPlayer connect(TestContext context, UUID id) {
        var server = context.getWorld().getServer();
        var data = ConnectedClientData.createDefault(new GameProfile(id, "front-" + id.toString().substring(0, 8)), false);
        var player = new ServerPlayerEntity(server, context.getWorld(), data.gameProfile(), data.syncedOptions());
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(GameMode.SURVIVAL);
        return new ConnectedPlayer(player, channel);
    }

    private static CurseCycleSyncPayload drainLastSync(ConnectedPlayer player) {
        player.channel().runPendingTasks();
        CurseCycleSyncPayload last = null;
        Object packet;
        while ((packet = player.channel().readOutbound()) != null) {
            if (packet instanceof CustomPayloadS2CPacket custom && custom.payload() instanceof CurseCycleSyncPayload sync) {
                last = sync;
            }
        }
        return last;
    }

    private static void assertIds(TestContext context, ConnectedPlayer player, List<String> expected) {
        var payload = drainLastSync(player);
        context.assertTrue(payload != null, "A state sync packet must reach " + player.player().getName().getString());
        context.assertEquals(expected, List.copyOf(payload.activeChallengeIds()), "Client must receive the current active IDs");
    }
}
