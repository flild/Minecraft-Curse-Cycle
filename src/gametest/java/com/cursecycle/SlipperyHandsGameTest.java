package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.SlipperyHandsChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.UUID;

public class SlipperyHandsGameTest implements FabricGameTest {
    // Synchronous to keep the shared challenge selection isolated from other tests.
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void icePhysicsAndLifecycle(TestContext context) throws Exception {
        var world = context.getWorld();
        var server = world.getServer();
        var manager = server.getPlayerManager();
        var state = CurseCycleState.getServerState(server);
        BlockPos floor = context.getAbsolutePos(new BlockPos(2, 3, 2));
        // Long enough for a second of walking followed by a second of coasting.
        for (int z = -2; z <= 18; z++) {
            for (int x = -1; x <= 1; x++) {
                world.setBlockState(floor.add(x, 0, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                for (int y = 1; y <= 3; y++) world.setBlockState(floor.add(x, y, z), Blocks.AIR.getDefaultState());
            }
        }
        ServerPlayerEntity player = createPlayer(context, UUID.randomUUID());
        var challenge = ChallengeRegistry.get(SlipperyHandsChallenge.ID);
        try {
            CycleManager.activateChallenge(server, state, "test_easy");
            double normal = step(player, floor, true, Vec3d.ZERO);
            world.setBlockState(floor, Blocks.ICE.getDefaultState());
            double ice = step(player, floor, true, Vec3d.ZERO);
            double iceAcceleration = step(player, floor, true, new Vec3d(0, 0, 1));
            world.setBlockState(floor, Blocks.STONE.getDefaultState());

            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc list");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc activate slippery_hands");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc active");
            context.assertTrue(state.getActiveChallengeIds().contains(SlipperyHandsChallenge.ID), "Activation command must select the new ID");
            near(context, ice, step(player, floor, true, Vec3d.ZERO), "Stone must retain exactly as much inertia as ordinary ice");
            near(context, iceAcceleration, step(player, floor, true, new Vec3d(0, 0, 1)), "Acceleration must also match ice");
            context.assertTrue(ice > normal * 1.5, "Control: ice must retain more velocity than normal ground");

            reset(player, floor, true);
            player.setVelocity(Vec3d.ZERO);
            for (int i = 0; i < 20; i++) player.travel(new Vec3d(0, 0, 1));
            double walkingSpeed = player.getVelocity().z;
            double releasePosition = player.getZ();
            for (int i = 0; i < 20; i++) player.travel(Vec3d.ZERO);
            context.assertTrue(player.getZ() - releasePosition > 0.5, "Releasing movement after a second must leave visible drift");
            context.assertTrue(player.getVelocity().z > 0 && player.getVelocity().z < walkingSpeed, "Coasting must gradually slow down");

            player.changeGameMode(GameMode.CREATIVE);
            near(context, normal, step(player, floor, true, Vec3d.ZERO), "Creative walking must keep normal friction");
            player.changeGameMode(GameMode.SPECTATOR);
            context.assertFalse(SlipperyHandsChallenge.isActiveForPlayer(player), "Spectator must be exempt");
            player.changeGameMode(GameMode.ADVENTURE);
            near(context, ice, step(player, floor, true, Vec3d.ZERO), "Adventure must slide immediately");
            player.changeGameMode(GameMode.SURVIVAL);

            double activeAir = step(player, floor, false, Vec3d.ZERO);
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            double activeFlight = step(player, floor, true, Vec3d.ZERO);
            player.getAbilities().flying = false;
            player.getAbilities().allowFlying = false;
            var boat = new BoatEntity(world, floor.getX(), floor.getY() + 1, floor.getZ());
            context.assertTrue(player.startRiding(boat, true), "Control: player is riding a boat");
            near(context, 0.6, SlipperyHandsChallenge.getSlipperiness(player, 0.6F), "Boats must be exempt");
            player.stopRiding();
            var cart = new MinecartEntity(world, floor.getX(), floor.getY() + 1, floor.getZ());
            context.assertTrue(player.startRiding(cart, true), "Control: player is riding a minecart");
            near(context, 0.6, SlipperyHandsChallenge.getSlipperiness(player, 0.6F), "Minecarts must be exempt");
            player.stopRiding();

            // Compare actual fluid travel with the challenge enabled and disabled.
            world.setBlockState(floor.up(), Blocks.WATER.getDefaultState());
            reset(player, floor, true);
            player.baseTick();
            context.assertTrue(player.isTouchingWater(), "Control: water physics are active");
            double activeWater = step(player, floor, true, Vec3d.ZERO);
            CycleManager.activateChallenge(server, state, "test_easy");
            near(context, activeWater, step(player, floor, true, Vec3d.ZERO), "Water movement must be unchanged");
            world.setBlockState(floor.up(), Blocks.AIR.getDefaultState());
            player.baseTick();
            near(context, normal, step(player, floor, true, Vec3d.ZERO), "Stopping must restore normal ground travel");
            near(context, activeAir, step(player, floor, false, Vec3d.ZERO), "Airborne movement must be unchanged");
            player.getAbilities().flying = true;
            near(context, activeFlight, step(player, floor, true, Vec3d.ZERO), "Flight must be unchanged");
            player.getAbilities().flying = false;

            CycleManager.activateChallenge(server, state, SlipperyHandsChallenge.ID);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1));
            challenge.onStart(server, player);
            challenge.onPlayerJoin(server, player);
            near(context, ice, step(player, floor, true, Vec3d.ZERO), "Repeated activation must not stack friction changes");
            ServerPlayerEntity late = createPlayer(context, UUID.randomUUID());
            near(context, ice, step(late, floor, true, Vec3d.ZERO), "Late joins inherit global physics");
            manager.remove(late);

            UUID id = player.getUuid();
            manager.remove(player);
            player = createPlayer(context, id);
            near(context, ice, step(player, floor, true, Vec3d.ZERO), "Reconnect inherits global physics");
            player.kill();
            player = manager.respawnPlayer(player, false, Entity.RemovalReason.KILLED);
            player.networkHandler.player = player;
            reset(player, floor, true);
            context.assertTrue(SlipperyHandsChallenge.isActiveForPlayer(player), "New player entity after death remains affected");
            near(context, Blocks.ICE.getSlipperiness(), SlipperyHandsChallenge.getSlipperiness(player, 0.6F), "Respawn needs no modifier reapplication");
            var nether = server.getWorld(World.NETHER);
            player.teleport(nether, floor.getX() + 0.5, 80, floor.getZ() + 0.5, 0, 0);
            player.setOnGround(true);
            context.assertTrue(player.getWorld() == nether, "Control: player reached Nether");
            near(context, Blocks.ICE.getSlipperiness(), SlipperyHandsChallenge.getSlipperiness(player, 0.6F), "Dimension changes keep the effect");
            player.teleport(world, floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 0, 0);

            var persistence = server.getOverworld().getPersistentStateManager();
            persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    SharedConstants.getGameVersion().getSaveVersion().getId());
            var restored = CurseCycleState.createFromNbt(saved.getCompound("data"), world.getRegistryManager());
            context.assertTrue(restored.getActiveChallengeIds().contains(SlipperyHandsChallenge.ID), "Active ID must actually be saved on disk");
            persistence.set(CurseCycleState.STATE_KEY, restored);
            state = restored;
            context.assertTrue(SlipperyHandsChallenge.isActiveForPlayer(player), "Restored state resumes the effect");

