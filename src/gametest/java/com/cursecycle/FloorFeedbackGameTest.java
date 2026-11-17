package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.FloorIsLavaChallenge;
import com.cursecycle.challenge.impl.FragileFloorChallenge;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

public class FloorFeedbackGameTest implements FabricGameTest {
    private static List<Object> packets(ChallengeScenario scenario, ServerPlayerEntity player) {
        var channel = scenario.channel(player);
        channel.runPendingTasks(); channel.flush();
        var result = new ArrayList<>();
        Object packet;
        while ((packet = channel.readOutbound()) != null) result.add(packet);
        return result;
    }

    private static List<BlockBreakingProgressS2CPacket> cracks(List<Object> packets) {
        return packets.stream().filter(BlockBreakingProgressS2CPacket.class::isInstance)
                .map(BlockBreakingProgressS2CPacket.class::cast).toList();
    }

    private static void position(ServerPlayerEntity player, BlockPos floor, double x, double height) {
        player.refreshPositionAndAngles(floor.getX() + x, floor.getY() + height, floor.getZ() + 0.5, 0, 0);
        player.setVelocity(Vec3d.ZERO);
        player.setNoGravity(true);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "floor_feedback", tickLimit = 90)
    public void crackProgressSoundAndImmediateCleanup(TestContext context) {
        var scenario = new ChallengeScenario(context);
        var world = context.getWorld();
        var server = world.getServer();
        var challenge = (FragileFloorChallenge) ChallengeRegistry.get(FragileFloorChallenge.ID);
        var floor = context.getAbsolutePos(new BlockPos(2, 3, 2));
        var original = world.getBlockState(floor);
        var originalEast = world.getBlockState(floor.east());
        var player = scenario.player();
        var observer = scenario.player();
        observer.changeGameMode(GameMode.CREATIVE);
        position(player, floor, 0.5, 1);
        position(observer, floor, 0.5, 3);
        world.setBlockState(floor, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        scenario.activate(FragileFloorChallenge.ID);
        var received = new ArrayList<Object>();
        int[] tick = {0};
        boolean[] done = {false};
        context.runAtEveryTick(() -> {
            if (done[0]) return;
            try {
                tick[0]++;
                position(player, floor, tick[0] >= 46 ? 4 : 0.5, 1);
                position(observer, floor, 0.5, 3);
                challenge.onTick(server, player);
                var currentPackets = packets(scenario, player);
                received.addAll(currentPackets);
                if (tick[0] == 46) {
                    assertCleared(context, currentPackets, 1, "Walking away clears on the next server tick");
                    done[0] = true;
                    challenge.onStop(server, null);
                    scenario.close();
                    world.setBlockState(floor, original, Block.NOTIFY_LISTENERS);
                    world.setBlockState(floor.east(), originalEast, Block.NOTIFY_LISTENERS);
                    context.complete();
                    return;
                }
                if (tick[0] < 45) return;

                context.assertTrue(world.getBlockState(floor).isAir(), "Warning does not delay the existing 40-tick collapse");
                var stages = cracks(received).stream().filter(packet -> packet.getPos().equals(floor))
                        .map(BlockBreakingProgressS2CPacket::getProgress).toList();
                for (int stage = 0; stage <= 9; stage++) context.assertTrue(stages.contains(stage), "Visible crack stage " + stage);
                context.assertEquals(-1, stages.getLast(), "Collapse clears its animation");
                context.assertEquals(11, stages.size(), "Only ten stage changes and one clear packet");
                var sounds = received.stream().filter(PlaySoundS2CPacket.class::isInstance)
                        .map(PlaySoundS2CPacket.class::cast)
                        .filter(sound -> sound.getSound().value().equals(Blocks.STONE.getDefaultState().getSoundGroup().getHitSound())).toList();
                context.assertEquals(5, sounds.size(), "Five increasingly urgent creaks");
                for (int i = 1; i < sounds.size(); i++) {
                    context.assertTrue(sounds.get(i).getVolume() > sounds.get(i - 1).getVolume(), "Creak volume rises");
                    context.assertTrue(sounds.get(i).getPitch() > sounds.get(i - 1).getPitch(), "Creak pitch rises");
                }
                context.assertTrue(cracks(packets(scenario, observer)).stream().anyMatch(packet -> packet.getProgress() == 9),
                        "Nearby players see the warning too");

                world.setBlockState(floor, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(floor.east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                scenario.activate(FragileFloorChallenge.ID);
                packets(scenario, player);
                position(player, floor, 1, 1);
                challenge.onTick(server, player);
                var boundary = cracks(packets(scenario, player));
                context.assertEquals(2, boundary.size(), "Both supporting blocks have warnings");
                context.assertEquals(2L, boundary.stream().map(BlockBreakingProgressS2CPacket::getEntityId).distinct().count(),
                        "Separate animation IDs for simultaneous supports");
                context.assertTrue(boundary.stream().allMatch(packet -> packet.getEntityId() < 0), "No collision with real mining IDs");
                challenge.onTick(server, player);
                context.assertTrue(cracks(packets(scenario, player)).isEmpty(), "Duplicate dispatch does not resend animations");
                challenge.onPlayerJump(server, player);
                assertCleared(context, packets(scenario, player), 2, "Jump clears both supports immediately");

                challenge.onTick(server, player); packets(scenario, player);
                challenge.onStop(server, player);
                assertCleared(context, packets(scenario, player), 2, "Stopping clears both supports");
                position(player, floor, 0.5, 1);
                challenge.onTick(server, player); packets(scenario, player);
                challenge.setEnabled(false);
                assertCleared(context, packets(scenario, player), 1, "Disable clears before dispatcher pruning");
                challenge.setEnabled(true);
                challenge.onTick(server, player); packets(scenario, player);
                player.changeGameMode(GameMode.CREATIVE);
                challenge.onTick(server, player);
                assertCleared(context, packets(scenario, player), 1, "Exempt game mode clears warnings");
                player.changeGameMode(GameMode.SURVIVAL);
                challenge.onTick(server, player); packets(scenario, player);
                position(player, floor, 4, 1);
            } catch (Throwable failure) {
                done[0] = true;
                challenge.setEnabled(true); challenge.onStop(server, null); scenario.close();
                world.setBlockState(floor, original, Block.NOTIFY_LISTENERS);
                world.setBlockState(floor.east(), originalEast, Block.NOTIFY_LISTENERS);
                throw failure;
            }
        });
    }

    private static void assertCleared(TestContext context, List<Object> packets, int count, String why) {
        var cracks = cracks(packets);
        context.assertEquals(count, cracks.size(), why);
        context.assertTrue(cracks.stream().allMatch(packet -> packet.getProgress() == -1), why);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "lava_feedback")
    public void sparksOnlyOnDangerousDamagePulses(TestContext context) {
        try (var scenario = new ChallengeScenario(context)) {
            var world = context.getWorld();
            var server = world.getServer();
            var player = scenario.player();
            var challenge = (FloorIsLavaChallenge) ChallengeRegistry.get(FloorIsLavaChallenge.ID);
            var floor = context.getAbsolutePos(new BlockPos(2, 3, 2));
            var original = world.getBlockState(floor);
            try {
                scenario.activate(FloorIsLavaChallenge.ID);
                world.setBlockState(floor, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                position(player, floor, 0.5, 1);
                packets(scenario, player);
                challenge.onTick(server, player);
                var sparks = particles(packets(scenario, player));
                context.assertEquals(1, sparks.size(), "One particle batch per damage pulse");
                var spark = sparks.getFirst();
                context.assertEquals(ParticleTypes.LAVA, spark.getParameters(), "Vanilla lava sparks");
                context.assertEquals(3, spark.getCount(), "Small three-spark burst");
                context.assertTrue(Math.abs(spark.getY() - (floor.getY() + 1.05)) < 0.001, "Sparks at the feet");
                context.assertEquals(17F, player.getHealth(), "Existing damage unchanged");
                challenge.onTick(server, player);
                context.assertTrue(particles(packets(scenario, player)).isEmpty(), "No duplicate burst in damage cooldown");

                player.age += 20;
                challenge.onTick(server, player);
                context.assertEquals(1, particles(packets(scenario, player)).size(), "Next pulse has sparks");
                for (var block : List.of(Blocks.WHITE_WOOL, Blocks.RED_BED, Blocks.HAY_BLOCK, Blocks.WHITE_CARPET)) {
                    challenge.onPlayerLeave(server, player);
                    world.setBlockState(floor, block.getDefaultState(), Block.NOTIFY_LISTENERS);
                    position(player, floor, 0.5, block == Blocks.RED_BED ? 9.0 / 16 : block == Blocks.WHITE_CARPET ? 1.0 / 16 : 1);
                    challenge.onTick(server, player);
                    context.assertTrue(particles(packets(scenario, player)).isEmpty(), "Safe support has no sparks: " + block);
                }
                position(player, floor, 0.5, 3);
                challenge.onTick(server, player);
                context.assertTrue(particles(packets(scenario, player)).isEmpty(), "No sparks while airborne");
                world.setBlockState(floor, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                position(player, floor, 0.5, 1);
                player.changeGameMode(GameMode.CREATIVE);
                challenge.onTick(server, player);
                context.assertTrue(particles(packets(scenario, player)).isEmpty(), "Creative has no sparks");
            } finally {
                challenge.onPlayerLeave(server, player);
                world.setBlockState(floor, original, Block.NOTIFY_LISTENERS);
            }
        }
        context.complete();
    }

    private static List<ParticleS2CPacket> particles(List<Object> packets) {
        return packets.stream().filter(ParticleS2CPacket.class::isInstance).map(ParticleS2CPacket.class::cast).toList();
    }
}
