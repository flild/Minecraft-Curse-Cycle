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
        for (int i = 0; i < 55; i++) if (advance(m, 60L + i) || (Long) get(m, "impactedAt") >= 0) return;
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
            advance(m, 75); c.assertEquals(75L, (Long) get(m, "impactedAt"), "Contact exactly on step 16 at 1.5 blocks/tick");
            c.assertTrue(pig.getHealth() < 10, "Real explosion damages mob");
            c.assertTrue(c.getWorld().getBlockState(a.base).isAir(), "Real explosion removes dirt");
            a.s.activate("test_easy");
            c.assertTrue(c.getWorld().getBlockState(a.base).isAir(), "End does not repair terrain");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_roof")
    public void caveFloorIsMarkedAndNewRoofInterceptsMeteor(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); var observer = a.player(); observer.changeGameMode(GameMode.CREATIVE); a.activate(ID);
            for (int x = -14; x <= 14; x++) for (int z = -14; z <= 14; z++) a.block(a.base.add(x, 7, z), Blocks.OBSIDIAN);
            var phase = storm(a.server); var owner = sessions(phase).get(p.getUuid());
            for (var client : List.of(p, observer)) {
                a.s.channel(client).runPendingTasks(); a.s.channel(client).flush();
                while (a.s.channel(client).readOutbound() != null) { }
            }
            invoke("issue", phase, owner, 0L);
            int count = meteors(phase).size();
            c.assertTrue(count >= 5 && count <= 10, "Complete 5–10 wave selected inside cave");
            for (var client : List.of(p, observer)) {
                a.s.channel(client).runPendingTasks(); a.s.channel(client).flush();
                var packets = new ArrayList<Object>(); Object packet;
                while ((packet = a.s.channel(client).readOutbound()) != null) packets.add(packet);
                var particles = packets.stream().filter(ParticleS2CPacket.class::isInstance)
                        .map(ParticleS2CPacket.class::cast).filter(v -> v.getParameters().getType() == ParticleTypes.FLAME).toList();
                c.assertEquals(count * 12, particles.size(), "Every warning reaches owner and nearby observer");
                for (var m : meteors(phase)) {
                    Vec3d target = (Vec3d) get(m, "target");
                    long ring = particles.stream().filter(v -> Math.abs(v.getY() - target.y - .12) < .001
                            && Math.abs(new Vec3d(v.getX(), target.y, v.getZ()).distanceTo(target) - 1.8) < .001).count();
                    c.assertEquals(12L, ring, "Particle packets fix the actual landing centre");
                }
                c.assertTrue(packets.stream().noneMatch(v -> v instanceof GameMessageS2CPacket message
                        && !message.overlay() && message.content().getContent() instanceof net.minecraft.text.TranslatableTextContent text
                        && text.getKey().equals("text.cursecycle.meteor_shower.warning")), "No warning chat spam for owner or observer");
            }
            for (var m : meteors(phase)) {
                var target = (Vec3d) get(m, "target");
                c.assertTrue(Math.abs(target.y - (a.base.getY() + 1)) < .001, "Local cave floor is marked, not roof");
                double radius = target.subtract(p.getPos()).horizontalLength();
                c.assertTrue(radius <= 12, "Candidate radius");
                c.assertTrue((Boolean) get(m, "geyser"), "All underground targets erupt");
                c.assertTrue((Double) get(m, "height") < 7, "Plume clipped to cave ceiling");
            }
            var pig = a.pig(a.target); var m = meteor(p, a.target, 0);
            set(m, "geyser", false); set(m, "position", a.target.add(0, 24, 0));
            impact(c, m); // A new roof intercepts a meteor aimed at a previously exposed floor.
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
            c.assertTrue(first >= 5 && first <= 10, "One shower search per tick for seven players");
            MeteorShowerChallenge.tick(a.server);
            c.assertEquals(first, meteors(phase).size(), "Duplicate tick does no work");
            for (int i = 0; i < 30; i++) {
                for (var owner : sessions(phase).values()) set(owner, "next", 0L);
                step(a.server); c.assertTrue(meteors(phase).size() <= 40, "Global queue cap");
            }
            c.assertTrue(MeteorShowerChallenge.SEARCH_ATTEMPTS == 64, "Bounded candidate budget");
            for (int i = 0; i < meteors(phase).size(); i++) for (int j = i + 1; j < meteors(phase).size(); j++)
                c.assertTrue(((Vec3d) get(meteors(phase).get(i), "target")).squaredDistanceTo(
                        (Vec3d) get(meteors(phase).get(j), "target")) >= MeteorShowerChallenge.MIN_SPACING * MeteorShowerChallenge.MIN_SPACING, "Nearby owners do not duplicate a point");
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
        c.assertTrue(scheduled == 80, "Four second initial preparation before the first warning");
        c.runAtTick(60, () -> {
            c.getWorld().setTimeOfDay(c.getWorld().getTimeOfDay() + 24000);
            c.assertTrue(meteors(phase).isEmpty(), "No early shower despite time jump");
        });
        // The first issue is on the real tick with age == scheduled. Inspect one tick later.
        c.runAtTick((int) scheduled + 3, () -> {
            try {
                c.assertTrue(meteors(phase).size() >= 5 && meteors(phase).size() <= 10, "Registered server event issued full wave");
                var m = meteors(phase).stream().filter(v -> !(Boolean) get(v, "geyser")).findFirst().orElseThrow();
                c.assertTrue((Long) get(m, "issued") >= scheduled && (Long) get(m, "issued") <= scheduled + 27, "Bounded warning staggering");
                long next = (Long) get(owner, "next");
                c.assertTrue(next - scheduled >= 240 && next - scheduled <= 360, "Next interval starts at warning");
                Vec3d target = (Vec3d) get(m, "target"); var block = BlockPos.ofFloored(target.add(0, -.01, 0));
                p.setPosition(a.target.add(0, 40, 0)); var pig = a.pig(target.add(.7, 0, 0));
                c.runAtTick((int) scheduled + 55, () -> {
                    c.assertTrue(pig.getHealth() == 10 && c.getWorld().getBlockState(block).isOf(Blocks.DIRT), "Actual warning harmless");
                });
                c.runAtTick((int) scheduled + 130, () -> {
                    try {
                        c.assertTrue(pig.getHealth() < 10, "Real timed impact hurts target");
                        c.assertTrue(c.getWorld().getBlockState(block).isAir(), "Real timed crater");
                        c.assertTrue(meteors(phase).isEmpty(), "All meteors and geysers retire"); c.complete();
                    } finally { a.close(); }
                });
            } catch (Throwable e) { a.close(); throw e; }
        });
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_geyser")
    public void geyserWarningBlastColumnDamageLiftAndRetirement(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); p.setPosition(a.target.add(12, 0, 0)); a.activate(ID);
            // Explosion-resistant floor and low roof isolate the sustained column from crater edits.
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) a.block(a.base.add(x, 0, z), Blocks.OBSIDIAN);
            a.block(a.base.up(5), Blocks.OBSIDIAN);
            var m = meteor(p, a.target, 0); set(m, "geyser", true);
            for (long t = 0; t < 60; t++) c.assertFalse(advance(m, t), "All warning ticks harmless");
            c.assertTrue((Long) get(m, "impactedAt") == -1, "No premature eruption");
            advance(m, 60); c.assertTrue((Long) get(m, "impactedAt") == 60, "Erupts from floor at warning end");
            var pig = a.pig(a.target.add(.5, 1, 0));
            var outside = a.pig(a.target.add(2.2, 1, 0));
            var above = a.pig(a.target.add(.5, 5, 0));
            advance(m, 70);
            c.assertTrue(pig.getHealth() < 10 && pig.isOnFire(), "Sustained column deals actual fire damage");
            c.assertTrue(pig.getVelocity().y >= .65 && pig.velocityModified, "Jet lifts and requests velocity packet");
            c.assertTrue(outside.getHealth() == 10 && above.getHealth() == 10, "Cylinder radius and solid ceiling bound damage");
            c.assertTrue((Double) get(m, "height") < 4.1, "Column stops under roof");
            c.assertTrue(advance(m, 90), "Bounded eruption retires at 30 ticks");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_geyser_cover")
    public void geyserRespectsWallsFireResistanceAndMissingFloor(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); p.setPosition(a.target.add(12, 0, 0)); a.activate(ID);
            var m = meteor(p, a.target, 0); set(m, "geyser", true); advance(m, 60);
            var resistant = a.pig(a.target.add(0, 1, .7));
            resistant.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE, 200));
            var sheltered = a.pig(a.target.add(1.5, 1, 0));
            a.block(a.base.add(1, 1, 0), Blocks.OBSIDIAN); a.block(a.base.add(1, 2, 0), Blocks.OBSIDIAN);
            advance(m, 70);
            c.assertTrue(resistant.getHealth() == 10 && sheltered.getHealth() == 10, "Resistance and a new barrier stop column damage");
            var missing = meteor(p, a.target.add(0, 3, 0), 0); set(missing, "geyser", true);
            c.assertTrue(advance(missing, 60), "Removed support cancels vent without lower-floor explosion");
            c.assertTrue((Long) get(missing, "impactedAt") == -1, "No unannounced new target");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_wave_atomic")
    public void wavesHaveFiveToTenWithBoundedContinuationInSmallSpaces(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); a.activate(ID); var phase = storm(a.server); var owner = sessions(phase).get(p.getUuid());
            for (int wave = 0; wave < 12; wave++) {
                meteors(phase).clear(); invoke("issue", phase, owner, 0L);
                c.assertTrue(meteors(phase).size() >= 5 && meteors(phase).size() <= 10, "Full wave count");
                c.assertTrue(meteors(phase).stream().anyMatch(m -> (Boolean) get(m, "geyser")), "Open ground includes geysers");
                c.assertTrue(meteors(phase).stream().anyMatch(m -> !(Boolean) get(m, "geyser")), "Open ground includes meteors");
            }
            meteors(phase).clear();
            for (int i = 0; i < 32; i++) meteors(phase).add(meteor(p, a.target.add(i * 4, 0, 0), 0));
            invoke("issue", phase, owner, 0L);
            c.assertEquals(32, meteors(phase).size(), "Capacity pressure defers whole wave");
            meteors(phase).clear();
            // A small island still starts its wave, carrying the remainder to later warned portions.
            for (int x = -14; x <= 14; x++) for (int z = -14; z <= 14; z++) if (x != 0 || z != 0) a.block(a.base.add(x, 0, z), Blocks.AIR);
            invoke("issue", phase, owner, 0L);
            c.assertEquals(1, meteors(phase).size(), "One accessible floor starts the first portion");
            int total = 1 + (Integer) get(owner, "remaining");
            c.assertTrue(total >= 5 && total <= 10, "Remainder is part of the same bounded wave");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_column_callback")
    public void lethalColumnCancelsRemainingDirectHits(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); p.setPosition(a.target.add(12, 0, 0)); a.activate(ID);
            var m = meteor(p, a.target, 0); set(m, "geyser", true); advance(m, 60);
            p.setPosition(a.target.add(0, 1, 0)); p.setHealth(1); p.timeUntilRegen = 0;
            var pig = a.pig(a.target.add(.5, 1, 0));
            advance(m, 70);
            c.assertTrue(p.getHealth() <= 0, "Column can kill owner");
            c.assertTrue(storm(a.server) == null, "Synchronous death cancels session");
            c.assertTrue(pig.getHealth() == 10, "No direct column hits after owner deactivation");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_column_cap")
    public void denseColumnHasBoundedEntityCollection(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); p.setPosition(a.target.add(12, 0, 0)); a.activate(ID);
            var m = meteor(p, a.target, 0); set(m, "geyser", true); advance(m, 60);
            var pigs = new ArrayList<PigEntity>();
            for (int i = 0; i < 40; i++) pigs.add(a.pig(a.target.add(.25, 1, .25)));
            advance(m, 70);
            c.assertEquals(32L, pigs.stream().filter(pig -> pig.getHealth() < 10).count(), "Exactly 32 direct targets in a dense jet");
        } c.complete();
    }

    private static void tunnel(Arena a) {
        for (int x = -13; x <= 13; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y <= 3; y++)
            a.block(a.base.add(x, y, z), z == 0 && (y == 1 || y == 2) ? Blocks.AIR : Blocks.OBSIDIAN);
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_narrow_tunnel")
    public void oneBlockWideTwoBlockHighTunnelFindsMultipleLocalVentsWithoutRandomLuck(TestContext c) {
        try (var a = new Arena(c)) {
            tunnel(a); var p = a.player(); a.activate(ID);
            var phase = storm(a.server); var owner = sessions(phase).get(p.getUuid());
            for (int seed = 0; seed < 16; seed++) {
                p.getRandom().setSeed(seed); meteors(phase).clear(); set(owner, "remaining", 0); invoke("issue", phase, owner, 0L);
                c.assertTrue(meteors(phase).size() >= 5, "Narrow tunnel must not depend on random angles, seed " + seed);
                for (var m : meteors(phase)) {
                    var target = (Vec3d) get(m, "target");
                    c.assertTrue((Boolean) get(m, "geyser") && (Double) get(m, "height") < 2, "Low cave produces ceiling-limited jets");
                    c.assertTrue(Math.abs(target.y - a.target.y) < .001 && MathHelper.floor(target.z) == a.base.getZ(), "Tunnel floor, not roof or other cavity");
                }
            }
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_small_cell")
    public void singleFloorCellRunsWholeWaveInSeparatelyWarnedPortions(TestContext c) {
        try (var a = new Arena(c)) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 3; y++)
                a.block(a.base.add(x, y, z), x == 0 && z == 0 && (y == 1 || y == 2) ? Blocks.AIR : Blocks.OBSIDIAN);
            var p = a.player(); p.getAbilities().invulnerable = true; a.activate(ID);
            var phase = storm(a.server); var owner = sessions(phase).get(p.getUuid());
            invoke("issue", phase, owner, 0L);
            c.assertEquals(1, meteors(phase).size(), "A tiny room still warns and erupts instead of deferring forever");
            int wanted = meteors(phase).size() + (Integer) get(owner, "remaining");
            c.assertTrue(wanted >= 5 && wanted <= 10, "Wave total remains 5–10");
            int emitted = 0; long now = 0;
            while (true) {
                emitted += meteors(phase).size();
                for (var m : List.copyOf(meteors(phase))) {
                    long issued = (Long) get(m, "issued");
                    for (long t = issued; t < issued + 60; t++) {
                        advance(m, t); c.assertTrue((Long) get(m, "impactedAt") == -1, "Fresh 60-tick warning for every portion");
                    }
                    advance(m, issued + 60);
                    c.assertTrue((Long) get(m, "impactedAt") == issued + 60, "Each warned portion really erupts");
                    c.assertTrue(advance(m, issued + 90), "Portion retires before reusing its floor");
                }
                meteors(phase).clear();
                if ((Integer) get(owner, "remaining") == 0) break;
                now = (Long) get(owner, "next"); invoke("issue", phase, owner, now);
                c.assertEquals(1, meteors(phase).size(), "Continue at the available local floor");
                c.assertTrue(emitted < 10, "No unbounded wave backlog");
            }
            c.assertEquals(wanted, emitted, "All five to ten strikes are delivered without tight-space immunity");
            c.assertTrue((Long) get(owner, "next") - now >= 240, "Normal cooldown follows final portion");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_step_floor")
    public void oneBlockStepFindsRaisedLocalFloorAndStillRejectsSolidWall(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); a.activate(ID); var owner = sessions(storm(a.server)).get(p.getUuid());
            for (int x = 1; x <= 5; x++) for (int z = -1; z <= 1; z++) a.block(a.base.add(x, 1, z), Blocks.STONE);
            var floor = (Vec3d) invoke("floor", owner, a.target.x + 4, a.target.z);
            c.assertTrue(floor != null && Math.abs(floor.y - a.target.y - 1) < .001, "Raised floor is reachable by a one-block jump");
            for (int y = 2; y <= 4; y++) a.block(a.base.add(2, y, 0), Blocks.OBSIDIAN);
            c.assertTrue(invoke("floor", owner, a.target.x + 4, a.target.z) == null, "Full wall still prevents targets in a hidden room");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "meteor_small_cell_clock", tickLimit = 1300)
    public void actualServerClockDeliversAllCrampedRoomPortionsAndStopCancelsRemainder(TestContext c) {
        var a = new Arena(c);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 3; y++)
            a.block(a.base.add(x, y, z), x == 0 && z == 0 && (y == 1 || y == 2) ? Blocks.AIR : Blocks.OBSIDIAN);
        var p = a.player(); p.getAbilities().invulnerable = true; a.activate(ID);
        var phase = storm(a.server); var owner = sessions(phase).get(p.getUuid());
        var issuedIds = new HashSet<Integer>(); var impactedIds = new HashSet<Integer>(); int[] wanted = {-1};
        c.runAtEveryTick(() -> {
            try {
                for (var m : meteors(phase)) {
                    int id = (Integer) get(m, "id");
                    if (issuedIds.add(id)) {
                        c.assertTrue(age(a.server) - (Long) get(m, "issued") < 60 && (Long) get(m, "impactedAt") == -1,
                                "Registered clock gives each new portion a fresh warning");
                        if (wanted[0] < 0) wanted[0] = 1 + (Integer) get(owner, "remaining");
                    }
                    if ((Long) get(m, "impactedAt") >= 0) impactedIds.add(id);
                }
                if (wanted[0] >= 5 && impactedIds.size() == wanted[0]) {
                    c.assertEquals(wanted[0], issuedIds.size(), "Exactly the selected 5–10 strikes, no new wave debt");
                    c.assertEquals(0, (Integer) get(owner, "remaining"), "All portions delivered on real clock");
                    // Start a new cramped wave and stop while it still has outstanding portions.
                    meteors(phase).clear(); invoke("issue", phase, owner, age(a.server));
                    c.assertTrue((Integer) get(owner, "remaining") > 0, "Pending continuation exists");
                    a.s.activate("test_easy");
                    c.assertTrue(storm(a.server) == null, "Stop discards all pending portions");
                    a.close(); c.complete();
                }
            } catch (Throwable failure) { a.close(); throw failure; }
        });
    }

}
