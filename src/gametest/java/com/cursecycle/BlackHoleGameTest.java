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
            var m = Arrays.stream(BlackHoleChallenge.class.getDeclaredMethods()).filter(v -> v.getName().equals(name) && v.getParameterCount() == args.length).findFirst().orElseThrow();
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
            var h = ctor.newInstance(holes(field(p.getServer())).size() + 1, sessions(field(p.getServer())).get(p.getUuid()), center, issued);
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
            for (int cx = MathHelper.floor(target.x - 64) >> 4; cx <= MathHelper.floor(target.x + 64) >> 4; cx++)
                for (int cz = MathHelper.floor(target.z - 64) >> 4; cz <= MathHelper.floor(target.z + 64) >> 4; cz++) c.getWorld().getChunk(cx, cz);
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

    private static void isolate(Arena a) { holes(field(a.server)).clear(); }
    private static void age(Arena a, int value) { set(field(a.server), "start", a.server.getTicks() - value); }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_warning")
    public void initialHunterWarnsImmediatelyAndAllSixtyWarningTicksAreHarmless(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); p.setPosition(a.target); var q = a.player(); q.changeGameMode(GameMode.CREATIVE);
            a.activate(ID);
            c.assertEquals(1, holes(field(a.server)).size(), "Hunter appears immediately");
            var h = holes(field(a.server)).getFirst(); var at = (Vec3d) get(h, "center");
            double distance = at.subtract(p.getPos()).horizontalLength();
            c.assertTrue(distance >= 28 && distance <= 32, "Spawn 28–32 blocks away");
            for (var player : List.of(p, q))
                c.assertTrue(packets(a.s, player).stream().anyMatch(v -> v instanceof GameMessageS2CPacket m
                        && m.content().getContent() instanceof net.minecraft.text.TranslatableTextContent t
                        && t.getKey().equals("text.cursecycle.black_hole.warning")), "Owner and observer warned");
            var pig = a.pig(at); var item = a.item(at.add(4, 0, 0));
            for (long t = 0; t < 60; t++) affect(a.server, t);
            c.assertTrue(pig.getHealth() == 10 && pig.getVelocity().equals(Vec3d.ZERO), "Entire warning harmless");
            c.assertTrue(item.getVelocity().equals(Vec3d.ZERO), "No early pull");
            affect(a.server, 60);
            c.assertTrue(pig.getHealth() < 10 && item.getVelocity().x < 0, "Core damage and pull start after warning");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_follow")
    public void persistentHunterFollowsAtBoundedSpeedAndDoesNotExpire(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); a.activate(ID); isolate(a);
            var h = hole(p, a.target, 0); age(a, 100);
            var before = (Vec3d) get(h, "center");
            var target = p.getPos().add(0, 2, 0); step(a.server);
            var after = (Vec3d) get(h, "center");
            c.assertTrue(Math.abs(after.distanceTo(before) - .085) < 1e-6, "Bounded continuous movement");
            c.assertTrue(after.distanceTo(target) < before.distanceTo(target), "Follows owner");
            age(a, 5000); step(a.server);
            c.assertTrue(holes(field(a.server)).contains(h), "No old eight-second expiry");
            c.assertEquals(1, holes(field(a.server)).size(), "No periodic extra holes");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_relocation")
    public void distantOwnerGetsOneRelocatedHunterWithFreshHarmlessWarning(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); a.activate(ID); isolate(a); var h = hole(p, a.target, 0);
            p.setPosition(a.target.add(80, 0, 0));
            for (int cx = ((int) p.getX() - 64) >> 4; cx <= ((int) p.getX() + 64) >> 4; cx++)
                for (int cz = ((int) p.getZ() - 64) >> 4; cz <= ((int) p.getZ() + 64) >> 4; cz++) c.getWorld().getChunk(cx, cz);
            age(a, 100); step(a.server);
            var at = (Vec3d) get(h, "center");
            c.assertTrue(at.distanceTo(a.target) > 40, "Old position replaced");
            c.assertTrue(at.distanceTo(p.getPos()) >= 28 && at.distanceTo(p.getPos()) < 33, "Safe relocation distance");
            c.assertEquals(100L, (Long) get(h, "issued"), "Warning restarted");
            c.assertEquals(1, holes(field(a.server)).size(), "Same hunter, no duplicates");
            var pig = a.pig(at); affect(a.server, 100);
            c.assertTrue(pig.getHealth() == 10 && pig.getVelocity().equals(Vec3d.ZERO), "No teleport hit");
            step(a.server); c.assertEquals(at, get(h, "center"), "Formation stays in announced place");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_pull")
    public void wideSphericalFieldPullsThreeTypesWithFalloffLiftAndVelocityPackets(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); p.setPosition(a.target.add(3, -.9, 0)); a.activate(ID); isolate(a); hole(p, a.target, 0);
            var pig = a.pig(a.target.add(0, 0, 3)); var item = a.item(a.target.add(-3, 0, 0));
            var far = a.pig(a.target.add(20, 0, 0)); var boundary = a.pig(a.target.add(24, 0, 0));
            var outside = a.pig(a.target.add(24.01, 0, 0)); var corner = a.pig(a.target.add(20, 0, 20));
            affect(a.server, 62);
            for (var e : List.of(p, pig, item, far, boundary)) {
                var delta = a.target.subtract(center(e));
                c.assertTrue(e.getVelocity().dotProduct(delta) > 0, "Inward velocity for " + e.getType());
                var observers = new ArrayList<Packet<?>>();
                new EntityTrackerEntry(c.getWorld(), e, 1, true, observers::add).tick();
                c.assertTrue(observers.stream().anyMatch(v -> v instanceof EntityVelocityUpdateS2CPacket), "Observer velocity packet");
                double before = delta.length(); e.move(MovementType.SELF, e.getVelocity());
                c.assertTrue(center(e).distanceTo(a.target) < before, "Real collision movement inward");
            }
            c.assertTrue(pig.getVelocity().y > 0 && item.getVelocity().y > 0, "Nearby mobs/items rise");
            c.assertTrue(Math.abs(p.getVelocity().x) > Math.abs(far.getVelocity().x), "Stronger near core");
            c.assertTrue(outside.getVelocity().equals(Vec3d.ZERO) && corner.getVelocity().equals(Vec3d.ZERO), "Exact spherical boundary");
            c.assertTrue(packets(a.s, p).stream().anyMatch(EntityVelocityUpdateS2CPacket.class::isInstance), "Own-player velocity packet");
            p.setVelocity(-2, .25, 0); var old = p.getVelocity(); affect(a.server, 62);
            c.assertTrue(p.getVelocity().equals(old), "Faster external motion preserved");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_core")
    public void coreDamagesOnCadenceConsumesItemsAndKeepsOrdinaryDamageRules(TestContext c) {
        try (var a = new Arena(c)) {
            var owner = a.player(); a.activate(ID); isolate(a); hole(owner, a.target, 0);
            var core = a.pig(a.target.add(.5, 0, 0)); var outer = a.pig(a.target.add(2.01, 0, 0));
            var item = a.item(a.target); affect(a.server, 60);
            c.assertTrue(core.getHealth() < 10 && outer.getHealth() == 10, "Two-block damaging core");
            float hp = core.getHealth(); core.timeUntilRegen = 0; affect(a.server, 62);
            c.assertTrue(core.getHealth() == hp, "No extra damage between seconds");
            core.timeUntilRegen = 0; affect(a.server, 80);
            c.assertTrue(core.getHealth() < hp, "Next one-second hit");
            c.assertTrue(item.isRemoved(), "Core consumes whole item stack");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_terrain")
    public void terrainIsPeeledWithBoundedFragmentsAndProtectedBlocksAreUntouched(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); a.activate(ID); isolate(a); var h = hole(p, a.target, 0); var f = field(a.server);
            var protectedPos = BlockPos.ofFloored(a.target).add(4, 0, 0);
            for (var block : List.of(Blocks.BEDROCK, Blocks.CHEST, Blocks.WATER, Blocks.OBSIDIAN)) {
                a.block(protectedPos, block);
                c.assertFalse((Boolean) invoke("erodible", c.getWorld(), protectedPos), "Protected " + block);
            }
            a.block(protectedPos, Blocks.AIR);
            var middle = BlockPos.ofFloored(a.target);
            for (int x = -4; x <= 4; x++) for (int y = -4; y <= 4; y++) for (int z = -4; z <= 4; z++)
                if (Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)) == 4) a.block(middle.add(x, y, z), Blocks.DIRT);
            int total = 0;
            for (int pass = 0; pass < 24; pass++) {
                invoke("erode", f, h);
                var debris = (List<?>) get(f, "debris");
                c.assertTrue(debris.size() <= 3, "At most three removals in a pass");
                for (var entry : debris) {
                    var fragment = (com.cursecycle.network.BlackHoleStatePayload.Fragment) get(entry, "fragment");
                    c.assertTrue(c.getWorld().getBlockState(fragment.pos()).isAir(), "Source actually removed");
                    c.assertTrue(Vec3d.ofCenter(fragment.pos()).distanceTo(a.target) <= 10, "Inside destruction sphere");
                    c.assertEquals(Block.getRawIdFromState(Blocks.DIRT.getDefaultState()), fragment.blockState(), "Matching visual block");
                }
                total += debris.size(); debris.clear();
            }
            c.assertTrue(total > 0, "Real surface erosion, not just particles");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_overlap")
    public void nearestHoleWinsAndEntityQueriesRemainBounded(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); var q = a.player(); p.setPosition(a.target.add(40, 0, 0)); q.setPosition(p.getPos());
            a.activate(ID); isolate(a); hole(p, a.target, 0); hole(q, a.target.add(4, 0, 0), 1);
            var pig = a.pig(a.target.add(3.5, 0, 0)); affect(a.server, 80);
            c.assertTrue(pig.getVelocity().x > 0, "Nearest hole only");
            c.assertTrue(pig.getHealth() < 10, "Odd issue tick still gets core damage");
            pig.discard(); isolate(a); hole(p, a.target, 0);
            var crowd = new ArrayList<PigEntity>();
            for (int i = 0; i < 200; i++) crowd.add(a.pig(a.target.add(3, 0, 0)));
            affect(a.server, 62);
            c.assertEquals(128L, crowd.stream().filter(v -> !v.getVelocity().equals(Vec3d.ZERO)).count(), "128-candidate cap");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_budget")
    public void sharedSpawnQueueAndDuplicateChallengeIdsRemainBounded(TestContext c) {
        try (var a = new Arena(c)) {
            for (int i = 0; i < 20; i++) a.player(); a.activate("double_challenge:black_hole,black_hole");
            var f = field(a.server);
            for (int i = 0; i < 20; i++) {
                int before = holes(f).size(); step(a.server);
                c.assertTrue(holes(f).size() - before <= 1 && holes(f).size() <= 4, "One shared search, cap four");
                int after = holes(f).size(); BlackHoleChallenge.tick(a.server);
                c.assertEquals(after, holes(f).size(), "Same tick idempotent");
            }
            c.assertEquals(4, holes(f).size(), "Saturates bounded field");
            c.assertEquals(20, sessions(f).size(), "Duplicate IDs do not duplicate sessions");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_loaded")
    public void unavailableRegionsAndWorldBorderNeverAcquireChunkTickets(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); a.activate(ID); isolate(a); var border = c.getWorld().getWorldBorder();
            double x = border.getCenterX(), z = border.getCenterZ(), size = border.getSize();
            try {
                border.setCenter(a.target.x, a.target.z); border.setSize(128);
                c.assertTrue((Boolean) invoke("available", c.getWorld(), a.target), "Loaded interior");
                border.setSize(32);
                c.assertFalse((Boolean) invoke("available", c.getWorld(), a.target), "Entire field fits border");
            } finally { border.setCenter(x, z); border.setSize(size); }
            int loaded = c.getWorld().getChunkManager().getLoadedChunkCount();
            var unavailable = new Vec3d(1000000, 100, 1000000);
            for (int i = 0; i < 100; i++) c.assertFalse((Boolean) invoke("available", c.getWorld(), unavailable), "Unloaded rejected");
            p.setPosition(unavailable);
            invoke("issue", field(a.server), sessions(field(a.server)).get(p.getUuid()), 0L);
            c.assertTrue(holes(field(a.server)).isEmpty(), "No unloaded spawn");
            c.assertEquals(loaded, c.getWorld().getChunkManager().getLoadedChunkCount(), "No chunk loads");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_lifecycle")
    public void stopDisableResetAndNbtReloadDiscardOldHunters(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); var challenge = ChallengeRegistry.get(ID); var pig = a.pig(a.target.add(3, 0, 0));
            for (int mode = 0; mode < 5; mode++) {
                a.activate(ID); isolate(a); var old = hole(p, a.target, -60); pig.setVelocity(Vec3d.ZERO);
                if (mode == 0) a.s.activate("test_easy");
                if (mode == 1) challenge.setEnabled(false);
                if (mode == 2) CurseCycleState.getServerState(a.server).reset();
                if (mode == 3) a.s.activate(ID);
                if (mode == 4) {
                    var state = CurseCycleState.getServerState(a.server); var nbt = state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
                    c.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY,
                            CurseCycleState.createFromNbt(nbt, c.getWorld().getRegistryManager()));
                }
                try {
                    step(a.server);
                    c.assertTrue(field(a.server) == null || !holes(field(a.server)).contains(old), "Old hunter removed " + mode);
                    c.assertTrue(pig.getVelocity().equals(Vec3d.ZERO), "No old pull " + mode);
                } finally { challenge.setEnabled(true); }
            }
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_eligibility")
    public void creativeDeathDisconnectPersonalAndDimensionTransitionsDropOldOwner(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); var q = a.player(); a.activate(ID); isolate(a); var f = field(a.server);
            hole(p, a.target, -60); var other = hole(q, a.target.add(5, 0, 0), -60);
            p.changeGameMode(GameMode.CREATIVE); step(a.server);
            c.assertTrue(holes(f).contains(other) && holes(f).size() == 1, "Other owner retained");
            p.setPosition(a.target.add(7, -.9, 0)); p.setVelocity(Vec3d.ZERO); affect(a.server, 0);
            c.assertTrue(p.getVelocity().equals(Vec3d.ZERO), "Creative unaffected");
            q.changeGameMode(GameMode.SPECTATOR); step(a.server); c.assertTrue(field(a.server) == null, "Spectator excluded");
            q.changeGameMode(GameMode.SURVIVAL); step(a.server); q.setHealth(0); step(a.server);
            c.assertTrue(field(a.server) == null, "Death cleanup"); q = a.s.respawn(q, false);
            q.setPosition(a.target.add(14, 0, 0)); step(a.server);
            c.assertTrue(holes(field(a.server)).stream().allMatch(h -> (Long) get(h, "issued") == 0), "Fresh respawn warning");
            var uuid = q.getUuid(); var oldSession = sessions(field(a.server)).get(uuid);
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(q.networkHandler, a.server);
            a.server.getPlayerManager().remove(q); c.assertTrue(field(a.server) == null, "Disconnect cleanup");
            q = a.s.player(uuid); q.setPosition(a.target.add(14, 0, 0)); step(a.server);
            c.assertTrue(sessions(field(a.server)).get(uuid) != oldSession, "New connection session");
            a.s.activate("test_easy"); var state = CurseCycleState.getServerState(a.server);
            state.getPersonalActiveChallenges().put(uuid, new ArrayList<>(List.of(ID, ID))); step(a.server); isolate(a);
            p.changeGameMode(GameMode.SURVIVAL); p.setPosition(a.target.add(2, -.9, 0)); p.setVelocity(Vec3d.ZERO);
            hole(q, a.target, -60); affect(a.server, 0);
            c.assertTrue(p.getVelocity().equals(Vec3d.ZERO), "Non-participant PERSONAL player unaffected");
            var old = holes(field(a.server)).getFirst(); var nether = a.server.getWorld(World.NETHER);
            q.teleport(nether, 0, 100, 0, 0, 0); step(a.server);
            c.assertFalse(holes(field(a.server)).contains(old), "Dimension drops old hunter");
            c.assertTrue(get(sessions(field(a.server)).get(uuid), "world") == nether, "Destination session");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_callback")
    public void lethalCoreCallbackCanClearIteratedQueue(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player(); a.activate("double_challenge:black_hole,pacifist"); isolate(a);
            p.setHealth(1); p.setPosition(a.target.add(.5, -.9, 0)); hole(p, a.target, -60); affect(a.server, 0);
            c.assertFalse(p.isAlive(), "Real lethal core hit"); c.assertTrue(field(a.server) == null, "Reentrant death cleanup");
        } c.complete();
    }

    private static void checked(Arena arena, Runnable action) {
        try { action.run(); } catch (Throwable e) { arena.close(); throw e; }
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "black_hole_real_clock", tickLimit = 360)
    public void realServerClockFormsFollowsPullsAndStopCancelsNewMotion(TestContext c) {
        var a = new Arena(c); var p = a.player(); p.setPosition(a.target); a.activate(ID);
        var f = field(a.server); var h = holes(f).getFirst(); var at = (Vec3d) get(h, "center");
        a.forceTickingArea(at); var item = a.item(at.add(4, 0, 0));
        c.runAtTick(50, () -> checked(a, () -> {
            c.assertEquals(at, get(h, "center"), "Formation does not move");
            c.assertTrue(item.getVelocity().equals(Vec3d.ZERO), "Real warning harmless");
        }));
        c.runAtTick(90, () -> checked(a, () -> {
            c.assertTrue(((Vec3d) get(h, "center")).distanceTo(at) > 1, "Registered server hook follows");
            c.assertTrue(center(item).distanceTo(at.add(4, 0, 0)) > .5 || item.isRemoved(), "Real item physics");
        }));
        c.runAtTick(250, () -> {
            try {
                c.assertTrue(holes(f).contains(h), "Hunter persists past old expiry");
                a.s.activate("test_easy"); item.setVelocity(Vec3d.ZERO); step(a.server);
                c.assertTrue(field(a.server) == null && item.getVelocity().equals(Vec3d.ZERO), "Stop cancels new motion");
                c.complete();
            } finally { a.close(); }
        });
    }
}