package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.DroughtChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import java.util.*;

public class DroughtGameTest implements FabricGameTest {
    private static final String ID = DroughtChallenge.ID;
    private static Object get(Object o, String field) {
        try { var f = o.getClass().getDeclaredField(field); f.setAccessible(true); return f.get(o); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void set(Object o, String field, Object value) {
        try { var f = o.getClass().getDeclaredField(field); f.setAccessible(true); f.set(o, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Object sweep(ServerPlayerEntity p) {
        try { var f = DroughtChallenge.class.getDeclaredField("SWEEPS"); f.setAccessible(true);
            return ((Map<?, ?>) f.get(null)).get(p.getServer());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void step(ServerPlayerEntity p) {
        if (sweep(p) == null) DroughtChallenge.tick(p.getServer());
        set(sweep(p), "lastTick", p.getServer().getTicks() - DroughtChallenge.INTERVAL);
        DroughtChallenge.tick(p.getServer());
    }
    private static void scan(ServerPlayerEntity p) { for (int i = 0; i < 16; i++) step(p); }
    private static Map<?, ?> cursors(ServerPlayerEntity p) { return (Map<?, ?>) get(sweep(p), "cursors"); }
    private static final class Arena implements AutoCloseable {
        final TestContext c;
        final ChallengeScenario s;
        final BlockPos base;
        ServerPlayerEntity p;
        Arena(TestContext c) {
            this.c = c; s = new ChallengeScenario(c);
            base = c.getAbsolutePos(new BlockPos(8, 6, 8));
            // Sealed, generous basin: ordinary scheduled fluid updates remain enabled.
            for (var pos : BlockPos.iterate(base.add(-8, -5, -8), base.add(8, 4, 8)))
                c.getWorld().setBlockState(pos, pos.getY() == base.getY() - 5
                        || Math.abs(pos.getX() - base.getX()) == 8 || Math.abs(pos.getZ() - base.getZ()) == 8
                        ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            p = s.player(); ready(p); s.activate(ID); DroughtChallenge.clear(p.getServer());
        }
        void ready(ServerPlayerEntity player) {
            player.setNoGravity(true); player.setPosition(Vec3d.ofBottomCenter(base)); player.setVelocity(Vec3d.ZERO);
        }
        void put(BlockPos pos, BlockState state) { c.getWorld().setBlockState(pos, state, Block.NOTIFY_ALL); }
        void water(BlockPos pos) { put(pos, Blocks.WATER.getDefaultState()); }
        void dry(BlockPos pos) { c.assertTrue(c.getWorld().getFluidState(pos).isEmpty(), "Dry at " + pos); }
        void source(BlockPos pos) { c.assertTrue(c.getWorld().getFluidState(pos).isStill()
                && c.getWorld().getFluidState(pos).isIn(net.minecraft.registry.tag.FluidTags.WATER), "Water source at " + pos); }
        @Override public void close() {
            ChallengeRegistry.get(ID).setEnabled(true); DroughtChallenge.clear(p.getServer()); s.close();
            for (var pos : BlockPos.iterate(base.add(-8, -5, -8), base.add(8, 4, 8)))
                c.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "drought")
    public void sourcesOnlyAndExactCylinderBoundary(TestContext c) {
        try (var a = new Arena(c)) {
            var inside = List.of(a.base, a.base.east(6), a.base.up(3), a.base.down(3), a.base.add(4, 0, 4));
            var outside = List.of(a.base.east(7), a.base.up(4), a.base.down(4), a.base.add(5, 0, 5));
            inside.forEach(a::water); outside.forEach(a::water);
            var flow = a.base.west(2); var lava = a.base.north(2); var ice = a.base.south(2);
            a.put(flow, Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL, 3));
            a.put(lava, Blocks.LAVA.getDefaultState()); a.put(ice, Blocks.ICE.getDefaultState());
            scan(a.p); inside.forEach(a::dry); outside.forEach(a::source);
            c.assertEquals(3, c.getWorld().getBlockState(flow).get(FluidBlock.LEVEL), "Flow not evaporated directly");
            c.assertTrue(c.getWorld().getBlockState(lava).isOf(Blocks.LAVA), "Lava retained");
            c.assertTrue(c.getWorld().getBlockState(ice).isOf(Blocks.ICE), "Ice retained");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "drought")
    public void waterloggedStateAndBlockEntityPreserved(TestContext c) {
        try (var a = new Arena(c)) {
            var stair = a.base.east(); var chest = a.base.west();
            var wet = Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST)
                    .with(StairsBlock.HALF, net.minecraft.block.enums.BlockHalf.TOP).with(Properties.WATERLOGGED, true);
            a.put(stair, wet); a.put(chest, Blocks.CHEST.getDefaultState().with(Properties.WATERLOGGED, true));
            var entity = (ChestBlockEntity) c.getWorld().getBlockEntity(chest);
            entity.setStack(0, new ItemStack(Items.DIAMOND, 7));
            var expected = c.getWorld().getBlockState(stair).with(Properties.WATERLOGGED, false);
            scan(a.p); a.dry(stair); a.dry(chest);
            c.assertEquals(expected, c.getWorld().getBlockState(stair), "Every other stair property retained");
            c.assertTrue(entity == c.getWorld().getBlockEntity(chest), "Same block entity");
            c.assertEquals(7, entity.getStack(0).getCount(), "Container contents retained");
            a.s.activate("no_sprint"); c.assertEquals(expected, c.getWorld().getBlockState(stair), "No restoration");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "drought")
    public void actualBucketUseSurvivesUntilSweepAndCanBeCollected(TestContext c) {
        try (var a = new Arena(c)) {
            var floor = a.base.down(2); a.put(floor, Blocks.STONE.getDefaultState());
            a.p.setPosition(Vec3d.ofCenter(floor).add(0, 2, 0)); a.p.setPitch(90); a.p.setYaw(0);
            a.p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            a.p.interactionManager.interactItem(a.p, c.getWorld(), a.p.getMainHandStack(), Hand.MAIN_HAND);
            a.source(floor.up()); c.assertTrue(a.p.getMainHandStack().isOf(Items.BUCKET), "Real bucket consumed");
            a.p.interactionManager.interactItem(a.p, c.getWorld(), a.p.getMainHandStack(), Hand.MAIN_HAND);
            a.dry(floor.up()); c.assertTrue(a.p.getMainHandStack().isOf(Items.WATER_BUCKET), "Can collect before evaporation");
            a.p.interactionManager.interactItem(a.p, c.getWorld(), a.p.getMainHandStack(), Hand.MAIN_HAND);
            a.source(floor.up()); scan(a.p); a.dry(floor.up());
            c.assertTrue(a.p.getMainHandStack().isOf(Items.BUCKET), "Evaporation never refunds bucket");
            a.s.activate("double_challenge:drought,no_block_placing");
            a.p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            a.p.interactionManager.interactItem(a.p, c.getWorld(), a.p.getMainHandStack(), Hand.MAIN_HAND);
            a.dry(floor.up()); c.assertTrue(a.p.getMainHandStack().isOf(Items.WATER_BUCKET), "Pair keeps existing placement ban");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "drought")
    public void applicabilityStopDisableResetAndPersonalIsolation(TestContext c) {
        try (var a = new Arena(c)) {
            for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
                a.p.changeGameMode(mode); a.water(a.base); scan(a.p); a.source(a.base);
            }
            a.p.changeGameMode(GameMode.ADVENTURE); a.ready(a.p); scan(a.p); a.dry(a.base);
            a.s.activate("no_sprint"); a.water(a.base); scan(a.p); a.source(a.base);
            a.s.state.getPersonalActiveChallengeIds(a.p.getUuid()).add(ID); scan(a.p); a.dry(a.base);
            var other = a.s.player(); a.ready(other); a.p.changeGameMode(GameMode.CREATIVE);
            a.water(a.base); scan(a.p); a.source(a.base); // other has no personal activation
            a.s.activate(ID); a.p.changeGameMode(GameMode.SURVIVAL); a.ready(a.p);
            ChallengeRegistry.get(ID).setEnabled(false); scan(a.p); a.source(a.base);
            ChallengeRegistry.get(ID).setEnabled(true); scan(a.p); a.dry(a.base);
            a.s.state.reset(); a.water(a.base); scan(a.p); a.source(a.base);
            a.s.activate(ID); scan(a.p); a.dry(a.base);
            c.assertTrue(a.s.state.getChallengeData().getCompound(ID).isEmpty(), "No water snapshots persisted");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "drought")
    public void movementOverlapSharedBudgetsAndDuplicateCallbacks(TestContext c) {
        try (var a = new Arena(c)) {
            var players = new ArrayList<ServerPlayerEntity>(); players.add(a.p);
            for (int i = 0; i < 19; i++) { var p = a.s.player(); a.ready(p); players.add(p); }
            step(a.p);
            for (var cursor : cursors(a.p).values()) c.assertEquals(256, get(cursor, "offset"), "Coincident areas share one scan");
            for (int i = 0; i < 20; i++) DroughtChallenge.tick(a.p.getServer());
            for (var cursor : cursors(a.p).values()) c.assertEquals(256, get(cursor, "offset"), "Repeated tick does not multiply budget");
            for (var p : players) p.setPosition(p.getPos().add(1, 0, 0));
            step(a.p);
            for (var cursor : cursors(a.p).values()) c.assertEquals(512, get(cursor, "offset"), "Movement preserves sweep progress");
            for (int i = 0; i < players.size(); i++) players.get(i).setPosition(Vec3d.ofBottomCenter(a.base.add(i % 4, 0, i / 4)));
            int before = cursors(a.p).values().stream().mapToInt(cursor -> (Integer) get(cursor, "offset")).sum();
            step(a.p);
            int after = cursors(a.p).values().stream().mapToInt(cursor -> (Integer) get(cursor, "offset")).sum();
            c.assertEquals(256, after - before, "Overlapping regions share attempt budget");
            // Dense actual sources: count world changes, rather than just testing a counter.
            for (var p : players) a.ready(p);
            for (var pos : BlockPos.iterate(a.base.add(-3, -3, -3), a.base.add(3, -3, 3))) a.water(pos);
            DroughtChallenge.clear(a.p.getServer()); step(a.p);
            long remaining = BlockPos.stream(a.base.add(-3, -3, -3), a.base.add(3, -3, 3))
                    .filter(pos -> !c.getWorld().getFluidState(pos).isEmpty()).count();
            c.assertEquals(41L, remaining, "At most eight actual removals for twenty owners");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "drought")
    public void reloadSerialDeathRespawnDisconnectAndDimensions(TestContext c) {
        try (var a = new Arena(c)) {
            a.water(a.base); scan(a.p); a.dry(a.base);
            var nbt = a.s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
            var reloaded = CurseCycleState.createFromNbt(nbt, c.getWorld().getRegistryManager());
            c.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY, reloaded);
            step(a.p); c.assertTrue(get(sweep(a.p), "state") == reloaded, "State identity reconciled");
            reloaded.beginActivation(); step(a.p);
            c.assertEquals(reloaded.getActivationSerial(), get(sweep(a.p), "activation"), "New activation invalidates cursor");
            c.assertTrue(a.p.damage(c.getWorld().getDamageSources().genericKill(), Float.MAX_VALUE), "Actual death");
            a.water(a.base); scan(a.p); a.source(a.base);
            var dead = a.p; a.p = a.s.respawn(dead, false); a.ready(a.p); scan(a.p); a.dry(a.base);
            var uuid = a.p.getUuid();
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(a.p.networkHandler, a.p.getServer());
            a.p.getServer().getPlayerManager().remove(a.p); a.water(a.base); scan(a.p); a.source(a.base);
            a.p = a.s.player(uuid); a.ready(a.p); scan(a.p); a.dry(a.base);
            a.p.teleport(a.p.getServer().getWorld(World.NETHER), a.base.getX(), 80, a.base.getZ(), 0, 0);
            a.p.onTeleportationDone(); a.water(a.base); scan(a.p); a.source(a.base);
            a.p.teleport(c.getWorld(), a.base.getX() + .5, a.base.getY(), a.base.getZ() + .5, 0, 0);
            a.p.onTeleportationDone(); scan(a.p); a.dry(a.base);
            c.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY, a.s.state);
            a.s.activate("no_sprint"); scan(a.p); a.dry(a.base);
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "drought")
    public void unloadedAreaAndWorldBorderDoNotLoadChunks(TestContext c) throws Exception {
        try (var a = new Arena(c)) {
            var method = DroughtChallenge.class.getDeclaredMethod("available", net.minecraft.server.world.ServerWorld.class, BlockPos.class);
            method.setAccessible(true); var manager = c.getWorld().getChunkManager();
            int count = manager.getLoadedChunkCount();
            c.assertFalse((Boolean) method.invoke(null, c.getWorld(), a.base.add(1000000, 0, 1000000)), "Unloaded source rejected");
            c.assertFalse((Boolean) method.invoke(null, c.getWorld(), new BlockPos(30000000, a.base.getY(), 0)), "Beyond border rejected");
            c.assertEquals(count, manager.getLoadedChunkCount(), "No chunk loading");
        } c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "drought_lake", tickLimit = 260)
    public void realClockDriesRegeneratingLakeAndStops(TestContext c) {
        var a = new Arena(c);
        for (var pos : BlockPos.iterate(a.base.add(-3, -1, -3), a.base.add(3, -1, 3))) {
            a.put(pos.down(), Blocks.STONE.getDefaultState()); a.water(pos);
        }
        c.runAtTick(1, () -> { try { a.source(a.base.down()); } catch (Throwable e) { a.close(); throw e; } });
        c.runAtTick(180, () -> { try {
            for (var pos : BlockPos.iterate(a.base.add(-3, -1, -3), a.base.add(3, -1, 3))) a.dry(pos);
            a.s.activate("no_sprint"); a.water(a.base.down());
        } catch (Throwable e) { a.close(); throw e; } });
        c.runAtTick(230, () -> { try { a.source(a.base.down()); c.complete(); } finally { a.close(); } });
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "drought_farm", tickLimit = 200)
    public void realInfiniteSourceFarmAndBubbleConstruction(TestContext c) {
        var a = new Arena(c);
        a.s.activate("no_sprint");
        for (var pos : BlockPos.iterate(a.base.add(-2, -1, -2), a.base.add(2, -1, 2))) a.put(pos, Blocks.STONE.getDefaultState());
        var pool = List.of(a.base.down(), a.base.down().east(), a.base.down().south(), a.base.down().east().south());
        for (var pos : pool) a.put(pos.down(), Blocks.STONE.getDefaultState());
        pool.forEach(a::water);
        a.put(pool.getFirst(), Blocks.AIR.getDefaultState());
        var farm = a.base.west().down(); a.put(farm, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
        a.put(farm.up(), Blocks.WHEAT.getDefaultState());
        var column = a.base.north(3); a.put(column.down(), Blocks.SOUL_SAND.getDefaultState());
        a.put(column, Blocks.BUBBLE_COLUMN.getDefaultState()); a.water(column.up());
        c.runAtTick(20, () -> { try {
            pool.forEach(a::source); // Real vanilla infinite-source regeneration before activation.
            c.getWorld().getBlockState(farm).randomTick(c.getWorld(), farm, Random.create(1));
            c.assertEquals(7, c.getWorld().getBlockState(farm).get(FarmlandBlock.MOISTURE), "Watered control remains moist");
            a.s.activate(ID);
        } catch (Throwable e) { a.close(); throw e; } });
        c.runAtTick(150, () -> { try {
            pool.forEach(a::dry); a.dry(column); a.dry(column.up());
            c.assertTrue(c.getWorld().getBlockState(column.down()).isOf(Blocks.SOUL_SAND), "Construction base retained");
            c.getWorld().getBlockState(farm).randomTick(c.getWorld(), farm, Random.create(1));
            c.assertTrue(c.getWorld().getBlockState(farm).get(FarmlandBlock.MOISTURE) < 7, "Ordinary farmland loses moisture");
            c.assertTrue(c.getWorld().getBlockState(farm.up()).isOf(Blocks.WHEAT), "Crop not directly destroyed");
            c.complete();
        } finally { a.close(); } });
    }
}
