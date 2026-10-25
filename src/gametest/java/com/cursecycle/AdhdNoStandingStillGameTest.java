package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.AdhdNoStandingStillChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdhdNoStandingStillGameTest implements FabricGameTest {
    // A separate batch isolates the real, asynchronous server ticks from tests changing global IDs.
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "adhd_no_standing_still", tickLimit = 900)
    public void penaltiesExemptionsAndLifecycle(TestContext context) {
        new Scenario(context).schedule();
    }

    private static final class Scenario {
        final TestContext context;
        final MinecraftServer server;
        final AdhdNoStandingStillChallenge challenge;
        final NbtCompound previousState;
        final List<ConnectedPlayer> connections = new ArrayList<>();
        final BlockPos origin;
        CurseCycleState state;
        ServerPlayerEntity first, late, adventure, creative, spectator, boatRider, cartRider, falling, moving;
        boolean fallingAndMoving = true;
        boolean finished;

        Scenario(TestContext context) {
            this.context = context;
            server = context.getWorld().getServer();
            state = CurseCycleState.getServerState(server);
            previousState = state.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager());
            challenge = (AdhdNoStandingStillChallenge) ChallengeRegistry.get(AdhdNoStandingStillChallenge.ID);
            origin = context.getAbsolutePos(new BlockPos(1, 2, 1));
        }

        void schedule() {
            at(1, () -> {
                // Earlier batches can leave mock players online. This batch needs an empty
                // fixture to exercise activation/deactivation with no connected players.
                for (var existing : List.copyOf(server.getPlayerManager().getPlayerList())) {
                    server.getPlayerManager().remove(existing);
                }
                first = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                first.getAbilities().invulnerable = false;
                adventure = connect(UUID.randomUUID(), GameMode.ADVENTURE);
                creative = connect(UUID.randomUUID(), GameMode.CREATIVE);
                spectator = connect(UUID.randomUUID(), GameMode.SPECTATOR);
                boatRider = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                cartRider = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                falling = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                moving = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                context.assertTrue(boatRider.startRiding(new BoatEntity(context.getWorld(), origin.getX(), 80, origin.getZ()), true),
                        "Control: boat passenger");
                context.assertTrue(cartRider.startRiding(new MinecartEntity(context.getWorld(), origin.getX(), 80, origin.getZ()), true),
                        "Control: minecart passenger");
                var pig = context.spawnEntity(EntityType.PIG, new BlockPos(2, 2, 2));
                pig.setNoGravity(true);
                pig.setAiDisabled(true);
                context.runAtTick(125, () -> context.assertFalse(pig.hasStatusEffect(StatusEffects.WITHER), "Mobs remain unaffected"));
                command("fdc list");
                command("fdc activate " + AdhdNoStandingStillChallenge.ID);
                command("fdc active");
                context.assertTrue(state.getActiveChallengeIds().contains(AdhdNoStandingStillChallenge.ID), "Command activation");
            });
            context.runAtEveryTick(() -> {
                if (finished) return;
                // Supply only movement samples and network keepalive replies. CycleManager is
                // invoked by the real END_SERVER_TICK event; effect duration and damage tick normally.
                for (var connection : List.copyOf(connections)) {
                    connection.channel.runPendingTasks();
                    Object packet;
                    while ((packet = connection.channel.readOutbound()) != null) {
                        if (packet instanceof KeepAliveS2CPacket keepAlive) {
                            connection.player.networkHandler.onKeepAlive(new KeepAliveC2SPacket(keepAlive.getId()));
                        }
                        if (packet instanceof PlayerPositionLookS2CPacket teleport) {
                            connection.player.networkHandler.onTeleportConfirm(new TeleportConfirmC2SPacket(teleport.getTeleportId()));
                        }
                        ReferenceCountUtil.release(packet);
                    }
                }
                if (falling != null && fallingAndMoving) {
                    // More than 60 ticks of descent without even crossing an integer Y boundary.
                    falling.refreshPositionAndAngles(origin.getX() + 0.5, 80.9 - context.getTick() * 0.001,
                            origin.getZ() + 0.5, 0, 0);
                    moving.refreshPositionAndAngles(origin.getX() + (context.getTick() / 30) % 2 + 0.5,
                            80, origin.getZ() + 0.5, 0, 0);
                }
            });
            at(33, () -> late = connect(UUID.randomUUID(), GameMode.SURVIVAL));
            at(64, () -> {
                duration(first, 200);
                duration(adventure, 200);
                noWither(late, "Late join gets its own full grace period");
                for (var exempt : List.of(creative, spectator, boatRider, cartRider, falling, moving)) {
                    noWither(exempt, "Exempt or moving player");
                }
                // Reapplying lifecycle hooks must neither reset the clock nor add a penalty.
                challenge.onStart(server, first);
                challenge.onPlayerJoin(server, first);
            });
            at(96, () -> duration(late, 200));
            at(125, () -> {
                duration(first, 400);
                context.assertTrue(first.getHealth() < 20, "Vanilla Wither must actually damage the survival player");
                for (var exempt : List.of(creative, spectator, boatRider, cartRider, falling, moving)) {
                    noWither(exempt, "Exceptions remain safe past the second threshold");
                }
                creative.changeGameMode(GameMode.SURVIVAL);
                spectator.changeGameMode(GameMode.ADVENTURE);
                creative.getAbilities().invulnerable = true;
                spectator.getAbilities().invulnerable = true;
                boatRider.stopRiding();
                cartRider.stopRiding();
                fallingAndMoving = false;
                moving.refreshPositionAndAngles(moving.getX() + 1, moving.getY(), moving.getZ(), 0, 0);
                UUID id = first.getUuid();
                disconnect(first);
                first = connect(id, GameMode.SURVIVAL);
            });
            at(188, () -> {
                duration(first, 600); // Reconnect resets position, but not accumulated violations.
                for (var resumed : List.of(creative, spectator, boatRider, cartRider, falling, moving)) duration(resumed, 200);
                first.setHealth(0);
                first.onDeath(first.getDamageSources().genericKill());
                first = server.getPlayerManager().respawnPlayer(first, false, Entity.RemovalReason.KILLED);
                first.networkHandler.player = first;
                // Refresh the connection's player reference just as the real client respawn path does.
                for (int i = 0; i < connections.size(); i++) {
                    var connection = connections.get(i);
                    if (connection.player.getUuid().equals(first.getUuid())) {
                        connections.set(i, new ConnectedPlayer(first, connection.channel, connection.connection));
                    }
                }
                prepare(first, GameMode.SURVIVAL);
                noWither(first, "Respawn clears vanilla effects and starts a fresh grace period");
            });
            at(249, () -> noWither(first, "Respawn must not reuse the old immobility timer"));
            at(252, () -> {
                duration(first, 800);
                first.teleport(server.getWorld(World.NETHER), first.getX(), first.getY(), first.getZ(), 0, 0);
            });
            at(313, () -> context.assertTrue(first.getStatusEffect(StatusEffects.WITHER).getDuration() <= 800,
                    "Even the same coordinates in a different dimension start a new grace period"));
            at(315, () -> {
                duration(first, 1000);
                first.teleport(server.getWorld(World.END), first.getX(), first.getY(), first.getZ(), 0, 0);
            });
            at(378, () -> {
                duration(first, 1200);
                first.teleport(context.getWorld(), origin.getX() + 0.5, 80, origin.getZ() + 0.5, 0, 0);
                CycleManager.activateChallenge(server, state, AdhdNoStandingStillChallenge.ID);
                first.removeStatusEffect(StatusEffects.WITHER);
                first.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 1200, 1));
                adventure.removeStatusEffect(StatusEffects.WITHER);
                adventure.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, StatusEffectInstance.INFINITE, 0));
                late.removeStatusEffect(StatusEffects.WITHER);
                late.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 1200, 0));
                boatRider.removeStatusEffect(StatusEffects.WITHER);
                boatRider.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200));
            });
            at(441, () -> {
                context.assertEquals(1, first.getStatusEffect(StatusEffects.WITHER).getAmplifier(), "Stronger Wither is preserved");
                context.assertTrue(first.getStatusEffect(StatusEffects.WITHER).getDuration() > 1100, "Stronger Wither duration is preserved");
                context.assertTrue(adventure.getStatusEffect(StatusEffects.WITHER).isInfinite(), "Infinite Wither is preserved");
                context.assertTrue(late.getStatusEffect(StatusEffects.WITHER).getDuration() > 1100, "Longer Wither I is preserved");
                duration(boatRider, 200);
                CycleManager.activateChallenge(server, state, "double_challenge:adhd_no_standing_still,no_sprint");
                boatRider.setSprinting(true);
                context.assertFalse(boatRider.isSprinting(), "Parallel no_sprint remains effective");
            });
            at(504, () -> {
                duration(boatRider, 200);
                var wither = boatRider.getStatusEffect(StatusEffects.WITHER);
                var speed = boatRider.getStatusEffect(StatusEffects.SPEED);
                CycleManager.activateChallenge(server, state, "no_sprint");
                context.assertTrue(wither == boatRider.getStatusEffect(StatusEffects.WITHER), "Stop leaves existing Wither to expire naturally");
                context.assertTrue(speed == boatRider.getStatusEffect(StatusEffects.SPEED), "Stop preserves unrelated effects");
                UUID reconnectId = boatRider.getUuid();
                for (var connection : List.copyOf(connections)) disconnect(connection.player);
                context.assertTrue(server.getPlayerManager().getPlayerList().isEmpty(), "Control: nobody is online");
                CycleManager.activateChallenge(server, state, AdhdNoStandingStillChallenge.ID);
                CycleManager.activateChallenge(server, state, "test_easy");
                CycleManager.activateChallenge(server, state, AdhdNoStandingStillChallenge.ID);
                boatRider = connect(reconnectId, GameMode.SURVIVAL);
                boatRider.removeStatusEffect(StatusEffects.WITHER);
            });
            at(567, () -> {
                duration(boatRider, 200); // Reset even without onStop calls or a state-object reload.
                var persistence = server.getOverworld().getPersistentStateManager();
                persistence.save();
                var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                        SharedConstants.getGameVersion().getSaveVersion().getId());
                state = CurseCycleState.createFromNbt(saved.getCompound("data"), context.getWorld().getRegistryManager());
                context.assertTrue(state.getActiveChallengeIds().contains(AdhdNoStandingStillChallenge.ID), "Active ID written to disk");
                persistence.set(CurseCycleState.STATE_KEY, state);
                challenge.onPlayerJoin(server, boatRider);
            });
            at(630, () -> {
                duration(boatRider, 200);
                UUID id = boatRider.getUuid();
                disconnect(boatRider);
                CycleManager.activateChallenge(server, state, "test_easy");
                boatRider = connect(id, GameMode.SURVIVAL);
                boatRider.removeStatusEffect(StatusEffects.WITHER);
            });
            at(693, () -> {
                noWither(boatRider, "Ending while offline leaves no stale monitoring");
                late = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                state.getPersonalActiveChallengeIds(boatRider.getUuid()).add(AdhdNoStandingStillChallenge.ID);
            });
            at(757, () -> {
                duration(boatRider, 200);
                noWither(late, "Personal activation affects only its owner");
                challenge.setEnabled(false);
                boatRider.removeStatusEffect(StatusEffects.WITHER);
            });
            at(820, () -> {
                noWither(boatRider, "Disabled challenge does not monitor players");
                cleanup();
                context.complete();
            });
        }

        void duration(ServerPlayerEntity player, int expected) {
            StatusEffectInstance effect = player.getStatusEffect(StatusEffects.WITHER);
            context.assertTrue(effect != null, "Expected Wither on " + player.getName().getString());
            context.assertEquals(0, effect.getAmplifier(), "Penalty is Wither I");
            context.assertTrue(effect.getDuration() <= expected && effect.getDuration() >= expected - 5,
                    "Expected fresh duration near " + expected + ", got " + effect.getDuration()
                            + " on " + player.getName().getString() + " at tick " + context.getTick());
        }

        void noWither(ServerPlayerEntity player, String message) {
            context.assertFalse(player.hasStatusEffect(StatusEffects.WITHER), message);
        }

        ServerPlayerEntity connect(UUID id, GameMode mode) {
            var data = ConnectedClientData.createDefault(new GameProfile(id, "adhd-" + id.toString().substring(0, 8)), false);
            var player = new ServerPlayerEntity(server, context.getWorld(), data.gameProfile(), data.syncedOptions());
            var connection = new ClientConnection(NetworkSide.SERVERBOUND);
            var channel = new EmbeddedChannel(connection);
            server.getPlayerManager().onPlayerConnect(connection, player, data);
            // EmbeddedChannel is not accepted by a listening socket. Register the connection
            // explicitly so vanilla ticks the network handler, player effects and damage.
            server.getNetworkIo().getConnections().add(connection);
            connections.add(new ConnectedPlayer(player, channel, connection));
            prepare(player, mode);
            return player;
        }

        void prepare(ServerPlayerEntity player, GameMode mode) {
            player.changeGameMode(mode);
            player.getAbilities().invulnerable = true;
            player.setNoGravity(true);
            player.refreshPositionAndAngles(origin.getX() + 0.5, 80, origin.getZ() + 0.5, 0, 0);
            player.setVelocity(0, 0, 0);
        }

        void disconnect(ServerPlayerEntity player) {
            challenge.onPlayerLeave(server, player);
            server.getPlayerManager().remove(player);
            connections.removeIf(connection -> {
                if (connection.player != player) return false;
                server.getNetworkIo().getConnections().remove(connection.connection);
                connection.channel.finishAndReleaseAll();
                return true;
            });
        }

        void command(String command) {
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
        }

        void at(long tick, Step step) {
            context.runAtTick(tick, () -> {
                try {
                    step.run();
                } catch (Exception | AssertionError failure) {
                    cleanup();
                    throw new RuntimeException("ADHD scenario at tick " + tick + ": " + failure.getMessage(), failure);
                }
            });
        }

        void cleanup() {
            finished = true;
            challenge.setEnabled(true);
            for (var connection : List.copyOf(connections)) disconnect(connection.player);
            server.getOverworld().getPersistentStateManager().set(CurseCycleState.STATE_KEY,
                    CurseCycleState.createFromNbt(previousState, context.getWorld().getRegistryManager()));
        }
    }

    private record ConnectedPlayer(ServerPlayerEntity player, EmbeddedChannel channel, ClientConnection connection) {}

    @FunctionalInterface
    private interface Step {
        void run() throws Exception;
    }
}
