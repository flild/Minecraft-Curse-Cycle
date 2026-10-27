package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.FragileFloorChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.BedPart;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class FragileFloorGameTest implements FabricGameTest {
    private static final String ID = FragileFloorChallenge.ID;

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fragile_floor", tickLimit = 550)
    public void supportsTimingLootAndLifecycle(TestContext context) { new Scenario(context).run(); }

    private static final class Fixture {
        ServerPlayerEntity player;
        final BlockPos pos;
        double x = .5, y = 1, z = .5;
        boolean pin = true;
        Fixture(ServerPlayerEntity player, BlockPos pos) { this.player = player; this.pos = pos; }
        void position() {
            if (pin) {
                player.refreshPositionAndAngles(pos.getX()+x, pos.getY()+y, pos.getZ()+z, 0, 0);
                player.setVelocity(Vec3d.ZERO);
            }
        }
    }

    private static final class Scenario {
        final TestContext c;
        final ChallengeScenario s;
        final FragileFloorChallenge challenge;
        final boolean tileDrops;
        final List<Fixture> all = new ArrayList<>(), materials = new ArrayList<>();
        Fixture chest, shulker, ender, shared, creative, spectator, passenger, door, bed;
        Fixture jump, leave, replace, property, edge, mode, late, dead, dimension;
        BoatEntity boat;
        boolean done;

        Scenario(TestContext context) {
            c = context; s = new ChallengeScenario(c);
            challenge = (FragileFloorChallenge) ChallengeRegistry.get(ID);
            tileDrops = world().getGameRules().getBoolean(GameRules.DO_TILE_DROPS);
        }
        void run() {
            c.runAtEveryTick(() -> { if (!done) all.forEach(Fixture::position); });
            at(1, () -> {
                geometry();
                for (var block : List.of(Blocks.OAK_PLANKS, Blocks.STONE, Blocks.GLASS, Blocks.BEDROCK,
                        Blocks.OAK_SLAB, Blocks.OAK_STAIRS, Blocks.OAK_FENCE, Blocks.HONEY_BLOCK)) {
                    var f = fixture(block.getDefaultState());
                    if (block == Blocks.OAK_SLAB || block == Blocks.OAK_STAIRS) f.y = .5;
                    if (block == Blocks.OAK_FENCE) f.y = 1.5;
                    if (block == Blocks.HONEY_BLOCK) f.y = .9375;
                    materials.add(f);
                }
                chest = fixture(Blocks.CHEST.getDefaultState()); chest.y = .875;
                ((ChestBlockEntity) world().getBlockEntity(chest.pos)).setStack(0, cargo());
                shulker = fixture(Blocks.SHULKER_BOX.getDefaultState());
                ((ShulkerBoxBlockEntity) world().getBlockEntity(shulker.pos)).setStack(0, cargo());
                ender = fixture(Blocks.ENDER_CHEST.getDefaultState()); ender.y = .875;
                ender.player.getEnderChestInventory().setStack(0, cargo());
                shared = fixture(Blocks.OAK_PLANKS.getDefaultState());
                all.add(new Fixture(s.player(), shared.pos));
                world().setBlockState(shared.pos.up(), Blocks.TORCH.getDefaultState());
                door = fixture(Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                world().setBlockState(door.pos.down(2), Blocks.STONE.getDefaultState());
                world().setBlockState(door.pos.down(), Blocks.OAK_DOOR.getDefaultState());
                var doorShape = world().getBlockState(door.pos).getCollisionShape(world(), door.pos).getBoundingBox();
                door.x = (doorShape.minX + doorShape.maxX) / 2;
                door.z = (doorShape.minZ + doorShape.maxZ) / 2;
                bed = fixture(Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.EAST)); bed.y = .5625; bed.x = 1;
                world().setBlockState(bed.pos.east(), Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.EAST).with(BedBlock.PART, BedPart.HEAD));
                chest.player.getInventory().setStack(0, new ItemStack(Items.IRON_PICKAXE));
                chest.player.getInventory().getStack(0).setDamage(17);
                creative = fixture(Blocks.STONE.getDefaultState()); creative.player.changeGameMode(GameMode.CREATIVE);
                spectator = fixture(Blocks.STONE.getDefaultState()); spectator.player.changeGameMode(GameMode.SPECTATOR);
                passenger = fixture(Blocks.STONE.getDefaultState()); passenger.pin = false;
                boat = new BoatEntity(world(), passenger.pos.getX()+.5, passenger.pos.getY()+1, passenger.pos.getZ()+.5);
                boat.setNoGravity(true); world().spawnEntity(boat); passenger.player.startRiding(boat, true);
                all.forEach(Fixture::position);
                var source = server().getCommandSource().withLevel(4);
                for (var cmd : List.of("fdc list", "fdc activate fragile_floor", "fdc active"))
                    server().getCommandManager().executeWithPrefix(source, cmd);
                c.assertEquals(1L, ChallengeRegistry.getAll().stream().filter(it -> it.getId().equals(ID)).count(), "Registered once");
                s.state.getPersonalActiveChallengeIds(materials.getFirst().player.getUuid()).add(ID);
            });
            at(20, () -> { materials.getFirst().x = .6; materials.getFirst().position(); });
            at(40, () -> { materials.forEach(f -> intact(f, "39 ticks")); intact(chest, "Chest at 39"); intact(shared, "Independent player clocks"); });
            at(41, () -> {
                materials.forEach(f -> broken(f, "40 ticks on " + world().getBlockState(f.pos)));
                broken(chest, "Chest"); broken(shulker, "Shulker"); broken(ender, "Ender chest"); broken(shared, "Shared support");
                c.assertEquals(1, count(shared, Items.OAK_PLANKS), "Shared support drops once");
                c.assertEquals(1, count(shared, Items.TORCH), "Neighbour support update drops torch once");
                broken(door, "Door upper support");
                c.assertTrue(world().getBlockState(door.pos.down()).isAir(), "Door lower half updated");
                c.assertEquals(1, count(door, Items.OAK_DOOR), "Door loot once");
                broken(bed, "Bed support"); c.assertTrue(world().getBlockState(bed.pos.east()).isAir(), "Bed other half updated");
                c.assertEquals(1, count(bed, Items.RED_BED), "Bed loot once despite two contacts");
                c.assertEquals(17, chest.player.getInventory().getStack(0).getDamage(), "Held tool not used");
                c.assertEquals(1, count(materials.getFirst(), Items.OAK_PLANKS), "Duplicate dispatch drops once");
                c.assertEquals(1, count(materials.get(1), Items.COBBLESTONE), "Vanilla World.breakBlock stone loot");
                for (var i : List.of(2, 3)) c.assertEquals(0, items(materials.get(i)).size(), "Vanilla glass/bedrock loot");
                c.assertEquals(7, count(chest, Items.DIAMOND), "Chest contents once"); c.assertEquals(1, count(chest, Items.CHEST), "Chest item once");
                c.assertEquals(0, count(shulker, Items.DIAMOND), "No loose shulker cargo");
                var drops = items(shulker).stream().filter(e -> e.getStack().isOf(Items.SHULKER_BOX)).toList();
                c.assertEquals(1, drops.size(), "One shulker item");
                var container = drops.getFirst().getStack().get(DataComponentTypes.CONTAINER);
                c.assertTrue(container != null && container.stream().anyMatch(it -> ItemStack.areEqual(it, cargo())), "Shulker retains named cargo");
                c.assertTrue(ItemStack.areEqual(cargo(), ender.player.getEnderChestInventory().getStack(0)), "Ender inventory unchanged");
                intact(creative, "Creative excluded"); intact(spectator, "Spectator excluded"); intact(passenger, "Passenger excluded");
                s.state.getPersonalActiveChallengeIds(materials.getFirst().player.getUuid()).clear();
                boat.discard(); all.forEach(f -> f.pin = false); s.activate("test_easy");
            });
            at(50, () -> {
                jump = fixture(Blocks.STONE.getDefaultState()); leave = fixture(Blocks.STONE.getDefaultState());
                replace = fixture(Blocks.STONE.getDefaultState());
                property = fixture(Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
                edge = fixture(Blocks.OAK_PLANKS.getDefaultState());
                world().setBlockState(edge.pos.east(), Blocks.OAK_PLANKS.getDefaultState());
                mode = fixture(Blocks.STONE.getDefaultState());
                s.activate(ID); all.forEach(Fixture::position);
            });
            at(70, () -> { edge.x = 1; edge.position(); });
            at(89, () -> {
                intact(jump, "Before jump"); jump.player.jump(); jump.y = 2; jump.position(); leave.x = 2; leave.position();
                world().setBlockState(replace.pos, Blocks.DIRT.getDefaultState());
                world().setBlockState(property.pos, world().getBlockState(property.pos).with(SlabBlock.WATERLOGGED, true));
                mode.player.changeGameMode(GameMode.ADVENTURE);
            });
            at(90, () -> {
                intact(jump, "Jump reset"); intact(leave, "Leaving reset"); intact(replace, "Type reset"); intact(mode, "Mode reset");
                broken(property, "Same type property retains clock"); broken(edge, "First boundary support");
                c.assertTrue(world().getBlockState(edge.pos.east()).isOf(Blocks.OAK_PLANKS), "Later boundary contact has its own clock");
                jump.y = 1; leave.x = .5; jump.position(); leave.position();
            });
            at(109, () -> { c.assertTrue(world().getBlockState(edge.pos.east()).isOf(Blocks.OAK_PLANKS), "Later boundary contact at 39"); });
            at(110, () -> { c.assertTrue(world().getBlockState(edge.pos.east()).isAir(), "Later boundary contact at 40"); });
            at(129, () -> { broken(replace, "40 on replacement"); broken(mode, "40 in Adventure"); intact(jump, "39 after return"); intact(leave, "39 after return"); });
            at(130, () -> { broken(jump, "40 after jump"); broken(leave, "40 after walking away"); });
            at(140, () -> {
                s.activate("test_easy"); dead = fixture(Blocks.STONE.getDefaultState());
                dimension = fixture(Blocks.STONE.getDefaultState()); late = fixture(Blocks.STONE.getDefaultState());
                s.activate("double_challenge:fragile_floor,no_sprint");
                dead.player.setSprinting(true); c.assertFalse(dead.player.isSprinting(), "Other mechanic works");
            });
            at(179, () -> {
                dead.player.setHealth(0); dead.player = s.respawn(dead.player, false); dead.position();
                var uuid = late.player.getUuid(); server().getPlayerManager().remove(late.player); late.player = s.player(uuid); late.position();
                var nether = server().getWorld(World.NETHER); var netherPos = dimension.pos.withY(80);
                c.assertTrue(nether.setBlockState(netherPos, Blocks.STONE.getDefaultState()), "Nether support placed above minimum Y");
                dimension.pin = false;
                dimension.player.teleport(nether, netherPos.getX()+.5, netherPos.getY()+1, netherPos.getZ()+.5, 0, 0);
                dimension.player.onTeleportationDone();
                c.assertTrue(dimension.player.getServerWorld() == nether, "Player entered Nether");
            });
            at(180, () -> {
                intact(dead, "Respawn resets"); intact(late, "Reconnect resets"); intact(dimension, "Old world intact");
                dimension.player.teleport(world(), dimension.pos.getX()+.5, dimension.pos.getY()+1, dimension.pos.getZ()+.5, 0, 0);
                dimension.player.onTeleportationDone(); dimension.pin = true;
                c.assertTrue(dimension.player.getServerWorld() == world(), "Player returned to Overworld");
            });
            at(218, () -> { intact(dead, "Respawn 39"); intact(late, "Reconnect 39"); });
            at(219, () -> { broken(dead, "Respawn 40"); broken(late, "Reconnect 40"); intact(dimension, "Dimension 39"); });
            at(220, () -> {
                broken(dimension, "Dimension 40");
                c.assertTrue(server().getWorld(World.NETHER).getBlockState(dimension.pos.withY(80)).isOf(Blocks.STONE), "No delayed nether break");
                server().getWorld(World.NETHER).setBlockState(dimension.pos.withY(80), Blocks.AIR.getDefaultState());
            });
            at(230, () -> {
                s.activate("test_easy"); world().getGameRules().get(GameRules.DO_TILE_DROPS).set(false, server());
                shared = fixture(Blocks.OAK_PLANKS.getDefaultState()); s.activate(ID);
            });
            at(270, () -> {
                broken(shared, "doTileDrops false still breaks"); c.assertEquals(0, count(shared, Items.OAK_PLANKS), "World loot rule");
                world().getGameRules().get(GameRules.DO_TILE_DROPS).set(tileDrops, server()); s.activate("no_sprint");
                shared = fixture(Blocks.STONE.getDefaultState());
                s.state.getPersonalActiveChallengeIds(shared.player.getUuid()).add(ID);
                late = fixture(Blocks.STONE.getDefaultState());
            });
            at(310, () -> {
                broken(shared, "Personal-only activation"); intact(late, "Other player unaffected");
                s.state.getPersonalActiveChallengeIds(shared.player.getUuid()).clear(); s.activate(ID);
            });
            at(349, () -> {
                intact(late, "Stop at 39"); var uuid = late.player.getUuid();
                server().getPlayerManager().remove(late.player); s.activate("no_sprint"); late.player = s.player(uuid); late.position();
            });
            at(390, () -> {
                intact(late, "Offline completion leaves no delayed task");
                late.player.setSprinting(true); c.assertFalse(late.player.isSprinting(), "Other challenge preserved");
                s.activate(ID);
                var manager = server().getOverworld().getPersistentStateManager(); s.state.markDirty(); manager.save();
                var nbt = manager.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
                var loaded = CurseCycleState.createFromNbt(nbt.getCompound("data"), world().getRegistryManager());
                c.assertTrue(loaded.getActiveChallengeIds().contains(ID), "Activation on disk");
                c.assertTrue(loaded.getChallengeData().isEmpty(), "No unfinished clocks persisted");
                new FragileFloorChallenge().onTick(server(), late.player);
                intact(late, "Fresh implementation has no old contact");
            });
            at(429, () -> {
                intact(late, "39 ticks before disabling"); challenge.setEnabled(false);
            });
            at(430, () -> {
                intact(late, "Disabled mechanic cannot break"); challenge.setEnabled(true);
                s.activate("double_challenge:fragile_floor,no_block_placing");
            });
            at(469, () -> { intact(late, "Reactivation restarts at zero"); });
            at(470, () -> {
                broken(late, "Automatic breaking coexists with no_block_placing");
                late = fixture(Blocks.STONE.getDefaultState());
                s.activate("double_challenge:fragile_floor,no_block_breaking");
            });
            at(510, () -> {
                broken(late, "Automatic breaking coexists with mining fatigue");
                cleanup(); c.waitAndRun(5, c::complete);
            });
        }
        net.minecraft.server.world.ServerWorld world() { return c.getWorld(); }
        net.minecraft.server.MinecraftServer server() { return world().getServer(); }
        Fixture fixture(BlockState state) {
            var pos = c.getAbsolutePos(new BlockPos(4+(all.size()%8)*4, 4, 4+(all.size()/8)*4));
            world().setBlockState(pos, state); var f = new Fixture(s.player(), pos); all.add(f); f.position(); return f;
        }
        void intact(Fixture f, String reason) { c.assertFalse(world().getBlockState(f.pos).isAir(), reason); }
        void broken(Fixture f, String reason) { c.assertTrue(world().getBlockState(f.pos).isAir() || world().getBlockState(f.pos).isOf(Blocks.WATER), reason); }
        List<ItemEntity> items(Fixture f) { return world().getEntitiesByClass(ItemEntity.class, new Box(f.pos).expand(1.4), it -> true); }
        int count(Fixture f, Item item) { return items(f).stream().map(ItemEntity::getStack).filter(it -> it.isOf(item)).mapToInt(ItemStack::getCount).sum(); }
        ItemStack cargo() { var stack = new ItemStack(Items.DIAMOND, 7); stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("fragile cargo")); return stack; }
        @FunctionalInterface interface Step { void run() throws Exception; }
        void at(long tick, Step action) {
            c.runAtTick(tick, () -> {
                try { action.run(); }
                catch (Exception | AssertionError failure) { cleanup(); throw new RuntimeException("fragile_floor tick " + tick + ": " + failure.getMessage(), failure); }
            });
        }
        void cleanup() {
            if (done) return; done = true; if (boat != null) boat.discard(); challenge.setEnabled(true);
            world().getGameRules().get(GameRules.DO_TILE_DROPS).set(tileDrops, server()); s.close();
            // Batches reuse the test area. Do not leave our loot/fixtures for later tests.
            for (var f : all) {
                for (var pos : BlockPos.iterate(f.pos.add(-1, -2, -1), f.pos.add(1, 1, 1)))
                    world().setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world().getEntitiesByClass(ItemEntity.class, new Box(f.pos).expand(2, 100, 2), it -> true)
                        .forEach(ItemEntity::discard);
            }
        }
        void geometry() {
            var f = fixture(Blocks.OAK_SLAB.getDefaultState()); f.y = .5; f.position();
            c.assertTrue(FragileFloorChallenge.supports(f.player).containsKey(f.pos), "Bottom slab");
            f.x = .6; f.position(); c.assertTrue(FragileFloorChallenge.supports(f.player).containsKey(f.pos), "Movement on same support");
            world().setBlockState(f.pos, Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
            f.x = .1; f.position(); c.assertTrue(FragileFloorChallenge.supports(f.player).containsKey(f.pos), "Low stair tread");
            f.x = .8; f.y = 1; f.position(); c.assertTrue(FragileFloorChallenge.supports(f.player).containsKey(f.pos), "High stair tread");
            world().setBlockState(f.pos, Blocks.WATER.getDefaultState());
            c.assertTrue(FragileFloorChallenge.supports(f.player).isEmpty(), "Fluid excluded");
            world().setBlockState(f.pos, Blocks.STONE.getDefaultState()); f.y = .5; f.x = 1.1; f.position();
            c.assertTrue(FragileFloorChallenge.supports(f.player).isEmpty(), "Side collision excluded");
            f.x = .5; f.y = 2; f.position(); c.assertTrue(FragileFloorChallenge.supports(f.player).isEmpty(), "Air excluded"); f.pin = false;
        }
    }
}
