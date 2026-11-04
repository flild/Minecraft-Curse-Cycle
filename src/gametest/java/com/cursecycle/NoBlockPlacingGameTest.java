package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.NoBlockPlacingChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.*;
import net.minecraft.predicate.BlockPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

public class NoBlockPlacingGameTest implements FabricGameTest {
    private static final String ID = NoBlockPlacingChallenge.ID;

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_block_placing")
    public void blocksPlantsMultiblocksAndFireBothHands(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            var pos = context.getAbsolutePos(new BlockPos(2, 3, 2));
            p.teleport(context.getWorld(), pos.getX() + 3.5, pos.getY(), pos.getZ() + 3.5, 0, 0);
            for (var item : List.of(Items.STONE, Items.CHEST, Items.TORCH, Items.OAK_DOOR, Items.RED_BED,
                    Items.WHEAT_SEEDS, Items.OAK_SAPLING, Items.SNOW, Items.POWDER_SNOW_BUCKET,
                    Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)) {
                for (var hand : Hand.values()) {
                    prepare(context, pos, item);
                    s.activate("test_easy");
                    p.setStackInHand(hand, new ItemStack(item));
                    var before = snapshot(context, pos);
                    use(p, pos.down(), hand);
                    context.assertFalse(before.equals(snapshot(context, pos)), "Vanilla control places " + item + " " + hand);
                    prepare(context, pos, item);
                    s.activate(ID);
                    p.setStackInHand(hand, new ItemStack(item));
                    before = snapshot(context, pos);
                    var original = p.getStackInHand(hand).copy();
                    int used = p.getStatHandler().getStat(Stats.USED.getOrCreateStat(item));
                    for (int repeat = 0; repeat < 3; repeat++) use(p, pos.down(), hand);
                    context.assertEquals(before, snapshot(context, pos), "No block or multiblock half for " + item + " " + hand);
                    context.assertTrue(ItemStack.areEqual(original, p.getStackInHand(hand)), "Count/components/durability retained " + item);
                    context.assertEquals(used, p.getStatHandler().getStat(Stats.USED.getOrCreateStat(item)), "No successful-use statistic " + item);
                }
            }
            for (var item : List.of(Items.DANDELION, Items.CANDLE)) {
                var base = item == Items.CANDLE ? Blocks.CAKE : Blocks.FLOWER_POT;
                for (var hand : Hand.values()) {
                    prepare(context, pos, Items.STONE);
                    context.getWorld().setBlockState(pos, base.getDefaultState());
                    s.activate("test_easy");
                    p.setStackInHand(hand, new ItemStack(item, 4));
                    use(p, pos, hand);
                    context.assertFalse(context.getWorld().getBlockState(pos).isOf(base), "Control inserts " + item);
                    context.getWorld().setBlockState(pos, base.getDefaultState());
                    s.activate(ID);
                    p.setStackInHand(hand, new ItemStack(item, 4));
                    use(p, pos, hand);
                    context.assertTrue(context.getWorld().getBlockState(pos).isOf(base), "Insertion denied " + item);
                    context.assertEquals(4, p.getStackInHand(hand).getCount(), "Insertion costs nothing");
                }
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_block_placing")
    public void bucketsAirRaycastWaterloggingAndDispenser(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            var pos = context.getAbsolutePos(new BlockPos(2, 3, 2));
            for (var item : List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.COD_BUCKET, Items.LILY_PAD)) {
                for (var hand : Hand.values()) for (boolean active : List.of(false, true)) {
                    prepare(context, pos, Items.STONE);
                    if (item == Items.LILY_PAD) context.getWorld().setBlockState(pos, Blocks.WATER.getDefaultState());
                    aimDown(p, pos);
                    s.activate(active ? ID : "test_easy");
                    p.setStackInHand(hand, new ItemStack(item));
                    var original = p.getStackInHand(hand).copy();
                    int used = p.getStatHandler().getStat(Stats.USED.getOrCreateStat(item));
                    var before = snapshot(context, pos);
                    p.interactionManager.interactItem(p, context.getWorld(), p.getStackInHand(hand), hand);
                    context.assertEquals(active, before.equals(snapshot(context, pos)), "Air-use control/restriction " + item + " " + hand);
                    if (active) {
                        context.assertTrue(ItemStack.areEqual(original, p.getStackInHand(hand)), "Resource/components retained " + item);
                        context.assertEquals(used, p.getStatHandler().getStat(Stats.USED.getOrCreateStat(item)), "No air-use statistic " + item);
                    }
                    context.getWorld().getEntitiesByClass(net.minecraft.entity.passive.FishEntity.class,
                            new Box(pos).expand(5), e -> true).forEach(net.minecraft.entity.Entity::discard);
                }
            }
            prepare(context, pos, Items.STONE);
            s.activate(ID);
            context.getWorld().setBlockState(pos, Blocks.OAK_SLAB.getDefaultState());
            var hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
            var bucket = (BucketItem) Items.WATER_BUCKET;
            context.assertFalse(bucket.placeFluid(p, context.getWorld(), pos, hit), "Waterlogging denied");
            context.assertFalse(context.getWorld().getBlockState(pos).get(SlabBlock.WATERLOGGED), "Slab unchanged");
            context.assertTrue(bucket.placeFluid(null, context.getWorld(), pos, hit), "Dispenser/null-player route allowed");
            context.assertTrue(context.getWorld().getBlockState(pos).get(SlabBlock.WATERLOGGED), "Automation waterlogs");
            context.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState());
            context.assertFalse(((PowderSnowBucketItem) Items.POWDER_SNOW_BUCKET)
                    .placeFluid(p, context.getWorld(), pos, hit), "Direct powder-snow fluid path denied");
            context.getWorld().setBlockState(pos, Blocks.WATER.getDefaultState());
            aimDown(p, pos);
            p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
            p.interactionManager.interactItem(p, context.getWorld(), p.getMainHandStack(), Hand.MAIN_HAND);
            context.assertTrue(p.getMainHandStack().isOf(Items.WATER_BUCKET), "Empty bucket still collects water");
            context.assertTrue(context.getWorld().getFluidState(pos).isEmpty(), "Source extracted");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_block_placing")
    public void normalInteractionsToolsEntitiesAndGrowth(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player(); var world = context.getWorld();
            var pos = context.getAbsolutePos(new BlockPos(2, 3, 2));
            prepare(context, pos, Items.STONE);
            p.teleport(world, pos.getX() + 3.5, pos.getY(), pos.getZ() + 3.5, 0, 0);
            s.activate(ID);
            for (var block : List.of(Blocks.CRAFTING_TABLE, Blocks.CHEST, Blocks.BARREL)) {
                world.setBlockState(pos, block.getDefaultState());
                p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 4));
                use(p, pos, Hand.MAIN_HAND);
                context.assertTrue(p.currentScreenHandler != p.playerScreenHandler, "Held block does not stop opening " + block);
                p.closeHandledScreen();
                context.assertEquals(4, p.getMainHandStack().getCount(), "Opening costs no held blocks");
            }
            world.setBlockState(pos, Blocks.OAK_DOOR.getDefaultState());
            world.setBlockState(pos.up(), Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.HALF,
                    net.minecraft.block.enums.DoubleBlockHalf.UPPER));
            use(p, pos, Hand.MAIN_HAND);
            context.assertTrue(world.getBlockState(pos).get(DoorBlock.OPEN), "Existing door opens");
            world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
            for (var item : List.of(Items.IRON_HOE, Items.IRON_AXE, Items.IRON_SHOVEL)) {
                var from = item == Items.IRON_AXE ? Blocks.OAK_LOG : Blocks.GRASS_BLOCK;
                var to = item == Items.IRON_AXE ? Blocks.STRIPPED_OAK_LOG : item == Items.IRON_HOE ? Blocks.FARMLAND : Blocks.DIRT_PATH;
                world.setBlockState(pos, from.getDefaultState());
                p.setStackInHand(Hand.MAIN_HAND, new ItemStack(item)); use(p, pos, Hand.MAIN_HAND);
                context.assertTrue(world.getBlockState(pos).isOf(to), "Tool transformation " + item);
            }
            for (var item : List.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)) {
                for (var block : List.of(Blocks.CAMPFIRE, Blocks.CANDLE, Blocks.CANDLE_CAKE)) {
                    world.setBlockState(pos, block.getDefaultState().with(net.minecraft.state.property.Properties.LIT, false));
                    p.setStackInHand(Hand.MAIN_HAND, new ItemStack(item)); use(p, pos, Hand.MAIN_HAND);
                    context.assertTrue(world.getBlockState(pos).get(net.minecraft.state.property.Properties.LIT), "Existing block lights " + block);
                }
            }
            world.setBlockState(pos.down(), Blocks.FARMLAND.getDefaultState());
            world.setBlockState(pos, Blocks.WHEAT.getDefaultState());
            p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BONE_MEAL, 64));
            context.assertTrue(BoneMealItem.useOnFertilizable(p.getMainHandStack(), world, pos), "Bone meal allowed");
            context.assertTrue(world.getBlockState(pos).get(CropBlock.AGE) > 0, "Plant growth allowed");
            prepare(context, pos, Items.STONE); aimDown(p, pos);
            p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_BOAT));
            p.interactionManager.interactItem(p, world, p.getMainHandStack(), Hand.MAIN_HAND);
            var boats = world.getEntitiesByClass(BoatEntity.class, new Box(pos).expand(5), e -> true);
            context.assertEquals(1, boats.size(), "Boat entity still places"); boats.forEach(net.minecraft.entity.Entity::discard);
            world.setBlockState(pos, Blocks.STONE.getDefaultState());
            context.assertTrue(p.interactionManager.tryBreakBlock(pos), "Breaking allowed");
            s.activate("double_challenge:no_block_placing,locked_containers");
            world.setBlockState(pos, Blocks.CHEST.getDefaultState()); use(p, pos, Hand.MAIN_HAND);
            context.assertTrue(p.currentScreenHandler == p.playerScreenHandler, "Other container restriction retained");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_block_placing")
    public void modesLifecyclePersistenceAndDoubleChallenge(TestContext context) throws Exception {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer(); var p = s.player(); var other = s.player();
            s.activate(ID); probe(context, p, false); probe(context, other, false); probe(context, s.player(), false);
            for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.SURVIVAL, GameMode.ADVENTURE)) {
                p.changeGameMode(mode);
                context.assertEquals(mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE,
                        NoBlockPlacingChallenge.blocks(p), "Eligibility " + mode);
                if (mode != GameMode.SPECTATOR) probe(context, p, mode == GameMode.CREATIVE);
            }
            s.activate("test_easy"); probe(context, p, true); // Adventure with real CanPlaceOn.
            s.activate(ID); probe(context, p, false); p.changeGameMode(GameMode.SURVIVAL);
            var c = ChallengeRegistry.get(ID); c.setEnabled(false);
            try { probe(context, p, true); } finally { c.setEnabled(true); }
            s.state.getActiveChallengeIds().clear(); s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID);
            probe(context, p, false); probe(context, other, true);
            s.state.getActiveChallengeIds().add(ID); probe(context, p, false);
            s.state.getPersonalActiveChallengeIds(p.getUuid()).clear();
            p.setHealth(0); context.assertFalse(NoBlockPlacingChallenge.blocks(p), "Dead object excluded");
            p = s.respawn(p, false); probe(context, p, false);
            for (var dimension : List.of(World.NETHER, World.OVERWORLD)) {
                p.teleport(server.getWorld(dimension), .5, 90, .5, 0, 0); probe(context, p, false);
                context.assertFalse(((BucketItem) Items.WATER_BUCKET).placeFluid(p, p.getServerWorld(),
                        p.getBlockPos().east(3), null), "Fluid denied in " + dimension.getValue());
            }
            var manager = server.getOverworld().getPersistentStateManager(); s.state.markDirty(); manager.save();
            var disk = manager.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), p.getRegistryManager());
            context.assertTrue(loaded.getActiveChallengeIds().contains(ID), "Active ID read from disk");
            context.assertTrue(loaded.getChallengeData().isEmpty(), "No inventory/world snapshots");
            manager.set(CurseCycleState.STATE_KEY, loaded); probe(context, p, false);
            var uuid = p.getUuid(); server.getPlayerManager().remove(p);
            var returning = s.player(uuid); probe(context, returning, false);
            CycleManager.activateChallenge(server, loaded, "double_challenge:no_block_placing,no_sprint");
            probe(context, returning, false); returning.setSprinting(true);
            context.assertFalse(returning.isSprinting(), "Double challenge also stops sprint");
            server.getPlayerManager().remove(returning); CycleManager.activateChallenge(server, loaded, "no_sprint");
            returning = s.player(uuid); probe(context, returning, true); returning.setSprinting(true);
            context.assertFalse(returning.isSprinting(), "Offline completion preserves no_sprint");
            var source = server.getCommandSource().withLevel(4);
            server.getCommandManager().getDispatcher().execute("fdc list", source);
            server.getCommandManager().getDispatcher().execute("fdc activate no_block_placing", source);
            server.getCommandManager().getDispatcher().execute("fdc active", source); probe(context, returning, false);
        }
        context.waitAndRun(5, context::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_block_placing_packets")
    public void currentPacketsCannotPlaceOrSpendAndStopRestoresPlacement(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            var pos = context.getAbsolutePos(new BlockPos(2, 3, 2));
            p.teleport(context.getWorld(), pos.getX() + .5, pos.getY(), pos.getZ() + 2.5, 0, 0);
            s.channel(p).runPendingTasks(); s.channel(p).flush();
            Object outbound;
            while ((outbound = s.channel(p).readOutbound()) != null) {
                if (outbound instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket teleport)
                    p.networkHandler.onTeleportConfirm(new net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket(teleport.getTeleportId()));
                io.netty.util.ReferenceCountUtil.release(outbound);
            }
            int sequence = 1;
            for (var item : List.of(Items.STONE, Items.OAK_DOOR, Items.RED_BED, Items.WHEAT_SEEDS,
                    Items.POWDER_SNOW_BUCKET, Items.FLINT_AND_STEEL, Items.FIRE_CHARGE, Items.DANDELION, Items.CANDLE)) {
                for (var hand : Hand.values()) {
                    prepare(context, pos, item);
                    var target = pos.down();
                    if (item == Items.DANDELION || item == Items.CANDLE) {
                        context.getWorld().setBlockState(pos,
                                (item == Items.CANDLE ? Blocks.CAKE : Blocks.FLOWER_POT).getDefaultState());
                        target = pos;
                    }
                    s.activate(ID);
                    var stack = new ItemStack(item, item.getMaxCount() > 1 ? 7 : 1);
                    stack.set(DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("placement-packet"));
                    p.setStackInHand(hand, stack);
                    var original = stack.copy();
                    var before = snapshot(context, pos);
                    var hit = new BlockHitResult(Vec3d.ofCenter(target).add(0, .5, 0), Direction.UP, target, false);
                    int used = p.getStatHandler().getStat(Stats.USED.getOrCreateStat(item));
                    for (int repeat = 0; repeat < 8; repeat++) {
                        p.networkHandler.onPlayerInteractBlock(new net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket(hand, hit, sequence++));
                    }
                    context.assertEquals(before, snapshot(context, pos), "C2S denies world change " + item + hand);
                    context.assertTrue(ItemStack.areEqual(original, p.getStackInHand(hand)), "C2S retains resource/components " + item);
                    context.assertEquals(used, p.getStatHandler().getStat(Stats.USED.getOrCreateStat(item)), "C2S has no success statistic");
                }
            }
            prepare(context, pos, Items.STONE);
            var hit = new BlockHitResult(Vec3d.ofCenter(pos.down()).add(0, .5, 0), Direction.UP, pos.down(), false);
            s.activate("no_sprint");
            p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 7));
            p.networkHandler.onPlayerInteractBlock(new net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, sequence));
            context.assertTrue(context.getWorld().getBlockState(pos).isOf(Blocks.STONE), "Same C2S path places after end");
            context.assertEquals(6, p.getMainHandStack().getCount(), "Vanilla spends exactly one after end");
            p.setSprinting(true);
            context.assertFalse(p.isSprinting(), "Other rule remains after end");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_block_placing_disk")
    public void repeatActivationAndActualPlayerdataDoNotRestoreOldState(TestContext context) throws Exception {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            var server = p.getServer();
            for (int i = 0; i < 32; i++) {
                long serial = s.state.getActivationSerial();
                s.activate("double_challenge:no_block_placing,no_block_placing");
                context.assertTrue(s.state.getActivationSerial() > serial, "Repeat activation advances serial");
                context.assertEquals(List.of(ID), s.state.getActiveChallengeIds(), "Repeated ID deduplicated");
                probe(context, p, false);
                var challenge = ChallengeRegistry.get(ID);
                challenge.setEnabled(false);
                try { probe(context, p, true); } finally { challenge.setEnabled(true); }
                probe(context, p, false);
                s.activate("no_sprint");
                probe(context, p, true);
            }
            s.activate(ID);
            p.getInventory().clear();
            var named = new ItemStack(Items.DIAMOND, 17);
            named.set(DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("foreign-resource"));
            p.getInventory().setStack(8, named.copy());
            p.experienceLevel = 7; p.experienceProgress = .625F; p.setHealth(17);
            var uuid = p.getUuid();
            disconnect(p);
            context.assertTrue(server.getPlayerManager().getPlayerList().isEmpty(), "Save without online players");
            var loaded = reloadState(context);
            p = s.player(uuid);
            assertForeignState(context, p, named); probe(context, p, false);
            disconnect(p);
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            loaded = reloadState(context);
            p = s.player(uuid);
            assertForeignState(context, p, named); probe(context, p, true);
            p.setSprinting(true); context.assertFalse(p.isSprinting(), "Offline end keeps no_sprint");
            disconnect(p);
            CycleManager.activateChallenge(server, loaded, ID);
            loaded = reloadState(context);
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            reloadState(context);
            p = s.player(uuid);
            assertForeignState(context, p, named); probe(context, p, true);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_block_placing")
    public void existingContentsEntitiesAndActualDispenserRemainAllowed(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player(); var world = context.getWorld();
            var pos = context.getAbsolutePos(new BlockPos(2, 3, 2));
            prepare(context, pos, Items.STONE);
            p.teleport(world, pos.getX() + 3.5, pos.getY(), pos.getZ() + 3.5, 0, 0);
            s.activate(ID);
            world.setBlockState(pos, Blocks.POTTED_DANDELION.getDefaultState());
            p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            use(p, pos, Hand.MAIN_HAND);
            context.assertTrue(world.getBlockState(pos).isOf(Blocks.FLOWER_POT), "Plant removal allowed");
            context.assertTrue(p.getInventory().contains(new ItemStack(Items.DANDELION)), "Removed plant retained");
            world.setBlockState(pos, Blocks.CAKE.getDefaultState());
            p.getHungerManager().setFoodLevel(10);
            p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            use(p, pos, Hand.MAIN_HAND);
            context.assertEquals(1, world.getBlockState(pos).get(CakeBlock.BITES), "Existing cake can be eaten");
            for (var item : List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.POWDER_SNOW_BUCKET)) {
                world.setBlockState(pos, Blocks.CAULDRON.getDefaultState());
                p.setStackInHand(Hand.MAIN_HAND, new ItemStack(item));
                use(p, pos, Hand.MAIN_HAND);
                context.assertFalse(world.getBlockState(pos).isOf(Blocks.CAULDRON), "Existing cauldron fills " + item);
                context.assertTrue(p.getMainHandStack().isOf(Items.BUCKET), "Cauldron interaction has vanilla cost");
            }
            prepare(context, pos, Items.STONE);
            p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.ARMOR_STAND));
            use(p, pos.down(), Hand.MAIN_HAND);
            var stands = world.getEntitiesByClass(net.minecraft.entity.decoration.ArmorStandEntity.class, new Box(pos).expand(5), e -> true);
            context.assertEquals(1, stands.size(), "Armor stand entity allowed");
            stands.forEach(net.minecraft.entity.Entity::discard);
            var dispenser = pos.west();
            var old = world.getBlockState(dispenser);
            try {
                world.setBlockState(dispenser, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.EAST));
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
                var blockEntity = (net.minecraft.block.entity.DispenserBlockEntity) world.getBlockEntity(dispenser);
                var pointer = new net.minecraft.util.math.BlockPointer(world, dispenser, world.getBlockState(dispenser), blockEntity);
                var result = DispenserBlock.BEHAVIORS.get(Items.WATER_BUCKET).dispense(pointer, new ItemStack(Items.WATER_BUCKET));
                context.assertTrue(world.getBlockState(pos).isOf(Blocks.WATER), "Real dispenser behavior places water");
                context.assertTrue(result.isOf(Items.BUCKET), "Automation consumes filled bucket normally");
            } finally { world.setBlockState(dispenser, old); world.setBlockState(pos, Blocks.AIR.getDefaultState()); }
            s.activate("double_challenge:no_block_placing,locked_containers");
            probe(context, p, false);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState()); use(p, pos, Hand.MAIN_HAND);
            context.assertTrue(p.currentScreenHandler == p.playerScreenHandler, "Pair denies chest");
            s.activate("locked_containers"); probe(context, p, true);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState()); use(p, pos, Hand.MAIN_HAND);
            context.assertTrue(p.currentScreenHandler == p.playerScreenHandler, "End placement rule keeps container rule");
        }
        context.complete();
    }

    private static void assertForeignState(TestContext c, ServerPlayerEntity p, ItemStack named) {
        c.assertTrue(ItemStack.areEqual(named, p.getInventory().getStack(8)), "Actual playerdata retains named foreign resources");
        c.assertEquals(7, p.experienceLevel, "Foreign XP level retained");
        c.assertEquals(.625F, p.experienceProgress, "Foreign XP progress retained");
        c.assertEquals(17F, p.getHealth(), "Foreign HP retained");
    }
    private static CurseCycleState reloadState(TestContext c) throws Exception {
        var manager = c.getWorld().getServer().getOverworld().getPersistentStateManager();
        CurseCycleState.getServerState(c.getWorld().getServer()).markDirty(); manager.save();
        var disk = manager.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
        var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager());
        c.assertFalse(loaded.getChallengeData().contains(ID), "No own inventory/world snapshot persisted");
        manager.set(CurseCycleState.STATE_KEY, loaded); return loaded;
    }
    private static void disconnect(ServerPlayerEntity p) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                .onPlayDisconnect(p.networkHandler, p.getServer());
        p.getServer().getPlayerManager().remove(p);
    }

    private static void probe(TestContext context, ServerPlayerEntity p, boolean allowed) {
        var world = p.getServerWorld(); var pos = p.getBlockPos().east(3);
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState()); world.setBlockState(pos, Blocks.AIR.getDefaultState());
        var stack = new ItemStack(Items.STONE, 4);
        stack.set(DataComponentTypes.CAN_PLACE_ON, new BlockPredicatesChecker(List.of(
                BlockPredicate.Builder.create().blocks(Blocks.STONE).build()), true));
        p.setStackInHand(Hand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3d.ofCenter(pos.down()).add(0, .5, 0), Direction.UP, pos.down(), false);
        stack.useOnBlock(new ItemUsageContext(p, Hand.MAIN_HAND, hit));
        context.assertEquals(allowed, world.getBlockState(pos).isOf(Blocks.STONE), "Live eligibility governs real CanPlaceOn placement");
        world.setBlockState(pos, Blocks.AIR.getDefaultState()); world.setBlockState(pos.down(), Blocks.AIR.getDefaultState());
    }
    private static void aimDown(ServerPlayerEntity p, BlockPos pos) {
        p.teleport(p.getServerWorld(), pos.getX() + .5, pos.getY() + 2, pos.getZ() + .5, 0, 90);
    }
    private static void use(ServerPlayerEntity p, BlockPos pos, Hand hand) {
        var hit = new BlockHitResult(Vec3d.ofCenter(pos).add(0, .5, 0), Direction.UP, pos, false);
        p.interactionManager.interactBlock(p, p.getServerWorld(), p.getStackInHand(hand), hand, hit);
    }
    private static void prepare(TestContext context, BlockPos pos, Item item) {
        var floor = item == Items.WHEAT_SEEDS ? Blocks.FARMLAND : item == Items.OAK_SAPLING ? Blocks.DIRT : Blocks.STONE;
        for (var at : BlockPos.iterate(pos.add(-1, -1, -1), pos.add(2, 2, 2)))
            context.getWorld().setBlockState(at, (at.getY() == pos.getY() - 1 ? floor : Blocks.AIR).getDefaultState(), Block.NOTIFY_LISTENERS);
    }
    private static List<BlockState> snapshot(TestContext context, BlockPos pos) {
        var states = new ArrayList<BlockState>();
        for (var at : BlockPos.iterate(pos.add(-1, -1, -1), pos.add(2, 2, 2))) states.add(context.getWorld().getBlockState(at));
        return states;
    }
}
