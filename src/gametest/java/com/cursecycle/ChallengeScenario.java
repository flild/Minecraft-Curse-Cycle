package com.cursecycle;

import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.IdentityHashMap;
import java.util.Map;

/** Isolates the shared registry activation used by synchronous gameplay scenarios. */
public final class ChallengeScenario implements AutoCloseable {
    public final TestContext context;
    public final CurseCycleState state;
    private final NbtCompound original;
    private final List<ServerPlayerEntity> players = new ArrayList<>();
    private final Map<ServerPlayerEntity, EmbeddedChannel> channels = new IdentityHashMap<>();
    public ChallengeScenario(TestContext context) {
        this.context = context;
        state = CurseCycleState.getServerState(context.getWorld().getServer());
        original = state.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager());
        // The roulette may select any newly registered mechanic before tests start.
        activate("test_easy");
    }
    public ServerPlayerEntity player() {
        return player(UUID.randomUUID());
    }
    public ServerPlayerEntity player(UUID uuid) {
        var server = context.getWorld().getServer();
        var data = ConnectedClientData.createDefault(new GameProfile(uuid, "spec-"+uuid.toString().substring(0,8)), false);
        var player = new ServerPlayerEntity(server, context.getWorld(), data.gameProfile(), data.syncedOptions());
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        channels.put(player, new EmbeddedChannel(connection));
        server.getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(GameMode.SURVIVAL);
        players.add(player);
        return player;
    }
    public EmbeddedChannel channel(ServerPlayerEntity player) { return channels.get(player); }
    public void activate(String id) { CycleManager.activateChallenge(context.getWorld().getServer(), state, id); }
    public ServerPlayerEntity respawn(ServerPlayerEntity previous, boolean alive) {
        var player = previous.getServer().getPlayerManager().respawnPlayer(previous, alive,
                alive ? net.minecraft.entity.Entity.RemovalReason.CHANGED_DIMENSION : net.minecraft.entity.Entity.RemovalReason.KILLED);
        player.networkHandler.player = player;
        player.changeGameMode(GameMode.SURVIVAL);
        players.add(player);
        return player;
    }
    @Override public void close() {
        var manager = context.getWorld().getServer().getPlayerManager();
        for (var player : players) if (manager.getPlayerList().contains(player)) manager.remove(player);
        channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);
        context.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY,
                CurseCycleState.createFromNbt(original, context.getWorld().getRegistryManager()));
    }
}
