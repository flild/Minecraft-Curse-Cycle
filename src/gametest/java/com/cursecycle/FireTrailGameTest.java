package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.FireTrailChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import java.util.*;

public class FireTrailGameTest implements FabricGameTest {
    private static final String ID = FireTrailChallenge.ID;
    private static Object get(Object o, String name) {
        try { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void set(Object o, String name, Object value) {
        try { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Object trail(ServerPlayerEntity p) {
        try { var f = FireTrailChallenge.class.getDeclaredField("TRAILS"); f.setAccessible(true);
            return ((Map<?, ?>) f.get(null)).get(p.getServer());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Map<?, ?> fires(ServerPlayerEntity p) { return (Map<?, ?>) get(trail(p), "fires"); }
    /** Synchronous boundaries reset only sample/duplicate guards, not actual server time. */
    private static void step(ServerPlayerEntity p) {
        var t = trail(p);
        if (t != null) {
            set(t, "lastTick", p.getServer().getTicks() - 1);
            for (var s : ((Map<?, ?>) get(t, "sessions")).values()) set(s, "sampled", p.getServer().getTicks() - 4);
        }
        FireTrailChallenge.tick(p.getServer());
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void age(ServerPlayerEntity p, int ticks) {
        var map = (Map) fires(p);
        for (var entry : List.copyOf(map.entrySet())) {
            var e = (Map.Entry) entry; var fire = e.getValue();
            try { var ctor = fire.getClass().getDeclaredConstructors()[0]; ctor.setAccessible(true);
                map.put(e.getKey(), ctor.newInstance(get(fire, "owner"), p.getServer().getTicks() - ticks));
            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        }
    }
    private static final class Arena implements AutoCloseable {
        final TestContext c;
        final ChallengeScenario s;
        ServerPlayerEntity p;
        final BlockPos base;
        final boolean fireTick;
        final List<Entity> entities = new ArrayList<>();
        Arena(TestContext c) {
            this.c = c; s = new ChallengeScenario(c);
            fireTick = c.getWorld().getGameRules().getBoolean(GameRules.DO_FIRE_TICK);
            c.getWorld().getGameRules().get(GameRules.DO_FIRE_TICK).set(false, c.getWorld().getServer());
            base = c.getAbsolutePos(new BlockPos(2, 4, 2));
            for (var pos : BlockPos.iterate(base.add(-2, -1, -2), base.add(12, 4, 4)))
                c.getWorld().setBlockState(pos, pos.getY() == base.getY() - 1 ? Blocks.NETHERRACK.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            p = s.player(); ready(p); s.activate(ID); step(p);
        }
        void ready(ServerPlayerEntity player) {
            player.setNoGravity(true); player.setOnGround(true); player.setPosition(Vec3d.ofBottomCenter(base)); player.setVelocity(Vec3d.ZERO);
        }
        BlockPos walk() { return walk(p); }
        BlockPos walk(ServerPlayerEntity player) {
            player.setPosition(player.getPos().add(1, 0, 0)); player.setOnGround(true); step(player); return player.getBlockPos();
        }
        BlockPos lit() { var pos = walk(); assertFire(pos, "Movement placed actual vanilla fire"); return pos; }
        void fire(BlockPos pos) { c.getWorld().setBlockState(pos, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL); }
        void assertFire(BlockPos pos, String why) { c.assertTrue(c.getWorld().getBlockState(pos).isOf(Blocks.FIRE), why); }
        @Override public void close() {
            ChallengeRegistry.get(ID).setEnabled(true); FireTrailChallenge.clear(p.getServer()); entities.forEach(Entity::discard); s.close();
            c.getWorld().getGameRules().get(GameRules.DO_FIRE_TICK).set(fireTick, c.getWorld().getServer());
            for (var pos : BlockPos.iterate(base.add(-2, -1, -2), base.add(12, 4, 4)))
                c.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire_trail")
    public void movementStandingAndDuplicateTicks(TestContext c) {
        try (var a = new Arena(c)) {
            step(a.p); c.assertEquals(0, fires(a.p).size(), "Standing has no fire");
            a.p.setYaw(90); step(a.p); c.assertEquals(0, fires(a.p).size(), "Turning is not movement");
            a.lit(); a.p.setPosition(a.p.getPos().add(1, 0, 0));
            for (int i = 0; i < 5; i++) FireTrailChallenge.tick(a.p.getServer());
            c.assertEquals(1, fires(a.p).size(), "One world pass per actual tick");
            step(a.p); c.assertEquals(2, fires(a.p).size(), "Next sample accepted");
            a.p.setPosition(a.p.getPos().add(8, 0, 0)); step(a.p);
            c.assertEquals(2, fires(a.p).size(), "Teleport does not paint a line");
            FireTrailChallenge.clear(a.p.getServer()); a.ready(a.p); step(a.p);
            for (int i = 0; i < 2; i++) { a.p.setPosition(a.p.getPos().add(.075, 0, 0)); step(a.p); }
            c.assertEquals(0, fires(a.p).size(), "Small movement accumulated below threshold");
            a.p.setPosition(a.p.getPos().add(.075, 0, 0)); step(a.p);
            c.assertEquals(1, fires(a.p).size(), "Slow continuous movement also leaves fire");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire_trail")
    public void deadlineAgeUpdatesAndFireTickFalse(TestContext c) {
        try (var a = new Arena(c)) {
            var pos = a.lit(); c.getWorld().setBlockState(pos, c.getWorld().getBlockState(pos).with(FireBlock.AGE, 8));
            age(a.p, 99); step(a.p); a.assertFire(pos, "Own age updates retain identity through tick 99");
            age(a.p, 100); step(a.p); c.assertTrue(c.getWorld().getBlockState(pos).isAir(), "Expiry at 100 with doFireTick=false");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire_trail")
    public void preexistingRelitIdenticalFireAndForeignBlocksSurvive(TestContext c) {
        try (var a = new Arena(c)) {
            var existing = a.base.east(); a.fire(existing); a.walk(); c.assertEquals(0, fires(a.p).size(), "Never claim existing fire");
            var relit = a.lit(); var state = c.getWorld().getBlockState(relit);
            c.getWorld().getWorldChunk(relit).setBlockState(relit, Blocks.AIR.getDefaultState(), false);
            a.p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
            var hit = new BlockHitResult(Vec3d.ofCenter(relit.down()).add(0, .5, 0), Direction.UP, relit.down(), false);
            a.p.getMainHandStack().useOnBlock(new ItemUsageContext(a.p, Hand.MAIN_HAND, hit));
            c.assertEquals(state, c.getWorld().getBlockState(relit), "Real flint re-lit identical BlockState");
            c.assertEquals(0, fires(a.p).size(), "Mutation hook released old identity");
            var block = a.lit(); c.getWorld().setBlockState(block, Blocks.DIAMOND_BLOCK.getDefaultState());
            age(a.p, 100); step(a.p); a.s.activate("no_sprint");
            a.assertFire(existing, "Existing fire retained"); a.assertFire(relit, "Re-lit fire retained");
            c.assertTrue(c.getWorld().getBlockState(block).isOf(Blocks.DIAMOND_BLOCK), "Foreign block retained");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire_trail")
    public void airWaterVehicleAndBlockedSpace(TestContext c) {
        try (var a = new Arena(c)) {
            a.p.setPosition(a.p.getPos().add(0, 2, 0)); step(a.p); a.walk(); c.assertEquals(0, fires(a.p).size(), "No air support");
            a.ready(a.p); step(a.p); var target = a.base.east(); c.getWorld().setBlockState(target, Blocks.WATER.getDefaultState());
            a.walk(); c.assertTrue(c.getWorld().getBlockState(target).isOf(Blocks.WATER), "Water not replaced");
            a.ready(a.p); step(a.p); c.getWorld().setBlockState(target, Blocks.OAK_SLAB.getDefaultState());
            a.walk(); c.assertTrue(c.getWorld().getBlockState(target).isOf(Blocks.OAK_SLAB), "Non-air not replaced");
            a.ready(a.p); FireTrailChallenge.clear(a.p.getServer()); step(a.p); var boat = EntityType.BOAT.create(c.getWorld()); boat.setPosition(a.p.getPos());
            c.getWorld().spawnEntity(boat); a.entities.add(boat); a.p.startRiding(boat, true); a.walk();
            c.assertEquals(0, fires(a.p).size(), "Riding leaves no fire"); a.p.stopRiding();
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire_trail")
    public void modesPersonalDoubleAndSeparateOwners(TestContext c) {
        try (var a = new Arena(c)) {
            a.p.changeGameMode(GameMode.CREATIVE); step(a.p); a.walk(); c.assertTrue(trail(a.p) == null, "Creative excluded");
            a.p.changeGameMode(GameMode.SPECTATOR); a.walk(); c.assertTrue(trail(a.p) == null, "Spectator excluded");
            a.p.changeGameMode(GameMode.ADVENTURE); step(a.p); var first = a.lit();
            a.p.changeGameMode(GameMode.SURVIVAL); step(a.p); c.assertTrue(c.getWorld().getBlockState(first).isAir(), "Mode change clears trail");
            a.s.activate("no_sprint"); a.s.state.getPersonalActiveChallengeIds(a.p.getUuid()).add(ID); step(a.p); a.lit();
            var other = a.s.player(); a.ready(other); other.setPosition(other.getPos().add(0, 0, 2)); step(other); a.walk(other);
            c.assertFalse(c.getWorld().getBlockState(other.getBlockPos()).isOf(Blocks.FIRE), "Personal-only excludes other owner");
            a.s.state.getPersonalActiveChallengeIds(a.p.getUuid()).clear(); a.s.activate("double_challenge:fire_trail,no_block_placing"); step(a.p);
            var own = a.lit(); var theirs = a.walk(other); a.assertFire(theirs, "Global and no_block_placing pair");
            ChallengeRegistry.get(ID).onStop(a.p.getServer(), a.p); c.assertTrue(c.getWorld().getBlockState(own).isAir(), "Own stop clears own fire");
            a.assertFire(theirs, "Other owner retained");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire_trail")
    public void stopDisableResetAndNewActivation(TestContext c) {
        try (var a = new Arena(c)) {
            var first = a.lit(); ChallengeRegistry.get(ID).setEnabled(false); c.assertTrue(c.getWorld().getBlockState(first).isAir(), "Disable immediate");
            ChallengeRegistry.get(ID).setEnabled(true); step(a.p); var second = a.lit(); a.s.activate(ID); step(a.p);
            c.assertTrue(c.getWorld().getBlockState(second).isAir(), "New serial clears old fire");
            var third = a.lit(); a.s.state.reset(); step(a.p);
            c.assertTrue(c.getWorld().getBlockState(third).isAir(), "Reset clears trail");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire_trail")
    public void stateReloadRespawnDisconnectAndDimension(TestContext c) {
        try (var a = new Arena(c)) {
            var first = a.lit(); var nbt = a.s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
            c.assertTrue(a.s.state.getChallengeData().isEmpty(), "No persistent queue");
            c.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY, CurseCycleState.createFromNbt(nbt, c.getWorld().getRegistryManager()));
            step(a.p); c.assertTrue(c.getWorld().getBlockState(first).isAir(), "Loaded state clears old fire");
            var second = a.lit(); var old = a.p; a.p = a.s.respawn(old, false); a.ready(a.p); step(a.p);
            c.assertTrue(c.getWorld().getBlockState(second).isAir(), "Respawn clears old owner");
            var third = a.lit(); var uuid = a.p.getUuid();
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(a.p.networkHandler, a.p.getServer());
            a.p.getServer().getPlayerManager().remove(a.p);
            c.assertTrue(c.getWorld().getBlockState(third).isAir(), "Fabric disconnect clears immediately");
            a.p = a.s.player(uuid); a.ready(a.p); step(a.p); var fourth = a.lit();
            a.p.teleport(a.p.getServer().getWorld(World.NETHER), a.base.getX(), 80, a.base.getZ(), 0, 0); a.p.onTeleportationDone();
            c.assertTrue(c.getWorld().getBlockState(fourth).isAir(), "Dimension event clears immediately");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire_trail")
    public void unloadHookBeforeSavePreservesForeignFire(TestContext c) {
        try (var a = new Arena(c)) {
            var own = a.lit(); var foreign = own.south(); a.fire(foreign); var chunk = c.getWorld().getWorldChunk(own);
            chunk.setLoadedToWorld(false);
            try { c.assertTrue(chunk.getBlockState(own).isAir(), "Transformed unload cleans original");
                a.assertFire(foreign, "Foreign fire retained on unload"); c.assertTrue(chunk.needsSaving(), "Chunk dirty before vanilla save");
            } finally { chunk.setLoadedToWorld(true); }
            step(a.p); a.walk(); FireTrailChallenge.clear(a.p.getServer()); a.assertFire(foreign, "Server cleanup preserves foreign fire");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire_trail")
    public void vanillaFireBurnsCreatureAndSpreadsIntoWood(TestContext c) {
        try (var a = new Arena(c)) {
            var own = a.lit(); a.p.setPosition(Vec3d.ofBottomCenter(a.base));
            var cow = EntityType.COW.create(c.getWorld()); cow.setAiDisabled(true); cow.setNoGravity(true);
            cow.setPosition(Vec3d.ofBottomCenter(own)); c.getWorld().spawnEntity(cow); a.entities.add(cow);
            for (int i = 0; i < 30; i++) c.getWorld().getBlockState(own).onEntityCollision(c.getWorld(), own, cow);
            c.assertTrue(cow.isOnFire() && cow.getHealth() < cow.getMaxHealth(), "Actual fire collision ignites and damages creature");
            var wood = own.east(); c.getWorld().setBlockState(wood, Blocks.OAK_LOG.getDefaultState());
            c.getWorld().getGameRules().get(GameRules.DO_FIRE_TICK).set(true, a.p.getServer()); BlockPos spread = null;
            for (int seed = 0; seed < 500 && spread == null; seed++) {
                c.getWorld().setBlockState(wood, Blocks.OAK_LOG.getDefaultState());
                c.getWorld().getBlockState(own).scheduledTick(c.getWorld(), own, Random.create(seed));
                for (var pos : BlockPos.iterate(own.add(-1, 0, -1), own.add(2, 2, 1)))
                    if (!pos.equals(own) && c.getWorld().getBlockState(pos).isOf(Blocks.FIRE)) { spread = pos.toImmutable(); break; }
            }
            c.assertTrue(spread != null, "Real scheduled fire spreads to wood neighbourhood");
            c.assertEquals(1, fires(a.p).size(), "Spread never claimed"); age(a.p, 100); step(a.p);
            c.assertTrue(c.getWorld().getBlockState(own).isAir(), "Original expired"); a.assertFire(spread, "Spread survives expiry");
            a.s.activate("no_sprint"); a.assertFire(spread, "Spread survives end");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire_trail_budget")
    public void boundedQueueFairBudgetAndNoBacklog(TestContext c) {
        try (var a = new Arena(c)) {
            var all = new ArrayList<ServerPlayerEntity>();
            try {
                for (var pos : BlockPos.iterate(a.base.add(0, -1, 0), a.base.add(22, 0, 22)))
                    c.getWorld().setBlockState(pos, pos.getY() < a.base.getY() ? Blocks.NETHERRACK.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                FireTrailChallenge.clear(a.p.getServer());
                for (int i = 0; i < 20; i++) {
                    var p = i == 0 ? a.p : a.s.player(); a.ready(p); p.setPosition(p.getPos().add(0, 0, i)); all.add(p);
                }
                step(a.p);
                for (int round = 0; round < 18; round++) {
                    int before = fires(a.p).size();
                    for (var p : all) p.setPosition(p.getPos().add(1, 0, 0));
                    step(a.p); int after = fires(a.p).size();
                    c.assertTrue(after - before <= 16 && after <= 256, "Shared per-tick work and queue bounded");
                    if (round == 0) c.assertEquals(16, after, "Exactly sixteen placements across twenty owners");
                }
                c.assertEquals(256, fires(a.p).size(), "Actual saturation reached");
                age(a.p, 100); step(a.p); c.assertEquals(0, fires(a.p).size(), "No catch-up after capacity is freed");
            } finally {
                FireTrailChallenge.clear(a.p.getServer());
                for (var pos : BlockPos.iterate(a.base.add(0, -1, 0), a.base.add(22, 0, 22)))
                    c.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire_trail")
    public void deathAndUnloadedAreaNeverCreateOrRestoreTrail(TestContext c) throws Exception {
        try (var a = new Arena(c)) {
            var own = a.lit();
            c.assertTrue(a.p.damage(c.getWorld().getDamageSources().genericKill(), Float.MAX_VALUE), "Real lethal damage");
            c.assertFalse(a.p.isAlive(), "Actual player death");
            c.assertTrue(c.getWorld().getBlockState(own).isAir(), "Death event clears original immediately");
            step(a.p); c.assertTrue(trail(a.p) == null, "Dead owner has no session");
            var method = FireTrailChallenge.class.getDeclaredMethod("available", net.minecraft.server.world.ServerWorld.class, BlockPos.class);
            method.setAccessible(true);
            var far = a.base.add(1000000, 0, 1000000); var manager = c.getWorld().getChunkManager();
            int loaded = manager.getLoadedChunkCount();
            c.assertFalse((Boolean) method.invoke(null, c.getWorld(), far), "Unloaded placement area rejected");
            c.assertEquals(loaded, manager.getLoadedChunkCount(), "No chunks loaded by eligibility check");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire_trail_clock", tickLimit = 150)
    public void realServerClockExpiresAfterFiveSeconds(TestContext c) {
        var a = new Arena(c); final int began = a.p.getServer().getTicks();
        c.runAtTick(4, () -> { a.p.setPosition(Vec3d.ofBottomCenter(a.base.east())); a.p.setOnGround(true); });
        c.runAtTick(6, () -> { try { a.assertFire(a.base.east(), "Actual END_SERVER_TICK placement"); } catch (Throwable e) { a.close(); throw e; } });
        c.runAtTick(102, () -> { try { a.assertFire(a.base.east(), "Before 100 ticks since placement"); } catch (Throwable e) { a.close(); throw e; } });
        c.runAtTick(106, () -> { try {
            c.assertTrue(c.getWorld().getBlockState(a.base.east()).isAir(), "Actual five-second expiry");
            c.assertTrue(a.p.getServer().getTicks() - began >= 100, "Actual clock advanced"); c.complete();
        } finally { a.close(); } });
    }
}
