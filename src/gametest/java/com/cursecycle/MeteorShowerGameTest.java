package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.MeteorShowerChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

import java.lang.reflect.*;
import java.util.*;

public class MeteorShowerGameTest implements FabricGameTest {
    private static final String ID = MeteorShowerChallenge.ID;
    private static Object get(Object object, String name) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void set(Object object, String name, Object value) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Object invoke(String name, Object... args) {
        try {
            var method = Arrays.stream(MeteorShowerChallenge.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
            method.setAccessible(true); return method.invoke(null, args);
        } catch (InvocationTargetException e) { throw new AssertionError(e.getCause()); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Object storm(MinecraftServer server) {
        try {
            var f = MeteorShowerChallenge.class.getDeclaredField("STORMS"); f.setAccessible(true);
            return ((Map<?, ?>) f.get(null)).get(server);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @SuppressWarnings("unchecked") private static List<Object> meteors(Object storm) { return (List<Object>) get(storm, "meteors"); }
    @SuppressWarnings("unchecked") private static Map<UUID, Object> sessions(Object storm) { return (Map<UUID, Object>) get(storm, "sessions"); }
    private static long age(MinecraftServer server) {
        return Integer.toUnsignedLong(server.getTicks() - (Integer) get(storm(server), "start"));
    }
    private static void step(MinecraftServer server) {
        if (storm(server) != null) set(storm(server), "lastTick", server.getTicks() - 1);
        MeteorShowerChallenge.tick(server);
    }
    private static Object meteor(ServerPlayerEntity p, Vec3d target, long issued) {
        var session = sessions(storm(p.getServer())).get(p.getUuid());
        try {
            var type = Class.forName(MeteorShowerChallenge.class.getName() + "$Meteor");
            var ctor = type.getDeclaredConstructors()[0]; ctor.setAccessible(true);
            return ctor.newInstance(session, target, issued);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static boolean advance(Object m, long now) { return (Boolean) invoke("advance", m, now); }
    private static void impact(TestContext c, Object m) {
        for (int i = 0; i < 40; i++) if (advance(m, 60L + i)) return;
        c.throwGameTestException("No bounded impact");
    }

    /** All chunk requests/terrain edits belong to the fixture, never to production search/flight. */
    private static final class Arena implements AutoCloseable {
        final TestContext c;
        final ChallengeScenario s;
        final MinecraftServer server;
        final BlockPos base;
        final Vec3d target;
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final List<net.minecraft.entity.Entity> entities = new ArrayList<>();
        Arena(TestContext c) {
            this.c = c; s = new ChallengeScenario(c); server = c.getWorld().getServer();
            base = c.getAbsolutePos(new BlockPos(2, 48, 2)); target = Vec3d.ofBottomCenter(base.up());
            for (int cx = (base.getX() - 40) >> 4; cx <= (base.getX() + 40) >> 4; cx++)
                for (int cz = (base.getZ() - 40) >> 4; cz <= (base.getZ() + 40) >> 4; cz++) c.getWorld().getChunk(cx, cz);
            for (int x = -14; x <= 14; x++) for (int z = -14; z <= 14; z++) block(base.add(x, 0, z), Blocks.DIRT);
        }
        void block(BlockPos pos, Block block) {
            blocks.putIfAbsent(pos.toImmutable(), c.getWorld().getBlockState(pos));
            c.getWorld().setBlockState(pos, block.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        ServerPlayerEntity player() {
            var p = s.player();
            for (int i = 0; i < 61; i++) p.tick();
            p.setHealth(20); p.timeUntilRegen = 0; p.setNoGravity(true);
            p.refreshPositionAndAngles(target.x, target.y, target.z, 0, 0); return p;
        }
        void activate(String id) { s.activate(id); step(server); }
        PigEntity pig(Vec3d at) {
            var pig = EntityType.PIG.create(c.getWorld()); pig.setAiDisabled(true); pig.setNoGravity(true);
            pig.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0); c.getWorld().spawnEntity(pig); entities.add(pig); return pig;
        }
        @Override public void close() {
            MeteorShowerChallenge.clear(server); entities.forEach(net.minecraft.entity.Entity::discard);
            blocks.forEach((pos, state) -> c.getWorld().setBlockState(pos, state, Block.NOTIFY_LISTENERS));
            c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                    new Box(base).expand(20), e -> true).forEach(net.minecraft.entity.Entity::discard);
            s.close();
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_warning")
    public void warningFlightAndRealDamageDestruction(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); p.setPosition(a.target.add(10, 0, 0)); a.activate(ID);
            var pig = a.pig(a.target.add(1.5, 0, 0));
            var m = meteor(p, a.target, 0);
            for (long t = 0; t < 60; t++) {
                c.assertFalse(advance(m, t), "Warning only");
                c.assertTrue(c.getWorld().getBlockState(a.base).isOf(Blocks.DIRT), "No destruction on warning");
                c.assertTrue(pig.getHealth() == 10, "No warning damage");
            }
            c.assertTrue(((Vec3d) get(m, "position")).equals(a.target.add(0, 24, 0)), "Stationary for 60 ticks");
            for (long t = 60; t < 75; t++) c.assertFalse(advance(m, t), "Flight precedes contact");
            c.assertTrue(c.getWorld().getBlockState(a.base).isOf(Blocks.DIRT), "No damage in flight");
            c.assertTrue(advance(m, 75), "Contact exactly on step 16 at 1.5 blocks/tick");
            c.assertTrue(pig.getHealth() < 10, "Real explosion damages mob");
            c.assertTrue(c.getWorld().getBlockState(a.base).isAir(), "Real explosion removes dirt");
            a.s.activate("test_easy");
            c.assertTrue(c.getWorld().getBlockState(a.base).isAir(), "End does not repair terrain");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_roof")
    public void existingRoofIsMarkedAndNewRoofInterceptsFlight(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); var observer = a.player(); observer.changeGameMode(GameMode.CREATIVE); a.activate(ID);
            for (int x = -14; x <= 14; x++) for (int z = -14; z <= 14; z++) a.block(a.base.add(x, 7, z), Blocks.OBSIDIAN);
            var phase = storm(a.server); var owner = sessions(phase).get(p.getUuid());
            for (var client : List.of(p, observer)) {
                a.s.channel(client).runPendingTasks(); a.s.channel(client).flush();
                while (a.s.channel(client).readOutbound() != null) { }
            }
            invoke("issue", phase, owner, 0L);
            c.assertEquals(3, meteors(phase).size(), "Three real surface selections");
            for (var client : List.of(p, observer)) {
                a.s.channel(client).runPendingTasks(); a.s.channel(client).flush();
                var packets = new ArrayList<Object>(); Object packet;
                while ((packet = a.s.channel(client).readOutbound()) != null) packets.add(packet);
                var particles = packets.stream().filter(ParticleS2CPacket.class::isInstance)
                        .map(ParticleS2CPacket.class::cast).filter(v -> v.getParameters().getType() == ParticleTypes.FLAME).toList();
                c.assertEquals(36, particles.size(), "All three rings reach owner and nearby observer");
                for (var m : meteors(phase)) {
                    Vec3d target = (Vec3d) get(m, "target");
                    long ring = particles.stream().filter(v -> Math.abs(v.getY() - target.y - .12) < .001
                            && Math.abs(new Vec3d(v.getX(), target.y, v.getZ()).distanceTo(target) - 1.5) < .001).count();
                    c.assertEquals(12L, ring, "Particle packets fix the actual landing centre");
                }
                if (client == p) c.assertTrue(packets.stream().anyMatch(v -> v instanceof GameMessageS2CPacket message
                        && !message.overlay() && message.content().getContent() instanceof net.minecraft.text.TranslatableTextContent text
                        && text.getKey().equals("text.cursecycle.meteor_shower.warning")), "Real chat warning packet");
            }
            for (var m : meteors(phase)) {
                var target = (Vec3d) get(m, "target");
                c.assertTrue(Math.abs(target.y - (a.base.getY() + 8)) < .001, "Highest roof is marked");
                double radius = target.subtract(p.getPos()).horizontalLength();
                c.assertTrue(radius >= 4 && radius <= 12, "Candidate radius");
            }
            var pig = a.pig(a.target); var m = meteor(p, a.target, 0);
            impact(c, m); // An obstacle added above an already marked lower surface intercepts it.
            c.assertTrue(c.getWorld().getBlockState(a.base.up(7)).isOf(Blocks.OBSIDIAN), "Vanilla resistance");
            c.assertTrue(c.getWorld().getBlockState(a.base).isOf(Blocks.DIRT), "No through-roof crater");
            c.assertTrue(pig.getHealth() == 10, "Roof blocks damage below");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_blocks")
    public void normalConstructionAndWaterUseVanillaExplosionRules(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); p.setPosition(a.target.add(10, 0, 0)); a.activate(ID);
            var rule = c.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING); boolean old = rule.get();
            try {
                rule.set(false, a.server); a.block(a.base, Blocks.OAK_PLANKS);
                impact(c, meteor(p, a.target, 0));
                c.assertTrue(c.getWorld().getBlockState(a.base).isAir(), "Construction destroyed with mobGriefing false");
                a.block(a.base, Blocks.DIRT); a.block(a.base.up(), Blocks.WATER);
                impact(c, meteor(p, a.target.add(0, 1, 0), 0));
                c.assertTrue(c.getWorld().getBlockState(a.base).isOf(Blocks.DIRT), "Water resistance prevents crater");
            } finally { rule.set(old, a.server); }
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_budget")
    public void manyPlayersShareCapSearchAndIdempotentTick(TestContext c) {
        try (var a = new Arena(c)) {
            for (int i = 0; i < 7; i++) a.player(); a.activate("double_challenge:meteor_shower,meteor_shower");
            var phase = storm(a.server);
            for (var owner : sessions(phase).values()) set(owner, "next", 0L);
            step(a.server); int first = meteors(phase).size();
            c.assertTrue(first > 0 && first <= 3, "One shower search per tick for seven players");
            MeteorShowerChallenge.tick(a.server);
            c.assertEquals(first, meteors(phase).size(), "Duplicate tick does no work");
            for (int i = 0; i < 30; i++) {
                for (var owner : sessions(phase).values()) set(owner, "next", 0L);
                step(a.server); c.assertTrue(meteors(phase).size() <= 12, "Global queue cap");
            }
            c.assertTrue(MeteorShowerChallenge.SEARCH_ATTEMPTS == 24, "Bounded candidate budget");
            for (int i = 0; i < meteors(phase).size(); i++) for (int j = i + 1; j < meteors(phase).size(); j++)
                c.assertTrue(((Vec3d) get(meteors(phase).get(i), "target")).squaredDistanceTo(
                        (Vec3d) get(meteors(phase).get(j), "target")) >= 16, "Nearby owners do not duplicate a point");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_border")
    public void borderChangeAndUnloadedLocationsCancelWithoutChunkRequests(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); a.activate(ID); var border = c.getWorld().getWorldBorder();
            double x = border.getCenterX(), z = border.getCenterZ(), size = border.getSize();
            try {
                border.setCenter(a.target.x, a.target.z); border.setSize(64);
                c.assertTrue((Boolean) invoke("available", c.getWorld(), a.target.x, a.target.z), "Interior accepted");
                c.assertFalse((Boolean) invoke("available", c.getWorld(), a.target.x + 20, a.target.z), "Safety margin at border");
                var m = meteor(p, a.target, 0); border.setSize(20);
                c.assertTrue(advance(m, 75), "Shrinking border cancels a pending impact");
                c.assertTrue(c.getWorld().getBlockState(a.base).isOf(Blocks.DIRT), "Cancelled hit preserves blocks");
            } finally { border.setCenter(x, z); border.setSize(size); }
            int loaded = c.getWorld().getChunkManager().getLoadedChunkCount();
            for (int i = 0; i < 100; i++) c.assertFalse((Boolean) invoke("available", c.getWorld(), 1000000.5, 1000000.5), "Unloaded rejected");
            c.assertEquals(loaded, c.getWorld().getChunkManager().getLoadedChunkCount(), "No synchronous chunk load");
            p.setPosition(1000000.5, 100, 1000000.5);
            invoke("issue", storm(a.server), sessions(storm(a.server)).get(p.getUuid()), 0L);
            c.assertTrue(meteors(storm(a.server)).isEmpty(), "Search rejects unloaded targets");
            c.assertEquals(loaded, c.getWorld().getChunkManager().getLoadedChunkCount(), "Search never creates a chunk");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_cleanup")
    public void stopDisableResetActivationAndStateReplacementCancelQueues(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); var challenge = ChallengeRegistry.get(ID);
            for (int mode = 0; mode < 5; mode++) {
                a.activate(ID); var phase = storm(a.server);
                meteors(phase).add(meteor(p, a.target, age(a.server)));
                if (mode == 0) a.s.activate("test_easy");
                if (mode == 1) challenge.setEnabled(false);
                if (mode == 2) CurseCycleState.getServerState(a.server).reset();
                if (mode == 3) a.s.activate(ID);
                if (mode == 4) {
                    var state = CurseCycleState.getServerState(a.server);
                    var nbt = state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
                    c.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY,
                            CurseCycleState.createFromNbt(nbt, c.getWorld().getRegistryManager()));
                }
                try {
                    step(a.server);
                    c.assertTrue(storm(a.server) == null || meteors(storm(a.server)).isEmpty(), "No old impact after lifecycle " + mode);
                    c.assertTrue(c.getWorld().getBlockState(a.base).isOf(Blocks.DIRT), "No deferred damage " + mode);
                } finally { challenge.setEnabled(true); }
            }
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_players")
    public void playerEligibilityPersonalLifecycleAndOwnerIsolation(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); var q = a.player(); a.activate(ID);
            var phase = storm(a.server); var m = meteor(p, a.target, 0); meteors(phase).add(m);
            var qm = meteor(q, a.target.add(5, 0, 0), 0); meteors(phase).add(qm);
            p.changeGameMode(GameMode.CREATIVE); step(a.server);
            c.assertFalse(meteors(phase).contains(m), "Creative loses own pending meteors");
            c.assertTrue(meteors(phase).contains(qm), "Other owner retained");
            q.changeGameMode(GameMode.SPECTATOR); step(a.server);
            c.assertTrue(storm(a.server) == null, "No owners in excluded modes");
            p.changeGameMode(GameMode.ADVENTURE); step(a.server);
            c.assertTrue(sessions(storm(a.server)).containsKey(p.getUuid()), "Adventure applies");
            phase = storm(a.server); meteors(phase).add(meteor(p, a.target, 0));
            p.setHealth(0); step(a.server); c.assertTrue(storm(a.server) == null, "Dead owner has no effects");
            p = a.s.respawn(p, false); p.setPosition(a.target); step(a.server);
            c.assertTrue(meteors(storm(a.server)).isEmpty(), "Respawn starts fresh");
            var old = sessions(storm(a.server)).get(p.getUuid()); var uuid = p.getUuid();
            a.server.getPlayerManager().remove(p); step(a.server);
            c.assertTrue(storm(a.server) == null, "Offline owner removed without waiting for onTick");
            p = a.s.player(uuid); p.setPosition(a.target); step(a.server);
            c.assertTrue(sessions(storm(a.server)).get(uuid) != old, "Rejoin new session");
            a.s.activate("test_easy");
            CurseCycleState.getServerState(a.server).getPersonalActiveChallenges().put(p.getUuid(), new ArrayList<>(List.of(ID, ID)));
            step(a.server); c.assertEquals(1, sessions(storm(a.server)).size(), "PERSONAL only eligible owner");
            var nether = a.server.getWorld(World.NETHER);
            p.teleport(nether, 0, 100, 0, 0, 0); step(a.server);
            c.assertTrue(meteors(storm(a.server)).isEmpty(), "Dimension transfer drops former points");
            c.assertTrue(get(sessions(storm(a.server)).get(uuid), "world") == nether, "Fresh destination session");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_callback")
    public void lethalImpactCanRemoveItsOwnerDuringQueueIteration(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); a.activate("double_challenge:meteor_shower,pacifist"); p.setHealth(1);
            var phase = storm(a.server);
            var m = meteor(p, a.target, age(a.server) - 60); set(m, "position", a.target.add(0, 1, 0));
            meteors(phase).add(m); meteors(phase).add(meteor(p, a.target.add(5, 0, 0), age(a.server)));
            step(a.server);
            c.assertTrue(p.getHealth() <= 0, "Real explosion kills owner without reflected recursion");
            c.assertTrue(storm(a.server) == null, "Death callback safely clears iterated queue");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_missing_surface")
    public void removedSurfaceCancelsInsteadOfStrikingAnUnmarkedFloor(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); a.activate(ID);
            var m = meteor(p, a.target.add(0, 1, 0), 0);
            impact(c, m);
            c.assertTrue(c.getWorld().getBlockState(a.base).isOf(Blocks.DIRT), "Lower unmarked floor is intact");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_real_clock", tickLimit = 1010)
    public void actualServerClockSchedulesWarningFlightImpactAndNextInterval(TestContext c) {
        var a = new Arena(c); var p = a.player(); a.activate(ID);
        var phase = storm(a.server); var owner = sessions(phase).get(p.getUuid());
        long scheduled = (Long) get(owner, "next");
        c.assertTrue(scheduled >= 600 && scheduled <= 900, "30–45 second first interval");
        c.runAtTick(580, () -> {
            c.getWorld().setTimeOfDay(c.getWorld().getTimeOfDay() + 24000);
            c.assertTrue(meteors(phase).isEmpty(), "No early shower despite time jump");
        });
        // The first issue is on the real tick with age == scheduled. Inspect one tick later.
        c.runAtTick((int) scheduled + 3, () -> {
            try {
                c.assertEquals(3, meteors(phase).size(), "Real registered server event issued three points");
                var m = meteors(phase).getFirst();
                c.assertEquals(scheduled, (Long) get(m, "issued"), "No duplicate issue or clock drift");
                long next = (Long) get(owner, "next");
                c.assertTrue(next - scheduled >= 600 && next - scheduled <= 900, "Next interval starts at warning");
                Vec3d target = (Vec3d) get(m, "target"); var block = BlockPos.ofFloored(target.add(0, -.01, 0));
                p.setPosition(a.target.add(0, 40, 0)); var pig = a.pig(target.add(.7, 0, 0));
                c.runAtTick((int) scheduled + 55, () -> {
                    c.assertTrue(pig.getHealth() == 10 && c.getWorld().getBlockState(block).isOf(Blocks.DIRT), "Actual warning harmless");
                });
                c.runAtTick((int) scheduled + 85, () -> {
                    try {
                        c.assertTrue(pig.getHealth() < 10, "Real timed impact hurts target");
                        c.assertTrue(c.getWorld().getBlockState(block).isAir(), "Real timed crater");
                        c.assertTrue(meteors(phase).isEmpty(), "All three flights finish and retire"); c.complete();
                    } finally { a.close(); }
                });
            } catch (Throwable e) { a.close(); throw e; }
        });
    }
}
