package com.cursecycle;

import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.impl.StuckThrottleChallenge;
import com.cursecycle.network.CurseCycleSyncPayload;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Dedicated-server activation and the real S2C gate; input/travel is checked in the CLIENT probe. */
public class StuckThrottleGameTest implements FabricGameTest {
    private static final String ID = StuckThrottleChallenge.ID;

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "stuck_throttle")
    public void vanillaWallJumpAndBlockInteractionRemainAvailable(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var world = context.getWorld(); var p = s.player(); s.activate(ID);
            var floor = context.getAbsolutePos(new net.minecraft.util.math.BlockPos(2, 1, 2));
            var wall = floor.south();
            for (var pos : net.minecraft.util.math.BlockPos.iterate(floor.add(-1, -1, -1), floor.add(1, 3, 2)))
                world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState());
            world.setBlockState(floor.down(), net.minecraft.block.Blocks.STONE.getDefaultState());
            world.setBlockState(wall, net.minecraft.block.Blocks.STONE.getDefaultState());
            world.setBlockState(wall.up(), net.minecraft.block.Blocks.STONE.getDefaultState());
            p.teleport(world, floor.getX() + .5, floor.getY(), floor.getZ() + .5, 0, 0);
            p.onTeleportationDone(); p.setOnGround(true);
            p.move(net.minecraft.entity.MovementType.SELF, new net.minecraft.util.math.Vec3d(0, 0, 2));
            context.assertTrue(p.getBoundingBox().maxZ <= wall.getZ() + 1e-6, "Wall blocks normal forward movement");
            p.setVelocity(net.minecraft.util.math.Vec3d.ZERO); p.setOnGround(true); p.jump();
            context.assertTrue(p.getVelocity().y > .4, "Vanilla jump impulse remains available");
            var lever = floor.west();
            world.setBlockState(lever.down(), net.minecraft.block.Blocks.STONE.getDefaultState());
            world.setBlockState(lever, net.minecraft.block.Blocks.LEVER.getDefaultState()
                    .with(net.minecraft.state.property.Properties.BLOCK_FACE, net.minecraft.block.enums.BlockFace.FLOOR));
            var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(lever),
                    net.minecraft.util.math.Direction.UP, lever, false);
            p.interactionManager.interactBlock(p, world, net.minecraft.item.ItemStack.EMPTY, net.minecraft.util.Hand.MAIN_HAND, hit);
            context.assertTrue(world.getBlockState(lever).get(net.minecraft.state.property.Properties.POWERED), "Real block interaction works");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "stuck_throttle")
    public void commandsRecipientsModesRespawnAndDimensions(TestContext context) throws Exception {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer();
            var first = s.player(); var channel = s.channel(first);
            var second = s.player();
            var challenge = ChallengeRegistry.get(ID);
            context.assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty(), "Difficulty");
            context.assertEquals(ChallengeScope.GLOBAL, challenge.getScope(), "Global challenge");
            context.assertEquals(1L, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count(), "Single registration");
            var source = server.getCommandSource().withLevel(2);
            context.assertTrue(server.getCommandManager().getDispatcher().execute("fdc activate " + ID, source) > 0, "Real activate command");
            assertIds(context, channel, List.of(ID));
            assertIds(context, s.channel(second), List.of(ID));
            var late = s.player(); assertIds(context, s.channel(late), List.of(ID));
            for (var mode : GameMode.values()) {
                first.changeGameMode(mode);
                context.assertEquals(mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE,
                        ChallengeActivity.active(first, ID), "Server activity matches client mode gate");
            }
            first.changeGameMode(GameMode.SURVIVAL); first.setHealth(0);
            context.assertFalse(ChallengeActivity.active(first, ID), "Dead player excluded");
            first = s.respawn(first, false);
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertIds(context, channel, List.of(ID));
            for (var dimension : List.of(World.NETHER, World.END, World.OVERWORLD)) {
                first.teleport(server.getWorld(dimension), .5, 80, .5, 90, 0);
                first.onTeleportationDone();
                CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
                assertIds(context, channel, List.of(ID));
                context.assertTrue(ChallengeActivity.active(first, ID), "World change keeps eligibility");
            }
            s.activate("double_challenge:stuck_throttle,no_sprint");
            assertIds(context, channel, List.of(ID, "no_sprint"));
            first.setSprinting(true); context.assertFalse(first.isSprinting(), "no_sprint remains authoritative");
            context.assertTrue(server.getCommandManager().getDispatcher().execute("fdc reset", source) > 0, "Real reset");
            assertIds(context, channel, List.of());
            context.assertFalse(ChallengeActivity.active(first, ID), "Reset removes effect");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "stuck_throttle")
    public void effectivePersonalIdsDeduplicationDisableAndReactivation(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer();
            var owner = s.player(); var other = s.player();
            s.activate("no_sprint");
            s.state.getPersonalActiveChallenges().put(owner.getUuid(), new ArrayList<>(List.of(ID, ID)));
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertIds(context, s.channel(owner), List.of("no_sprint", ID));
            assertIds(context, s.channel(other), List.of("no_sprint"));
            context.assertTrue(ChallengeActivity.active(owner, ID), "Personal activity");
            context.assertFalse(ChallengeActivity.active(other, ID), "No personal leakage");
            s.state.getActiveChallengeIds().add(ID);
            var challenge = ChallengeRegistry.get(ID);
            try {
                challenge.setEnabled(false);
                CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
                assertIds(context, s.channel(owner), List.of("no_sprint"));
                assertIds(context, s.channel(other), List.of("no_sprint"));
                context.assertFalse(ChallengeActivity.active(owner, ID), "Disable gates both scopes");
            } finally { challenge.setEnabled(true); }
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertIds(context, s.channel(owner), List.of("no_sprint", ID));
            assertIds(context, s.channel(other), List.of("no_sprint", ID));
            // A global activation intentionally preserves PERSONAL overrides. End that fixture scope explicitly.
            s.state.getPersonalActiveChallenges().remove(owner.getUuid());
            for (int repeat = 0; repeat < 8; repeat++) {
                s.activate("double_challenge:stuck_throttle,stuck_throttle");
                assertIds(context, s.channel(owner), List.of(ID));
                s.activate("test_easy"); assertIds(context, s.channel(owner), List.of("test_easy"));
                context.assertFalse(s.state.getChallengeData().contains(ID), "No retained input session");
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "stuck_throttle_disk")
    public void diskReloadReconnectAndOfflineEndPreservePlayerData(TestContext context) throws Exception {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer(); var player = s.player(); var uuid = player.getUuid();
            player.getInventory().clear();
            var cargo = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 17);
            player.getInventory().setStack(8, cargo.copy()); player.experienceLevel = 7; player.setHealth(17);
            s.activate(ID); disconnect(player);
            var loaded = reload(context);
            player = s.player(uuid); assertIds(context, s.channel(player), List.of(ID));
            context.assertTrue(net.minecraft.item.ItemStack.areEqual(cargo, player.getInventory().getStack(8)), "Inventory preserved");
            context.assertEquals(7, player.experienceLevel, "XP preserved");
            context.assertEquals(17F, player.getHealth(), "Health preserved");
            disconnect(player);
            CycleManager.activateChallenge(server, loaded, "no_sprint"); reload(context);
            player = s.player(uuid); assertIds(context, s.channel(player), List.of("no_sprint"));
            context.assertFalse(ChallengeActivity.active(player, ID), "Offline end survives disk");
            player.setSprinting(true); context.assertFalse(player.isSprinting(), "Companion preserved");
        }
        context.complete();
    }

    private static CurseCycleState reload(TestContext context) throws Exception {
        var manager = context.getWorld().getServer().getOverworld().getPersistentStateManager();
        CurseCycleState.getServerState(context.getWorld().getServer()).markDirty(); manager.save();
        var disk = manager.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
        var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), context.getWorld().getRegistryManager());
        context.assertFalse(loaded.getChallengeData().contains(ID), "No input/keybinding snapshot in NBT");
        manager.set(CurseCycleState.STATE_KEY, loaded); return loaded;
    }

    private static void disconnect(ServerPlayerEntity player) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                .onPlayDisconnect(player.networkHandler, player.getServer());
        player.getServer().getPlayerManager().remove(player);
    }

    private static void assertIds(TestContext context, EmbeddedChannel channel, List<String> expected) {
        channel.runPendingTasks(); channel.flush();
        CurseCycleSyncPayload last = null; Object packet;
        while ((packet = channel.readOutbound()) != null)
            if (packet instanceof CustomPayloadS2CPacket custom && custom.payload() instanceof CurseCycleSyncPayload sync) last = sync;
        context.assertTrue(last != null, "Real S2C packet received");
        context.assertEquals(expected, last.activeChallengeIds(), "Only effective enabled IDs received");
    }
}
