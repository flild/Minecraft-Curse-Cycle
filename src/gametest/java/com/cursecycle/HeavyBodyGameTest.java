package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.HeavyBodyChallenge;
import com.cursecycle.network.HeavyBodyPayload;
import com.cursecycle.state.CurseCycleState;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class HeavyBodyGameTest implements FabricGameTest {
    private static final String ID = HeavyBodyChallenge.ID;
    private static void near(TestContext c, double expected, double actual, String message) {
        c.assertTrue(Math.abs(expected - actual) < 0.00001, message + ": expected=" + expected + ", actual=" + actual);
    }
    private static void intent(ChallengeScenario s, ServerPlayerEntity p, long serial, boolean moving) {
        var buf = new RegistryByteBuf(Unpooled.buffer(), p.getRegistryManager());
        try {
            HeavyBodyPayload.CODEC.encode(buf, new HeavyBodyPayload(serial, moving));
            HeavyBodyChallenge.receive(p, HeavyBodyPayload.CODEC.decode(buf));
            s.context.assertEquals(0, buf.readableBytes(), "Input codec consumes packet");
        } finally { buf.release(); }
    }
    private static void intent(ChallengeScenario s, ServerPlayerEntity p, boolean moving) {
        intent(s, p, s.state.getActivationSerial(), moving);
    }
    private static ArrayList<Object> packets(ChallengeScenario s, ServerPlayerEntity p) {
        var channel = s.channel(p); channel.runPendingTasks(); channel.flush();
        var found = new ArrayList<Object>(); Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof PlaySoundS2CPacket sound && sound.getSound().value().getId().equals(HeavyBodyChallenge.PANT.getId())) {
                near(s.context, .35, sound.getVolume(), "Pant volume");
                var buf = new RegistryByteBuf(Unpooled.buffer(), p.getRegistryManager());
                try {
                    PlaySoundS2CPacket.CODEC.encode(buf, sound);
                    s.context.assertEquals(HeavyBodyChallenge.PANT.getId(), PlaySoundS2CPacket.CODEC.decode(buf).getSound().value().getId(), "Sound codec");
                } finally { buf.release(); }
                found.add(packet);
            }
            if (packet instanceof StopSoundS2CPacket stop && HeavyBodyChallenge.PANT.getId().equals(stop.getSoundId())) found.add(packet);
        }
        return found;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "heavy_body_physics", tickLimit = 40)
    public void realPhysicsEffectsExclusionsAndLifecycle(TestContext c) throws Exception {
        var s = new ChallengeScenario(c); var server = c.getWorld().getServer();
        var p = s.player(UUID.randomUUID(), true); var challenge = ChallengeRegistry.get(ID);
        var base = c.getAbsolutePos(new BlockPos(3, 4, 3));
        Map<BlockPos, net.minecraft.block.BlockState> blocks = new LinkedHashMap<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y <= 3; y++) {
            var pos = base.add(x, y, z); blocks.put(pos, c.getWorld().getBlockState(pos));
            c.getWorld().setBlockState(pos, y == 0 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        try {
            double control = walk(p, base, true), jump = jump(p, base);
            var commands = server.getCommandManager();
            commands.executeWithPrefix(server.getCommandSource(), "fdc list");
            commands.executeWithPrefix(server.getCommandSource(), "fdc activate heavy_body");
            commands.executeWithPrefix(server.getCommandSource(), "fdc active");
            c.assertTrue(s.state.getActiveChallengeIds().contains(ID), "Real activation command");
            near(c, control * .6, walk(p, base, true), "100 travel steps at 60 percent distance");
            near(c, jump * .6, jump(p, base), "Jump impulse at 60 percent");
            p.setSprinting(true); c.assertTrue(p.isSprinting(), "Sprint allowed");
            double heavySprint = walk(p, base, true);
            s.activate("test_easy"); p.setSprinting(true);
            near(c, walk(p, base, true) * .6, heavySprint, "Sprint scales once"); p.setSprinting(false);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 1200, 1));
            double boostedWalk = walk(p, base, true), boostedJump = jump(p, base);
            s.activate(ID); challenge.onPlayerJoin(server, p); challenge.onStart(server, p);
            near(c, boostedWalk * .6, walk(p, base, true), "Speed II and idempotence");
            near(c, boostedJump * .6, jump(p, base), "Jump Boost included before scaling");
            p.changeGameMode(GameMode.CREATIVE); near(c, boostedWalk, walk(p, base, true), "Creative unchanged");
            p.changeGameMode(GameMode.SPECTATOR); c.assertFalse(HeavyBodyChallenge.active(p), "Spectator excluded");
            p.changeGameMode(GameMode.ADVENTURE); near(c, boostedWalk * .6, walk(p, base, true), "Adventure affected");
            p.changeGameMode(GameMode.SURVIVAL);
            double air = walk(p, base, false);
            p.getAbilities().flying = true; double flight = walk(p, base, true); p.getAbilities().flying = false;
            var boat = new BoatEntity(c.getWorld(), base.getX(), base.getY()+1, base.getZ());
            p.startRiding(boat, true); near(c, 1, HeavyBodyChallenge.scale(p, 1), "Vehicle excluded"); p.stopRiding(); boat.discard();
            c.getWorld().setBlockState(base.up(), Blocks.WATER.getDefaultState()); reset(p, base, true); p.baseTick();
            c.assertTrue(p.isTouchingWater(), "Water control"); double water = walk(p, base, true);
            s.activate("test_easy"); near(c, water, walk(p, base, true), "Water unchanged");
            c.getWorld().setBlockState(base.up(), Blocks.AIR.getDefaultState()); reset(p, base, true); p.baseTick();
            near(c, air, walk(p, base, false), "Air control unchanged");
            p.getAbilities().flying = true; near(c, flight, walk(p, base, true), "Flight unchanged"); p.getAbilities().flying = false;
            s.activate("double_challenge:heavy_body,no_jumping"); near(c, 0, jump(p, base), "no_jumping priority");
            s.activate("double_challenge:heavy_body,no_sprint"); p.setSprinting(true); c.assertFalse(p.isSprinting(), "no_sprint priority");
            near(c, boostedWalk * .6, walk(p, base, true), "Double challenge slowdown");
            s.activate("no_sprint"); near(c, boostedWalk, walk(p, base, true), "Replacing removes slowdown");
            c.assertTrue(p.hasStatusEffect(StatusEffects.SPEED) && p.hasStatusEffect(StatusEffects.JUMP_BOOST), "External effects survive");
            s.activate("test_easy"); s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID);
            near(c, boostedWalk * .6, walk(p, base, true), "PERSONAL activation");
            s.state.getActiveChallengeIds().add(ID); near(c, boostedWalk * .6, walk(p, base, true), "Duplicate IDs do not square multiplier");
            challenge.setEnabled(false); near(c, boostedWalk, walk(p, base, true), "Disabled"); challenge.setEnabled(true);
            s.state.getPersonalActiveChallengeIds(p.getUuid()).clear(); s.activate(ID);
            var late = s.player(); late.setOnGround(true); near(c, .6, HeavyBodyChallenge.scale(late, 1), "Late join");
            UUID uuid = late.getUuid(); server.getPlayerManager().remove(late); late = s.player(uuid);
            late.setOnGround(true); near(c, .6, HeavyBodyChallenge.scale(late, 1), "Reconnect");
            late.setHealth(0); c.assertFalse(HeavyBodyChallenge.active(late), "Dead object excluded");
            late = s.respawn(late, false); late.setOnGround(true); near(c, .6, HeavyBodyChallenge.scale(late, 1), "Respawn");
            late.teleport(server.getWorld(World.NETHER), base.getX(), 80, base.getZ(), 0, 0); late.setOnGround(true);
            near(c, .6, HeavyBodyChallenge.scale(late, 1), "Nether");
            late.teleport(c.getWorld(), base.getX(), base.getY()+1, base.getZ(), 0, 0);
            var persistence = server.getOverworld().getPersistentStateManager(); persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            var restored = CurseCycleState.createFromNbt(saved.getCompound("data"), c.getWorld().getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, restored);
            near(c, boostedWalk * .6, walk(p, base, true), "Actual disk state restores active physics");
            c.assertFalse(restored.getChallengeData().contains(ID), "No breathing state persisted");
            server.getPlayerManager().remove(late);
            com.cursecycle.state.CycleManager.activateChallenge(server, restored, "test_easy");
            late = s.player(uuid); late.setOnGround(true); near(c, 1, HeavyBodyChallenge.scale(late, 1), "Ended while offline");
        } finally {
            challenge.setEnabled(true); HeavyBodyChallenge.clear(server); s.close();
            blocks.forEach((pos, state) -> c.getWorld().setBlockState(pos, state, Block.NOTIFY_LISTENERS));
        }
        c.waitAndRun(5, c::complete);
    }

    private static void reset(ServerPlayerEntity p, BlockPos base, boolean ground) {
        p.refreshPositionAndAngles(base.getX()+.5, base.getY()+1, base.getZ()+.5, 0, 0);
        p.setOnGround(ground); p.setVelocity(Vec3d.ZERO);
        p.setMovementSpeed((float) p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
    }
    private static double walk(ServerPlayerEntity p, BlockPos base, boolean ground) {
        reset(p, base, ground); double distance = 0;
        for (int i = 0; i < 100; i++) {
            // Keep the supporting block, retain accumulated horizontal velocity/friction.
            var velocity = p.getVelocity(); reset(p, base, ground); p.setVelocity(velocity.x, 0, velocity.z);
            double z = p.getZ(); p.travel(new Vec3d(0, 0, 1)); distance += p.getZ() - z;
        }
        return distance;
    }
    private static double jump(ServerPlayerEntity p, BlockPos base) { reset(p, base, true); p.jump(); return p.getVelocity().y; }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "heavy_body_timing", tickLimit = 360)
    public void realTicksInputIndependentPlayersAndSoundCleanup(TestContext c) {
        var s = new ChallengeScenario(c); var server = c.getWorld().getServer();
        var p = s.player(); var other = s.player(); s.activate(ID);
        p.setNoGravity(true); other.setNoGravity(true);
        var base = c.getAbsolutePos(new BlockPos(3, 20, 3));
        p.refreshPositionAndAngles(base.getX(), base.getY(), base.getZ(), 0, 0);
        other.refreshPositionAndAngles(base.getX()+3, base.getY(), base.getZ(), 0, 0);
        intent(s, p, true); intent(s, other, true);
        HeavyBodyChallenge.tick(server); packets(s, p); packets(s, other);
        int start = server.getTicks();
        for (int i = 1; i <= 320; i++) {
            final int tick = i;
            c.waitAndRun(i, () -> {
                try {
                    c.assertEquals(tick, server.getTicks()-start, "Real server tick");
                    // 1..49 move, 50 rest resets; 51..150 earns the first pant.
                    boolean move = tick != 50 && tick <= 230;
                    p.setPosition(p.getX(), p.getY(), p.getZ() + (move ? .04 : 0)); p.setOnGround(true);
                    other.setPosition(other.getX(), other.getY(), other.getZ() + (tick <= 100 ? .019 : .04)); other.setOnGround(true);
                    if (tick == 101) intent(s, other, false);
                    if (tick == 121) intent(s, other, true);
                    if (tick == 231) intent(s, p, false);
                    HeavyBodyChallenge.tick(server); HeavyBodyChallenge.tick(server);
                    var one = packets(s, p); var two = packets(s, other);
                    if (tick == 150 || tick == 230) c.assertEquals(1L, one.stream().filter(PlaySoundS2CPacket.class::isInstance).count(), "100 ticks, then 80 tick interval");
                    else c.assertTrue(one.stream().noneMatch(PlaySoundS2CPacket.class::isInstance), "Unexpected first pant tick="+tick);
                    if (tick == 270) c.assertTrue(one.stream().anyMatch(StopSoundS2CPacket.class::isInstance), "Stop at exact 40th resting tick");
                    if (tick == 220 || tick == 300) c.assertEquals(1L, two.stream().filter(PlaySoundS2CPacket.class::isInstance).count(), "Independent timer and input exclusion");
                    else c.assertTrue(two.stream().noneMatch(PlaySoundS2CPacket.class::isInstance), "Unexpected second pant tick="+tick);
                    if (tick == 320) {
                        s.activate("no_sprint");
                        c.assertTrue(packets(s, other).stream().anyMatch(StopSoundS2CPacket.class::isInstance), "Deactivation stops own sound");
                        HeavyBodyChallenge.clear(server); s.close(); c.complete();
                    }
                } catch (Throwable failure) { HeavyBodyChallenge.clear(server); s.close(); throw failure; }
            });
        }
    }
}
