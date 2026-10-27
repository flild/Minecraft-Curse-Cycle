package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.LockedContainersChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

public class LockedContainersGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "locked_containers")
    public void realBlocksBothHandsAndDoubleHalves(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            var pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
            var world = context.getWorld();
            p.teleport(world, pos.getX() + .5, pos.getY(), pos.getZ() + 2, 0, 0);
            var forbidden = new ArrayList<Block>(List.of(Blocks.CHEST, Blocks.TRAPPED_CHEST,
                    Blocks.ENDER_CHEST, Blocks.SHULKER_BOX));
            for (var color : DyeColor.values()) forbidden.add(ShulkerBoxBlock.get(color));
            p.getEnderChestInventory().setStack(0, new ItemStack(Items.DIAMOND, 7));
            for (var block : forbidden) {
                world.setBlockState(pos, block.getDefaultState());
                s.activate("test_easy");
                use(p, pos, Hand.MAIN_HAND);
                context.assertTrue(p.currentScreenHandler != p.playerScreenHandler, "Control opens " + block);
                p.closeHandledScreen();
                s.activate(LockedContainersChallenge.ID);
                drain(s.channel(p));
                for (var hand : Hand.values()) {
                    use(p, pos, hand);
                    context.assertTrue(p.currentScreenHandler == p.playerScreenHandler, hand + " denied " + block);
                }
                context.assertEquals(0, drain(s.channel(p)), "No preliminary OpenScreen packet for " + block);
            }
            context.assertEquals(7, p.getEnderChestInventory().getStack(0).getCount(), "Ender contents retained");
            for (var block : List.of(Blocks.CHEST, Blocks.TRAPPED_CHEST)) {
                var other = pos.east();
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
                world.setBlockState(other, Blocks.AIR.getDefaultState());
                world.setBlockState(pos, block.getDefaultState().with(ChestBlock.FACING, Direction.NORTH)
                        .with(ChestBlock.CHEST_TYPE, ChestType.LEFT), Block.NOTIFY_LISTENERS);
                world.setBlockState(other, block.getDefaultState().with(ChestBlock.FACING, Direction.NORTH)
                        .with(ChestBlock.CHEST_TYPE, ChestType.RIGHT), Block.NOTIFY_LISTENERS);
                for (var half : List.of(pos, other)) {
                    s.activate("test_easy");
                    use(p, half, Hand.MAIN_HAND);
                    context.assertTrue(p.currentScreenHandler instanceof GenericContainerScreenHandler g
                            && g.getRows() == 6, "Control opens real double chest from either half");
                    p.closeHandledScreen();
                    s.activate(LockedContainersChallenge.ID);
                    for (var hand : Hand.values()) {
                        use(p, half, hand);
                        context.assertTrue(p.currentScreenHandler == p.playerScreenHandler, "Double half denied " + hand);
                    }
                }
                world.setBlockState(other, Blocks.AIR.getDefaultState());
            }
            for (var block : List.of(Blocks.BARREL, Blocks.FURNACE, Blocks.HOPPER,
                    Blocks.DISPENSER, Blocks.DROPPER, Blocks.CRAFTING_TABLE)) {
                world.setBlockState(pos, block.getDefaultState());
                use(p, pos, Hand.MAIN_HAND);
                context.assertTrue(p.currentScreenHandler != p.playerScreenHandler, "Allowed station opens " + block);
                p.closeHandledScreen();
            }
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "locked_containers")
    public void vehiclesAutomationAndAllowedTransfers(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            var world = context.getWorld();
            var pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
            var boat = new ChestBoatEntity(world, pos.getX(), pos.getY(), pos.getZ());
            var cart = new ChestMinecartEntity(world, pos.getX(), pos.getY(), pos.getZ());
            var hopperCart = new HopperMinecartEntity(world, pos.getX(), pos.getY(), pos.getZ());
            try {
                p.setSneaking(true);
                for (var vehicle : List.of(boat, cart, hopperCart)) {
                    s.activate("test_easy");
                    vehicle.interact(p, Hand.MAIN_HAND);
                    context.assertTrue(p.currentScreenHandler != p.playerScreenHandler, "Control opens vehicle");
                    p.closeHandledScreen();
                    s.activate(LockedContainersChallenge.ID);
                    for (var hand : Hand.values()) {
                        vehicle.interact(p, hand);
                        context.assertEquals(vehicle == hopperCart, p.currentScreenHandler != p.playerScreenHandler,
                                "Only chest vehicles denied for " + hand);
                        if (vehicle == hopperCart) p.closeHandledScreen();
                    }
                }
                boat.openInventory(p);
                context.assertTrue(p.currentScreenHandler == p.playerScreenHandler, "Passenger inventory route denied");
                p.setSneaking(false);
                boat.interact(p, Hand.MAIN_HAND);
                context.assertTrue(p.getVehicle() == boat, "Boarding a chest boat remains allowed");
                p.stopRiding();
                world.setBlockState(pos.down(), Blocks.HOPPER.getDefaultState());
                world.setBlockState(pos, Blocks.CHEST.getDefaultState());
                var chest = (Inventory) world.getBlockEntity(pos);
                chest.setStack(0, new ItemStack(Items.STONE, 12));
                var hopper = (HopperBlockEntity) world.getBlockEntity(pos.down());
                context.assertTrue(HopperBlockEntity.extract(world, hopper), "Hopper still extracts from forbidden chest");
                context.assertEquals(11, chest.getStack(0).getCount(), "One item extracted");
                context.assertEquals(1, hopper.getStack(0).getCount(), "No loss during automation");
                context.assertTrue(p.interactionManager.tryBreakBlock(pos), "Breaking chest allowed");
                p.teleport(world, pos.getX() + 3.5, pos.getY(), pos.getZ() + 3.5, 0, 0);
                world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
                world.setBlockState(pos, Blocks.STONE.getDefaultState());
                p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHULKER_BOX));
                var hit = new BlockHitResult(Vec3d.ofCenter(pos).add(0, .5, 0), Direction.UP, pos, false);
                p.getMainHandStack().useOnBlock(new ItemUsageContext(p, Hand.MAIN_HAND, hit));
                context.assertTrue(world.getBlockState(pos.up()).isOf(Blocks.SHULKER_BOX), "Placing shulker allowed");
                p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                p.getInventory().setStack(0, new ItemStack(Items.RED_SHULKER_BOX));
                p.playerScreenHandler.onSlotClick(36, 0, SlotActionType.PICKUP, p);
                p.playerScreenHandler.onSlotClick(37, 0, SlotActionType.PICKUP, p);
                context.assertTrue(p.getInventory().getStack(1).isOf(Items.RED_SHULKER_BOX), "Shulker item transferable");
                world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
                world.setBlockState(pos, Blocks.BARREL.getDefaultState());
                var barrel = (Inventory) world.getBlockEntity(pos);
                barrel.setStack(0, new ItemStack(Items.DIAMOND, 3));
                s.activate("double_challenge:locked_containers,inventory_lockdown");
                use(p, pos, Hand.MAIN_HAND);
                var screen = p.currentScreenHandler;
                screen.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, p);
                context.assertEquals(3, p.getInventory().count(Items.DIAMOND), "Allowed barrel shift transfer works");
                p.getInventory().setStack(9, new ItemStack(Items.EMERALD, 4));
                screen.onSlotClick(27, 0, SlotActionType.PICKUP, p);
                context.assertEquals(4, p.getInventory().getStack(9).getCount(), "Other challenge still locks player slots");
                p.closeHandledScreen();
            } finally {
                boat.discard(); cart.discard(); hopperCart.discard();
                world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
                world.setBlockState(pos.down(), Blocks.AIR.getDefaultState());
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "locked_containers")
    public void activationCursorAndStalePackets(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            var pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
            var world = context.getWorld();
            p.teleport(world, pos.getX() + .5, pos.getY(), pos.getZ() + 1, 0, 0);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState());
            var inv = (Inventory) world.getBlockEntity(pos);
            inv.setStack(0, new ItemStack(Items.DIAMOND, 23));
            use(p, pos, Hand.MAIN_HAND);
            var old = p.currentScreenHandler;
            old.onSlotClick(0, 0, SlotActionType.PICKUP, p);
            s.activate(LockedContainersChallenge.ID);
            context.assertTrue(p.currentScreenHandler == p.playerScreenHandler, "Activation closes existing screen immediately");
            context.assertTrue(old.getCursorStack().isEmpty(), "Cursor cleared using vanilla close");
            context.assertEquals(23, p.getInventory().count(Items.DIAMOND), "Entire cursor stack returned once");
            inv.setStack(0, new ItemStack(Items.EMERALD, 9));
            for (var action : SlotActionType.values()) {
                for (int button : List.of(0, 1, 8, 40)) {
                    old.onSlotClick(0, button, action, p);
                    p.networkHandler.onClickSlot(new ClickSlotC2SPacket(old.syncId, 0, 0, button, action,
                            ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
                }
            }
            context.assertEquals(9, inv.getStack(0).getCount(), "Every stale action leaves storage untouched");
            context.assertEquals(23, p.getInventory().count(Items.DIAMOND), "Repeated packets do not duplicate cursor");
            // Activation applied before its lifecycle notification / tick.
            s.activate("test_easy");
            use(p, pos, Hand.MAIN_HAND);
            var race = p.currentScreenHandler;
            s.state.getActiveChallengeIds().add(LockedContainersChallenge.ID);
            race.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, p);
            context.assertTrue(p.currentScreenHandler == p.playerScreenHandler, "Click before next tick closes forbidden screen");
            context.assertEquals(9, inv.getStack(0).getCount(), "Race cannot shift transfer");
            s.activate("test_easy");
            use(p, pos, Hand.MAIN_HAND);
            var full = p.currentScreenHandler;
            full.onSlotClick(0, 0, SlotActionType.PICKUP, p);
            for (int i = 0; i < 36; i++) p.getInventory().setStack(i, new ItemStack(Items.STONE, 64));
            s.activate(LockedContainersChallenge.ID);
            context.assertTrue(full.getCursorStack().isEmpty(), "Full-inventory cursor resolved");
            int dropped = world.getEntitiesByClass(ItemEntity.class, new Box(p.getBlockPos()).expand(4),
                    e -> e.getStack().isOf(Items.EMERALD)).stream().mapToInt(e -> e.getStack().getCount()).sum();
            context.assertEquals(9, dropped, "Vanilla drops cursor exactly once if inventory is full");
            s.activate("test_easy");
            context.assertTrue(p.currentScreenHandler == p.playerScreenHandler, "Stop never automatically opens screen");
            use(p, pos, Hand.MAIN_HAND);
            context.assertTrue(p.currentScreenHandler != p.playerScreenHandler, "Stop allows reopening");
            p.closeHandledScreen();
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "locked_containers")
    public void eligibilityLifecycleAndDiskState(TestContext context) throws Exception {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            var server = p.getServer();
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc list");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc activate locked_containers");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc active");
            var late = s.player();
            openProbe(context, p, false);
            openProbe(context, late, false);
            for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL)) {
                p.changeGameMode(mode);
                openProbe(context, p, mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR);
            }
            s.state.getActiveChallengeIds().clear();
            s.state.getPersonalActiveChallengeIds(p.getUuid()).add(LockedContainersChallenge.ID);
            openProbe(context, p, false);
            openProbe(context, late, true);
            s.state.getActiveChallengeIds().add(LockedContainersChallenge.ID);
            openProbe(context, p, false);
            s.state.getPersonalActiveChallengeIds(p.getUuid()).clear();
            var c = ChallengeRegistry.get(LockedContainersChallenge.ID);
            c.setEnabled(false);
            try { openProbe(context, p, true); } finally { c.setEnabled(true); }
            p.setHealth(0);
            openProbe(context, p, true);
            p = s.respawn(p, false);
            openProbe(context, p, false);
            for (var dimension : List.of(World.NETHER, World.OVERWORLD)) {
                p.teleport(server.getWorld(dimension), .5, 90, .5, 0, 0);
                openProbe(context, p, false);
            }
            var persistence = server.getOverworld().getPersistentStateManager();
            s.state.markDirty();
            persistence.save();
            var disk = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), p.getRegistryManager());
            context.assertTrue(loaded.getActiveChallengeIds().contains(LockedContainersChallenge.ID), "Active ID saved on disk");
            context.assertTrue(loaded.getChallengeData().isEmpty(), "No storage snapshots persisted");
            persistence.set(CurseCycleState.STATE_KEY, loaded);
            openProbe(context, p, false);
            var uuid = p.getUuid();
            server.getPlayerManager().remove(p);
            var returning = s.player(uuid);
            openProbe(context, returning, false);
            CycleManager.activateChallenge(server, loaded, "double_challenge:locked_containers,no_sprint");
            openProbe(context, returning, false);
            returning.setSprinting(true);
            context.assertFalse(returning.isSprinting(), "Both mechanics active");
            server.getPlayerManager().remove(returning);
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            returning = s.player(uuid);
            openProbe(context, returning, true);
            returning.setSprinting(true);
            context.assertFalse(returning.isSprinting(), "Offline completion preserves other challenge");
        }
        context.waitAndRun(5, context::complete);
    }

    private static void use(ServerPlayerEntity p, BlockPos pos, Hand hand) {
        var hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        p.interactionManager.interactBlock(p, p.getServerWorld(), p.getStackInHand(hand), hand, hit);
    }

    private static void openProbe(TestContext context, ServerPlayerEntity p, boolean allowed) {
        NamedScreenHandlerFactory factory = new SimpleNamedScreenHandlerFactory((id, inventory, player) ->
                GenericContainerScreenHandler.createGeneric9x3(id, inventory, p.getEnderChestInventory()), Text.literal("probe"));
        context.assertEquals(allowed, p.openHandledScreen(factory).isPresent(), "Current player eligibility controls opening");
        context.assertEquals(allowed, p.currentScreenHandler != p.playerScreenHandler, "Screen installed only when allowed");
        if (allowed) p.closeHandledScreen();
    }

    private static int drain(EmbeddedChannel channel) {
        channel.runPendingTasks();
        channel.flush();
        int opens = 0;
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof OpenScreenS2CPacket) opens++;
            ReferenceCountUtil.release(packet);
        }
        return opens;
    }
}
