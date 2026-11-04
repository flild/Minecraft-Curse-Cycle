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

    private static java.util.List<HeavyBodyPayload> contexts(ChallengeScenario s, ServerPlayerEntity p) {
        var channel = s.channel(p); channel.runPendingTasks(); channel.flush();
        var found = new java.util.ArrayList<HeavyBodyPayload>(); Object packet;
        while ((packet = channel.readOutbound()) != null)
            if (packet instanceof net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket custom
                    && custom.payload() instanceof HeavyBodyPayload heavy) found.add(heavy);
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
                    // Repeated valid packets never restart accumulation or create extra sounds.
                    intent(s, p, move); intent(s, p, move);
                    if (tick == 75) intent(s, p, s.state.getActivationSerial() - 1, false);
                    if (tick == 100) server.getOverworld().setTimeOfDay(server.getOverworld().getTimeOfDay() + 24000);
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
    @SuppressWarnings("unchecked")
    private static java.util.Map<Object, java.util.Map<Object, Object>> sessions() throws Exception {
        var field = HeavyBodyChallenge.class.getDeclaredField("SESSIONS"); field.setAccessible(true);
        return (java.util.Map<Object, java.util.Map<Object, Object>>) field.get(null);
    }
    private static void disconnect(ServerPlayerEntity player) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                .onPlayDisconnect(player.networkHandler, player.getServer());
        player.getServer().getPlayerManager().remove(player);
    }
    private static CurseCycleState reload(TestContext c) throws Exception {
        var server = c.getWorld().getServer(); var manager = server.getOverworld().getPersistentStateManager();
        CurseCycleState.getServerState(server).markDirty(); manager.save();
        var disk = manager.readNbt(CurseCycleState.STATE_KEY, null,
                net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
        var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager());
        manager.set(CurseCycleState.STATE_KEY, loaded); return loaded;
    }
    private static void foreign(TestContext c, ServerPlayerEntity p, net.minecraft.item.ItemStack cargo) {
        c.assertTrue(net.minecraft.item.ItemStack.areEqual(cargo, p.getInventory().getStack(8)), "Foreign named items");
        c.assertEquals(7, p.experienceLevel, "Foreign XP"); c.assertEquals(.625F, p.experienceProgress, "Foreign XP progress");
        c.assertEquals(17F, p.getHealth(), "Foreign HP");
        c.assertTrue(p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE), "Foreign effect");
    }


    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "heavy_body_disk")
    public void sessionsScopesLifecycleAndDiskNeverReplayBreathing(TestContext c) throws Exception {
        var server = c.getWorld().getServer();
        try (var s = new ChallengeScenario(c)) {
            var p = s.player(); var other = s.player(); var uuid = p.getUuid();
            var challenge = ChallengeRegistry.get(ID);
            var cargo = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 17);
            cargo.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("foreign-heavy-review"));
            p.getInventory().clear(); p.getInventory().setStack(8, cargo.copy());
            p.experienceLevel = 7; p.experienceProgress = .625F; p.setHealth(17);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 1200, 0));
            s.activate("test_easy"); s.state.getPersonalActiveChallengeIds(uuid).add(ID);
            contexts(s, p); contexts(s, other);
            com.cursecycle.network.NetworkHandler.syncToAll(server, 1, 20, s.state.getActiveChallengeIds(), "", java.util.List.of());
            var sync = contexts(s, p);
            c.assertEquals(java.util.List.of(new HeavyBodyPayload(s.state.getActivationSerial(), false)), sync, "Actual S2C context to PERSONAL owner");
            c.assertTrue(contexts(s, other).isEmpty(), "No context to unrelated player");
            intent(s, other, true); c.assertFalse(sessions().containsKey(server), "Unrelated PERSONAL recipient cannot create session");
            intent(s, p, true); c.assertEquals(1, sessions().get(server).size(), "Only PERSONAL owner has input");
            HeavyBodyChallenge.clear(server); s.state.getPersonalActiveChallengeIds(uuid).clear();
            for (int cycle = 0; cycle < 32; cycle++) {
                long serial = s.state.getActivationSerial(); s.activate("double_challenge:heavy_body,heavy_body");
                c.assertEquals(serial + 1, s.state.getActivationSerial(), "One activation for duplicate ID");
                intent(s, p, serial, true); c.assertFalse(sessions().containsKey(server), "Old activation cannot create session");
                intent(s, p, true); intent(s, p, true); intent(s, other, true);
                c.assertEquals(2, sessions().get(server).size(), "Bounded one session per player");
                var old = sessions().get(server).get(uuid);
                challenge.onPlayerJoin(server, p); challenge.onStart(server, p);
                c.assertFalse(sessions().get(server).containsKey(uuid), "Repeated start clears own accumulation");
                c.assertEquals(1, sessions().get(server).size(), "Other session retained");
                intent(s, p, true); c.assertTrue(old != sessions().get(server).get(uuid), "Fresh breathing object");
                challenge.setEnabled(false); HeavyBodyChallenge.tick(server);
                c.assertFalse(sessions().containsKey(server), "Disabled tick releases player/world/server references");
                challenge.setEnabled(true); s.activate("double_challenge:heavy_body,no_sprint"); intent(s, p, true);
                p.setOnGround(true); near(c, .6, HeavyBodyChallenge.scale(p, 1), "Both mechanics active");
                p.setSprinting(true); c.assertFalse(p.isSprinting(), "Companion active");
                s.activate("no_sprint"); c.assertFalse(sessions().containsKey(server), "End clears last bucket");
                near(c, 1, HeavyBodyChallenge.scale(p, 1), "End removes own multiplier");
                p.setSprinting(true); c.assertFalse(p.isSprinting(), "End retains no_sprint"); foreign(c, p, cargo);
            }
            s.activate(ID); intent(s, p, true); intent(s, other, true);
            p.changeGameMode(GameMode.CREATIVE); HeavyBodyChallenge.tick(server);
            c.assertFalse(sessions().get(server).containsKey(uuid), "Creative drops timer");
            p.changeGameMode(GameMode.SURVIVAL); intent(s, p, true);
            p.teleport(server.getWorld(World.NETHER), 0, 80, 0, 0, 0); HeavyBodyChallenge.tick(server);
            var current = sessions().get(server).get(uuid);
            var inputField = current.getClass().getDeclaredField("input"); inputField.setAccessible(true);
            c.assertFalse(inputField.getBoolean(current), "Dimension replacement has no old input");
            p.teleport(c.getWorld(), 0, 80, 0, 0, 0); intent(s, p, true);
            var oldPlayer = other; other = s.respawn(other, true);
            c.assertFalse(sessions().get(server).containsKey(oldPlayer.getUuid()), "Respawn releases old object");
            disconnect(other); intent(s, p, true);
            s.state.getChallengeData().putString("foreign-heavy", "preserve");
            disconnect(p); c.assertFalse(sessions().containsKey(server), "Actual Fabric disconnect clears last session");
            var loaded = reload(c); c.assertFalse(loaded.getChallengeData().contains(ID), "Disk has no input or breathing clocks");
            c.assertEquals("preserve", loaded.getChallengeData().getString("foreign-heavy"), "Foreign world state");
            p = s.player(uuid); foreign(c, p, cargo); p.setOnGround(true);
            near(c, .6, HeavyBodyChallenge.scale(p, 1), "Active disk rejoin physics");
            c.assertFalse(sessions().containsKey(server), "Rejoin does not replay old breathing");
            com.cursecycle.state.CycleManager.activateChallenge(server, loaded, "no_sprint"); disconnect(p); reload(c);
            p = s.player(uuid); foreign(c, p, cargo); p.setOnGround(true);
            near(c, 1, HeavyBodyChallenge.scale(p, 1), "Ended disk rejoin"); disconnect(p);
            loaded = reload(c); com.cursecycle.state.CycleManager.activateChallenge(server, loaded, ID); reload(c);
            com.cursecycle.state.CycleManager.activateChallenge(server, CurseCycleState.getServerState(server), "no_sprint"); reload(c);
            p = s.player(uuid); foreign(c, p, cargo); p.setOnGround(true);
            near(c, 1, HeavyBodyChallenge.scale(p, 1), "Empty server offline end stays ended");
            p.setSprinting(true); c.assertFalse(p.isSprinting(), "Disk end retains companion"); disconnect(p);
            c.assertFalse(sessions().containsKey(server), "No retained context after repeated lifecycle");
        } finally { ChallengeRegistry.get(ID).setEnabled(true); HeavyBodyChallenge.clear(server); }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "heavy_body_pairs")
    public void realJumpGravityAndAutoJumpKeepIndependentPhysics(TestContext c) {
        var server = c.getWorld().getServer();
        try (var s = new ChallengeScenario(c)) {
            var p = s.player(UUID.randomUUID(), true);
            p.setOnGround(true); p.setVelocity(Vec3d.ZERO); p.jump(); double ordinary = p.getVelocity().y;
            s.activate("double_challenge:heavy_body,gravity_ten");
            p.setOnGround(true); p.setVelocity(Vec3d.ZERO); p.jump(); near(c, ordinary * .6, p.getVelocity().y, "Gravity leaves scaled jump impulse");
            p.setOnGround(false); p.setVelocity(Vec3d.ZERO); p.travel(Vec3d.ZERO); double both = p.getVelocity().y;
            s.activate(ID); p.setOnGround(false); p.setVelocity(Vec3d.ZERO); p.travel(Vec3d.ZERO);
            c.assertTrue(Math.abs(p.getVelocity().y) < Math.abs(both), "Ending gravity keeps ordinary heavy air branch");
            s.activate("double_challenge:heavy_body,gravity_ten");
            s.activate("gravity_ten"); p.setOnGround(false); p.setVelocity(Vec3d.ZERO); p.travel(Vec3d.ZERO);
            near(c, both, p.getVelocity().y, "Ending heavy preserves gravity travel");
            s.activate("double_challenge:heavy_body,auto_jump");
            p.setOnGround(true); p.setVelocity(Vec3d.ZERO);
            com.cursecycle.challenge.impl.AutoJumpChallenge.tick(server);
            near(c, ordinary * .6, p.getVelocity().y, "Automatic real jump passes heavy multiplier once");
            c.assertFalse(p.isOnGround(), "Automatic takeoff actually happened");
            s.activate(ID); p.setOnGround(true); p.setVelocity(Vec3d.ZERO);
            com.cursecycle.challenge.impl.AutoJumpChallenge.tick(server);
            near(c, 0, p.getVelocity().y, "Ending auto leaves no automatic jump");
            p.jump(); near(c, ordinary * .6, p.getVelocity().y, "Ending auto retains heavy manual jump");
            s.activate("double_challenge:heavy_body,auto_jump");
            s.activate("auto_jump"); p.setOnGround(true); p.setVelocity(Vec3d.ZERO);
            com.cursecycle.challenge.impl.AutoJumpChallenge.tick(server);
            near(c, ordinary, p.getVelocity().y, "Ending heavy keeps automatic vanilla impulse");
            s.activate("double_challenge:heavy_body,silent_world");
            p.setOnGround(true); p.setVelocity(Vec3d.ZERO); p.jump();
            near(c, ordinary * .6, p.getVelocity().y, "Silence keeps heavy physics");
            s.activate(ID); p.setOnGround(true); p.setVelocity(Vec3d.ZERO); p.jump();
            near(c, ordinary * .6, p.getVelocity().y, "Ending silence keeps heavy physics");
        } finally { HeavyBodyChallenge.clear(server); com.cursecycle.challenge.impl.AutoJumpChallenge.clear(server); }
        c.complete();
    }
}