            CycleManager.activateChallenge(server, state, "double_challenge:slippery_hands,no_sprint");
            player.setOnGround(true);
            player.setSprinting(true);
            context.assertFalse(player.isSprinting(), "Parallel no_sprint must still work");
            near(context, Blocks.ICE.getSlipperiness(), SlipperyHandsChallenge.getSlipperiness(player, 0.6F), "Double challenge keeps ice friction");
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1));
            Vec3d velocity = player.getVelocity();
            CycleManager.activateChallenge(server, state, "test_easy");
            context.assertTrue(player.hasStatusEffect(StatusEffects.SPEED), "Stopping must preserve other effects");
            context.assertEquals(velocity, player.getVelocity(), "Stopping must preserve current velocity");
            near(context, 0.6, SlipperyHandsChallenge.getSlipperiness(player, 0.6F), "Stopping restores original friction immediately");

            state.getPersonalActiveChallengeIds(player.getUuid()).add(SlipperyHandsChallenge.ID);
            near(context, Blocks.ICE.getSlipperiness(), SlipperyHandsChallenge.getSlipperiness(player, 0.6F), "Per-player lookup must work too");
            challenge.setEnabled(false);
            near(context, 0.6, SlipperyHandsChallenge.getSlipperiness(player, 0.6F), "Disabled challenges cannot change friction");
            state.getPersonalActiveChallengeIds(player.getUuid()).clear();
        } finally {
            challenge.setEnabled(true);
            manager.remove(player);
            CycleManager.activateChallenge(server, state, "test_easy");
        }
        // Let chunk tasks queued by dimension transfers finish before server shutdown.
        context.waitAndRun(5, context::complete);
    }

    private static void near(TestContext context, double expected, double actual, String message) {
        context.assertTrue(Math.abs(expected - actual) < 0.00001,
                message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void reset(ServerPlayerEntity player, BlockPos floor, boolean onGround) {
        player.refreshPositionAndAngles(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 0, 0);
        player.setOnGround(onGround);
        player.setVelocity(0, 0, 0.2);
        player.setMovementSpeed(0.1F);
    }

    private static double step(ServerPlayerEntity player, BlockPos floor, boolean onGround, Vec3d input) {
        reset(player, floor, onGround);
        player.travel(input);
        return player.getVelocity().z;
    }

    private static ServerPlayerEntity createPlayer(TestContext context, UUID id) {
        var server = context.getWorld().getServer();
        var client = ConnectedClientData.createDefault(new GameProfile(id, "ice-" + id.toString().substring(0, 8)), false);
        // Real players simulate travel on the controlling client. Enable that same
        // vanilla path here to measure the applied common Mixin on a headless server.
        var player = new ServerPlayerEntity(server, context.getWorld(), client.gameProfile(), client.syncedOptions()) {
            @Override
            public boolean isLogicalSideForUpdatingMovement() { return true; }
        };
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, player, client);
        player.changeGameMode(GameMode.SURVIVAL);
        return player;
    }
}
