package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.BlackHoleChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

import java.lang.reflect.*;
import java.util.*;

public class BlackHoleGameTest implements FabricGameTest {
    private static final String ID = BlackHoleChallenge.ID;
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
            var m = Arrays.stream(BlackHoleChallenge.class.getDeclaredMethods()).filter(v -> v.getName().equals(name)).findFirst().orElseThrow();
            m.setAccessible(true); return m.invoke(null, args);
        } catch (InvocationTargetException e) { throw new AssertionError(e.getCause()); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Object field(MinecraftServer server) {
        try { var f = BlackHoleChallenge.class.getDeclaredField("FIELDS"); f.setAccessible(true); return ((Map<?, ?>) f.get(null)).get(server); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @SuppressWarnings("unchecked") private static List<Object> holes(Object field) { return (List<Object>) get(field, "holes"); }
    @SuppressWarnings("unchecked") private static Map<UUID, Object> sessions(Object field) { return (Map<UUID, Object>) get(field, "sessions"); }
    private static void step(MinecraftServer server) {
        if (field(server) != null) set(field(server), "lastTick", server.getTicks() - 1);
        BlackHoleChallenge.tick(server);
    }
    private static Object hole(ServerPlayerEntity p, Vec3d center, long issued) {
        try {
            var ctor = Class.forName(BlackHoleChallenge.class.getName() + "$Hole").getDeclaredConstructors()[0]; ctor.setAccessible(true);
            var h = ctor.newInstance(sessions(field(p.getServer())).get(p.getUuid()), center, issued);
            holes(field(p.getServer())).add(h); return h;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void affect(MinecraftServer server, long now) { invoke("affect", server, field(server), now); }
    private static Vec3d center(Entity e) { return e.getBoundingBox().getCenter(); }
    private static List<Object> packets(ChallengeScenario s, ServerPlayerEntity p) {
        var ch = s.channel(p); ch.runPendingTasks(); ch.flush(); var result = new ArrayList<Object>(); Object packet;
        while ((packet = ch.readOutbound()) != null) result.add(packet); return result;
    }
    private static final class Arena implements AutoCloseable {
        final TestContext c;
        final ChallengeScenario s;
        final MinecraftServer server;
        final Vec3d target;
        final List<Entity> entities = new ArrayList<>();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final List<ChunkPos> forced = new ArrayList<>();
        boolean closed;
        Arena(TestContext c) {
            this.c = c; s = new ChallengeScenario(c); server = c.getWorld().getServer();
            target = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(4, 50, 4)));
            for (int cx = MathHelper.floor(target.x - 32) >> 4; cx <= MathHelper.floor(target.x + 32) >> 4; cx++)
                for (int cz = MathHelper.floor(target.z - 32) >> 4; cz <= MathHelper.floor(target.z + 32) >> 4; cz++) c.getWorld().getChunk(cx, cz);
        }
        ServerPlayerEntity player() {
            var p = s.player(); for (int i = 0; i < 61; i++) p.tick();
            p.setHealth(20); p.timeUntilRegen = 0; p.setNoGravity(true); p.setPosition(target.add(14, 0, 0)); p.setVelocity(Vec3d.ZERO); return p;
        }
        PigEntity pig(Vec3d middle) {
            var p = EntityType.PIG.create(c.getWorld()); p.setAiDisabled(true); p.setNoGravity(true);
            p.setPosition(middle.add(0, -p.getHeight() / 2, 0)); c.assertTrue(c.getWorld().spawnEntity(p), "Pig spawned"); entities.add(p); return p;
        }
        ItemEntity item(Vec3d middle) {
            var e = new ItemEntity(c.getWorld(), middle.x, middle.y - .125, middle.z, new ItemStack(Items.DIAMOND, 3));
            e.setNoGravity(true); e.setVelocity(Vec3d.ZERO); e.setPickupDelayInfinite(); c.getWorld().spawnEntity(e); entities.add(e); return e;
        }
        void block(BlockPos p, Block b) { blocks.putIfAbsent(p, c.getWorld().getBlockState(p)); c.getWorld().setBlockState(p, b.getDefaultState(), Block.NOTIFY_LISTENERS); }
        void forceTickingArea(Vec3d at) {
            // Fixture only: loading a chunk does not make ItemEntity.tick run in it.
            for (int x = MathHelper.floor(at.x - 10) >> 4; x <= MathHelper.floor(at.x + 10) >> 4; x++)
                for (int z = MathHelper.floor(at.z - 10) >> 4; z <= MathHelper.floor(at.z + 10) >> 4; z++) {
                    var pos = new ChunkPos(x, z);
                    if (!c.getWorld().getForcedChunks().contains(pos.toLong())) {
                        c.getWorld().setChunkForced(x, z, true); forced.add(pos);
                    }
                }
        }
        void activate(String id) { s.activate(id); step(server); }
        @Override public void close() {
            if (closed) return; closed = true;
            BlackHoleChallenge.clear(server); entities.forEach(Entity::discard);
            forced.forEach(pos -> c.getWorld().setChunkForced(pos.x, pos.z, false));
            blocks.forEach((p, b) -> c.getWorld().setBlockState(p, b, Block.NOTIFY_LISTENERS)); s.close();
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_warning")
    public void actualSelectionWarnsOwnerAndObserverBeforeAnyEffect(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); p.setPosition(a.target); var q = a.player(); q.changeGameMode(GameMode.CREATIVE);
            a.activate(ID); packets(a.s, p); packets(a.s, q);
            var f = field(a.server); var session = sessions(f).get(p.getUuid()); invoke("issue", f, session, 0L);
            c.assertEquals(1, holes(f).size(), "One announced centre"); var h = holes(f).getFirst(); var at = (Vec3d) get(h, "center");
            double radius = at.subtract(p.getPos()).horizontalLength(); c.assertTrue(radius >= 6 && radius <= 10, "Nearby 6–10 blocks");
            c.assertTrue(Math.abs(at.y - p.getY() - 1) < .001, "At owner elevation, usable underground");
            for (var player : List.of(p, q)) {
                var received = packets(a.s, player);
                c.assertTrue(received.stream().anyMatch(v -> v instanceof GameMessageS2CPacket m
                        && m.content().getContent() instanceof net.minecraft.text.TranslatableTextContent t
                        && t.getKey().equals("text.cursecycle.black_hole.warning")), "Nearby observer receives chat warning");
                var ring = received.stream().filter(ParticleS2CPacket.class::isInstance).map(ParticleS2CPacket.class::cast)
                        .filter(v -> v.getParameters().getType() == ParticleTypes.SMOKE).toList();
                c.assertEquals(12, ring.size(), "Full marker ring sent");
                for (var v : ring) c.assertTrue(Math.abs(new Vec3d(v.getX(), v.getY(), v.getZ()).distanceTo(at) - 1) < .001, "Exact announced core");
            }
            var pig = a.pig(at); var item = a.item(at.add(2, 0, 0));
            for (long t = 0; t < 60; t++) affect(a.server, t);
            c.assertTrue(pig.getHealth() == 10 && pig.getVelocity().equals(Vec3d.ZERO), "All 60 warning ticks harmless");
            c.assertTrue(item.getVelocity().equals(Vec3d.ZERO), "No early item pull");
            affect(a.server, 60); c.assertTrue(pig.getHealth() == 8, "First active core damage");
            c.assertTrue(item.getVelocity().x < 0 || item.getVelocity().z != 0, "Pull starts after warning");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_pull")
    public void threeEntityTypesMoveInwardWithBoundedImpulseAndVanillaVelocityPackets(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); p.setPosition(a.target.add(3, -.9, 0)); a.activate(ID); hole(p, a.target, 0);
            var pig = a.pig(a.target.add(0, 0, 3)); var item = a.item(a.target.add(-3, 0, 0));
            affect(a.server, 62);
            for (var e : List.of(p, pig, item)) {
                var delta = a.target.subtract(center(e));
                c.assertTrue(e.getVelocity().dotProduct(delta) > 0, "Inward velocity for " + e.getType());
                c.assertTrue(Math.abs(e.getVelocity().length() - .08) < 1E-6, "Bounded impulse");
                var observers = new ArrayList<Packet<?>>();
                new EntityTrackerEntry(c.getWorld(), e, 1, true, observers::add).tick();
                c.assertTrue(observers.stream().anyMatch(v -> v instanceof EntityVelocityUpdateS2CPacket velocity
                        && velocity.getEntityId() == e.getId()), "Vanilla tracker sends actual velocity to observers");
                double before = delta.length(); e.move(MovementType.SELF, e.getVelocity());
                c.assertTrue(center(e).distanceTo(a.target) < before, "Real collision movement approaches core");
            }
            c.assertTrue(packets(a.s, p).stream().anyMatch(v -> v instanceof EntityVelocityUpdateS2CPacket velocity
                    && velocity.getEntityId() == p.getId() && velocity.getVelocityX() < 0), "Affected player's own vanilla packet");
            for (int i = 0; i < 100; i++) affect(a.server, 62);
            for (var e : List.of(p, pig, item)) c.assertTrue(e.getVelocity().length() <= .600001, "Repeated pull capped");
            p.setVelocity(-2, .25, 0); var old = p.getVelocity(); affect(a.server, 62);
            c.assertTrue(p.getVelocity().equals(old), "Faster existing inward/external motion preserved");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_core")
    public void sphericalBoundaryCoreCadenceAndItemsBlocksArePreserved(TestContext c) {
        try (var a = new Arena(c)) {
            var owner = a.player(); a.activate(ID); hole(owner, a.target, 0);
            var core = a.pig(a.target.add(.5, 0, 0)); var outer = a.pig(a.target.add(1.01, 0, 0));
            var boundary = a.pig(a.target.add(8, 0, 0)); var outside = a.pig(a.target.add(8.01, 0, 0));
            var diagonal = a.pig(a.target.add(7, 0, 7)); var item = a.item(a.target);
            var block = BlockPos.ofFloored(a.target).down(2); a.block(block, Blocks.DIAMOND_BLOCK);
            affect(a.server, 60);
            c.assertTrue(core.getHealth() == 8 && outer.getHealth() == 10, "Damage only in one-block core");
            c.assertTrue(boundary.getVelocity().x < 0 && outside.getVelocity().equals(Vec3d.ZERO), "Inclusive spherical radius 8");
            c.assertTrue(diagonal.getVelocity().equals(Vec3d.ZERO), "AABB corner excluded by sphere");
            for (long t = 62; t < 80; t += 2) { core.timeUntilRegen = 0; affect(a.server, t); }
            c.assertTrue(core.getHealth() == 8, "Separate one-second damage cadence");
            core.timeUntilRegen = 0; affect(a.server, 80); c.assertTrue(core.getHealth() == 6, "Second actual magic hit");
            for (long t = 82; t < 220; t += 2) affect(a.server, t);
            c.assertFalse(item.isRemoved(), "Items never deleted/damaged by core"); c.assertEquals(3, item.getStack().getCount(), "Whole stack retained");
            c.assertTrue(c.getWorld().getBlockState(block).isOf(Blocks.DIAMOND_BLOCK), "Blocks untouched");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_overlap")
    public void overlappingOwnersUseOneNearestPullAndBoundedEntityQuery(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); var q = a.player(); a.activate(ID);
            hole(p, a.target, 0); hole(q, a.target.add(4, 0, 0), 1); // odd issue tick also damages on global second
            var pig = a.pig(a.target.add(3.5, 0, 0)); affect(a.server, 80);
            c.assertTrue(pig.getVelocity().x > 0 && Math.abs(pig.getVelocity().length() - .08) < 1E-6, "Nearest hole, one impulse");
            c.assertTrue(pig.getHealth() == 8, "Odd-tick issue has working core cadence");
            pig.discard(); holes(field(a.server)).clear(); hole(p, a.target, 0);
            var crowd = new ArrayList<PigEntity>(); for (int i = 0; i < 200; i++) crowd.add(a.pig(a.target.add(3, 0, 0)));
            affect(a.server, 62); long moved = crowd.stream().filter(v -> !v.getVelocity().equals(Vec3d.ZERO)).count();
            c.assertEquals(128L, moved, "Query aborts at 128 candidates, not an unbounded returned list");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_budget")
    public void ownersShareSearchQueueCapAndDuplicateTicks(TestContext c) {
        try (var a = new Arena(c)) {
            for (int i = 0; i < 20; i++) a.player(); a.activate("double_challenge:black_hole,black_hole");
            var f = field(a.server);
            sessions(f).values().forEach(v -> set(v, "next", 0L));
            for (int i = 0; i < 20; i++) {
                int before = holes(f).size(); step(a.server);
                c.assertTrue(holes(f).size() - before <= 1 && holes(f).size() <= 4, "One bounded search for all owners, cap four");
                int after = holes(f).size(); BlackHoleChallenge.tick(a.server); c.assertEquals(after, holes(f).size(), "Same server tick is idempotent");
            }
            c.assertEquals(4, holes(f).size(), "Actual queue saturation");
            c.assertEquals(20, sessions(f).size(), "Duplicate active IDs do not duplicate owners");
            c.assertTrue(BlackHoleChallenge.SEARCH_ATTEMPTS == 16, "Bounded candidates");
            for (var session : sessions(f).values()) c.assertTrue((Long) get(session, "next") >= 800, "No saturation debt");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_loaded")
    public void unloadedAndBorderCancelWithoutAcquiringChunks(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); a.activate(ID); var border = c.getWorld().getWorldBorder();
            double x = border.getCenterX(), z = border.getCenterZ(), size = border.getSize();
            try {
                border.setCenter(a.target.x, a.target.z); border.setSize(64);
                c.assertTrue((Boolean) invoke("available", c.getWorld(), a.target), "Loaded interior accepted");
                hole(p, a.target, -60); border.setSize(16); step(a.server);
                c.assertTrue(holes(field(a.server)).isEmpty(), "Shrinking border cancels hole");
            } finally { border.setCenter(x, z); border.setSize(size); }
            int loaded = c.getWorld().getChunkManager().getLoadedChunkCount();
            var unavailable = new Vec3d(1000000, 100, 1000000);
            for (int i = 0; i < 100; i++) c.assertFalse((Boolean) invoke("available", c.getWorld(), unavailable), "Unloaded query rejected");
            p.setPosition(unavailable); invoke("issue", field(a.server), sessions(field(a.server)).get(p.getUuid()), 0L);
            c.assertTrue(holes(field(a.server)).isEmpty(), "No event at unloaded location");
            c.assertEquals(loaded, c.getWorld().getChunkManager().getLoadedChunkCount(), "No synchronous chunk loads/tickets");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_lifecycle")
    public void stopDisableResetSerialAndNbtReloadCancelAllNewEffects(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); var challenge = ChallengeRegistry.get(ID); var pig = a.pig(a.target.add(2, 0, 0));
            for (int mode = 0; mode < 5; mode++) {
                a.activate(ID); hole(p, a.target, -60); pig.setVelocity(Vec3d.ZERO);
                if (mode == 0) a.s.activate("test_easy");
                if (mode == 1) challenge.setEnabled(false);
                if (mode == 2) CurseCycleState.getServerState(a.server).reset();
                if (mode == 3) a.s.activate(ID);
                if (mode == 4) {
                    var state = CurseCycleState.getServerState(a.server); var nbt = state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
                    c.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY, CurseCycleState.createFromNbt(nbt, c.getWorld().getRegistryManager()));
                }
                try {
                    step(a.server); c.assertTrue(field(a.server) == null || holes(field(a.server)).isEmpty(), "Old hole discarded " + mode);
                    c.assertTrue(pig.getVelocity().equals(Vec3d.ZERO), "No deferred pull " + mode);
                } finally { challenge.setEnabled(true); }
            }
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_eligibility")
    public void personalModesDeathRespawnDisconnectAndWorldIdentity(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); var q = a.player(); a.activate(ID); var f = field(a.server);
            hole(p, a.target, -60); var other = hole(q, a.target.add(5, 0, 0), -60);
            p.changeGameMode(GameMode.CREATIVE); step(a.server);
            c.assertEquals(1, holes(f).size(), "Creative owner's hole removed"); c.assertTrue(holes(f).contains(other), "Other owner retained");
            p.setPosition(a.target.add(7, -.9, 0)); q.changeGameMode(GameMode.ADVENTURE); p.setVelocity(Vec3d.ZERO);
            affect(a.server, 0); c.assertTrue(p.getVelocity().equals(Vec3d.ZERO), "Creative immune to another owner's hole");
            q.changeGameMode(GameMode.SPECTATOR); step(a.server); c.assertTrue(field(a.server) == null, "Spectator excluded");
            q.changeGameMode(GameMode.SURVIVAL); step(a.server); q.setHealth(0); step(a.server);
            c.assertTrue(field(a.server) == null, "Death cancels owner"); q = a.s.respawn(q, false); q.setPosition(a.target.add(14, 0, 0)); step(a.server);
            c.assertTrue(holes(field(a.server)).isEmpty(), "Respawn has no old holes");
            var old = sessions(field(a.server)).get(q.getUuid()); var uuid = q.getUuid();
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(q.networkHandler, a.server); a.server.getPlayerManager().remove(q);
            c.assertTrue(field(a.server) == null, "Fabric disconnect cancels immediately");
            q = a.s.player(uuid); q.setPosition(a.target.add(14, 0, 0)); step(a.server);
            c.assertTrue(sessions(field(a.server)).get(uuid) != old, "Rejoin fresh identity");
            a.s.activate("test_easy"); var state = CurseCycleState.getServerState(a.server);
            state.getPersonalActiveChallenges().put(uuid, new ArrayList<>(List.of(ID, ID))); step(a.server);
            c.assertEquals(1, sessions(field(a.server)).size(), "PERSONAL deduplicated");
            p.changeGameMode(GameMode.SURVIVAL); p.setPosition(a.target.add(2, -.9, 0)); p.setVelocity(Vec3d.ZERO);
            hole(q, a.target, -60); affect(a.server, 0); c.assertTrue(p.getVelocity().equals(Vec3d.ZERO), "Non-participant PERSONAL player unaffected");
            var nether = a.server.getWorld(World.NETHER); q.teleport(nether, 0, 100, 0, 0, 0); step(a.server);
            c.assertTrue(holes(field(a.server)).isEmpty(), "World change drops former hole");
            c.assertTrue(get(sessions(field(a.server)).get(uuid), "world") == nether, "Destination session");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_callback")
    public void lethalCoreCallbackCanClearIteratedQueue(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); a.activate("double_challenge:black_hole,pacifist"); p.setHealth(1); p.setPosition(a.target.add(.5, -.9, 0));
            hole(p, a.target, -60); affect(a.server, 0);
            c.assertFalse(p.isAlive(), "Real lethal core hit"); c.assertTrue(field(a.server) == null, "Death callback cleared field safely");
        } c.complete();
    }

    private static void checked(Arena arena, Runnable action) {
        try { action.run(); } catch (Throwable e) { arena.close(); throw e; }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_real_clock", tickLimit = 1500)
    public void realServerClockWarningLifetimeAndStopRetainOnlyExistingMomentum(TestContext c) {
        var a = new Arena(c); var p = a.player(); p.setPosition(a.target); a.activate(ID);
        var f = field(a.server); var session = sessions(f).get(p.getUuid()); long scheduled = (Long) get(session, "next");
        c.assertTrue(scheduled >= 800 && scheduled <= 1200, "First interval 40–60 seconds");
        c.runAtTick(780, () -> checked(a, () -> { c.getWorld().setTimeOfDay(c.getWorld().getTimeOfDay() + 24000); c.assertTrue(holes(f).isEmpty(), "No early event from daylight change"); }));
        c.runAtTick((int) scheduled + 3, () -> {
            try {
                c.assertEquals(1, holes(f).size(), "Registered END_SERVER_TICK created event"); var h = holes(f).getFirst();
                c.assertEquals(scheduled, (Long) get(h, "issued"), "Actual scheduled tick");
                long next = (Long) get(session, "next"); c.assertTrue(next - scheduled >= 800 && next - scheduled <= 1200, "Next interval scheduled");
                var at = (Vec3d) get(h, "center"); a.forceTickingArea(at); var item = a.item(at.add(2, 0, 0));
                c.runAtTick((int) scheduled + 55, () -> checked(a, () -> {
                    c.assertTrue(c.getWorld().shouldTickEntity(item.getBlockPos()), "Fixture entity chunk actually ticks");
                    c.assertTrue(item.getVelocity().equals(Vec3d.ZERO), "Actual warning harmless");
                }));
                c.runAtTick((int) scheduled + 75, () -> checked(a, () ->
                        c.assertTrue(center(item).distanceTo(at) < 2, "Real server ticks pull item")));
                c.runAtTick((int) scheduled + 221, () -> {
                    try {
                        c.assertTrue(holes(f).isEmpty(), "Event expires after 60+160 ticks");
                        item.setVelocity(Vec3d.ZERO); step(a.server); c.assertTrue(item.getVelocity().equals(Vec3d.ZERO), "No impulse after expiry");
                        hole(p, at, (long) a.server.getTicks() - (Integer) get(f, "start") - 60);
                        invoke("pull", item, at); var momentum = item.getVelocity(); a.s.activate("test_easy");
                        c.assertTrue(item.getVelocity().equals(momentum), "Stop preserves existing momentum");
                        item.setVelocity(Vec3d.ZERO); step(a.server); c.assertTrue(item.getVelocity().equals(Vec3d.ZERO), "No new impulse after stop"); c.complete();
                    } finally { a.close(); }
                });
            } catch (Throwable e) { a.close(); throw e; }
        });
    }
}
