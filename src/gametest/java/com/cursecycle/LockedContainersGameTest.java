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
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.EnderChestInventory;
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

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "locked_containers_balance")
    public void repeatedDenialsBalanceOpenCloseAndPreserveOtherViewer(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            var other = s.player();
            var world = context.getWorld();
            var pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
            var original = world.getBlockState(pos);
            var counted = new EnderChestInventory() {
                int opens, closes;
                @Override public void onOpen(PlayerEntity player) { super.onOpen(player); opens++; }
                @Override public void onClose(PlayerEntity player) { super.onClose(player); closes++; }
            };
            var factory = new SimpleNamedScreenHandlerFactory((id, inventory, player) ->
                    GenericContainerScreenHandler.createGeneric9x3(id, inventory, counted), Text.literal("balance"));
            try {
                world.setBlockState(pos, Blocks.CHEST.getDefaultState());
                other.openHandledScreen((NamedScreenHandlerFactory) world.getBlockEntity(pos));
                context.assertEquals(1, ChestBlockEntity.getPlayersLookingInChestCount(world, pos), "Control viewer is counted");
                // PERSONAL activation must not close another player's allowed screen.
                s.state.getPersonalActiveChallengeIds(p.getUuid()).add(LockedContainersChallenge.ID);
                var c = ChallengeRegistry.get(LockedContainersChallenge.ID);
                var otherScreen = other.currentScreenHandler;
                drain(s.channel(p));
                for (int i = 0; i < 32; i++) {
                    context.assertFalse(p.openHandledScreen(factory).isPresent(), "Repeated denial returns no sync id");
                    context.assertEquals(i + 1, counted.opens, "Constructor opens exactly once");
                    context.assertEquals(counted.opens, counted.closes, "Denied handler closes exactly once");
                    p.openHandledScreen((NamedScreenHandlerFactory) world.getBlockEntity(pos));
                    context.assertEquals(1, ChestBlockEntity.getPlayersLookingInChestCount(world, pos), "Denied viewer does not leak or subtract another viewer");
                    c.onStart(p.getServer(), p);
                    c.onStart(p.getServer(), p);
                    c.onTick(p.getServer(), p);
                    context.assertTrue(other.currentScreenHandler == otherScreen, "Personal denial preserves other screen");
                }
                context.assertEquals(0, drain(s.channel(p)), "Denied handlers emit no OpenScreen packets");
                other.closeHandledScreen();
                context.assertEquals(0, ChestBlockEntity.getPlayersLookingInChestCount(world, pos), "Final real viewer closes normally");
                s.state.getPersonalActiveChallengeIds(p.getUuid()).clear();
                context.assertTrue(p.openHandledScreen(factory).isPresent(), "End restores vanilla opening");
                context.assertEquals(counted.closes + 1, counted.opens, "Allowed screen stays open");
                p.closeHandledScreen();
                context.assertEquals(counted.opens, counted.closes, "Allowed close also balances");
            } finally {
                p.closeHandledScreen();
                other.closeHandledScreen();
                world.setBlockState(pos, original);
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "locked_containers_packets")
    public void currentScreenPacketsAndStaleScreenCannotAffectAllowedCursor(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            var world = context.getWorld();
            var pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
            var barrelPos = pos.east();
            var original = world.getBlockState(pos);
            var originalBarrel = world.getBlockState(barrelPos);
            try {
                p.teleport(world, pos.getX() + .5, pos.getY(), pos.getZ() + 2, 0, 0);
                world.setBlockState(pos, Blocks.CHEST.getDefaultState());
                world.setBlockState(barrelPos, Blocks.BARREL.getDefaultState());
                var chest = (Inventory) world.getBlockEntity(pos);
                var barrel = (Inventory) world.getBlockEntity(barrelPos);
                var named = new ItemStack(Items.DIAMOND, 23);
                named.set(DataComponentTypes.CUSTOM_NAME, Text.literal("cursor-owned"));
                for (var action : SlotActionType.values()) {
                    for (int button : List.of(0, 1, 8, 40)) {
                        s.activate("test_easy");
                        p.getInventory().clear();
                        chest.setStack(0, new ItemStack(Items.EMERALD, 9));
                        chest.setStack(1, named.copy());
                        use(p, pos, Hand.MAIN_HAND);
                        var old = p.currentScreenHandler;
                        old.onSlotClick(1, 0, SlotActionType.PICKUP, p);
                        // Current sync id and real server packet route, before onStart/onTick.
                        s.state.getActiveChallengeIds().add(LockedContainersChallenge.ID);
                        var forgedChanges = new Int2ObjectOpenHashMap<ItemStack>();
                        forgedChanges.put(0, new ItemStack(Items.NETHERITE_BLOCK, 64));
                        p.networkHandler.onClickSlot(new ClickSlotC2SPacket(old.syncId, old.getRevision(),
                                0, button, action, new ItemStack(Items.NETHERITE_BLOCK, 64), forgedChanges));
                        context.assertTrue(p.currentScreenHandler == p.playerScreenHandler, "Current packet closes denied screen before operation");
                        context.assertEquals(9, chest.getStack(0).getCount(), "Current packet cannot move storage items");
                        context.assertEquals(23, p.getInventory().count(Items.DIAMOND), "Packet close returns cursor exactly once");
                        var returned = p.getInventory().getStack(p.getInventory().getSlotWithStack(named));
                        context.assertTrue(ItemStack.areEqual(named, returned), "Cursor components and count survive close");
                        context.assertEquals(0, p.getInventory().count(Items.NETHERITE_BLOCK), "Untrusted packet claims do not become items");
                        barrel.setStack(0, new ItemStack(Items.GOLD_INGOT, 5));
                        use(p, barrelPos, Hand.MAIN_HAND);
                        var allowed = p.currentScreenHandler;
                        allowed.onSlotClick(0, 0, SlotActionType.PICKUP, p);
                        old.onSlotClick(0, button, action, p);
                        p.networkHandler.onClickSlot(new ClickSlotC2SPacket(old.syncId, 0, 0, button,
                                action, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
                        context.assertTrue(p.currentScreenHandler == allowed, "Stale forbidden screen does not close allowed screen");
                        context.assertEquals(5, allowed.getCursorStack().getCount(), "Stale action preserves new cursor");
                        context.assertTrue(allowed.getCursorStack().isOf(Items.GOLD_INGOT), "New cursor keeps its item");
                        context.assertEquals(9, chest.getStack(0).getCount(), "Stale action preserves old storage");
                        p.closeHandledScreen();
                        context.assertEquals(5, p.getInventory().count(Items.GOLD_INGOT), "Allowed close returns only its own cursor");
                    }
                }
            } finally {
                p.closeHandledScreen();
                world.setBlockState(pos, original);
                world.setBlockState(barrelPos, originalBarrel);
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "locked_containers_disk")
    public void repeatedActivationsAndActualPlayerdataKeepCurrentRules(TestContext context) throws Exception {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            var server = p.getServer();
            for (int i = 0; i < 32; i++) {
                long previous = s.state.getActivationSerial();
                s.activate("double_challenge:locked_containers,locked_containers");
                context.assertTrue(s.state.getActivationSerial() > previous, "Repeat activation advances serial");
                context.assertEquals(List.of(LockedContainersChallenge.ID), s.state.getActiveChallengeIds(), "Same ID is not multiplied");
                openProbe(context, p, false);
                var c = ChallengeRegistry.get(LockedContainersChallenge.ID);
                c.setEnabled(false);
                try { openProbe(context, p, true); } finally { c.setEnabled(true); }
                openProbe(context, p, false);
                s.activate("no_sprint");
                openProbe(context, p, true);
                p.setSprinting(true);
                context.assertFalse(p.isSprinting(), "Replacing container rule preserves other mechanic");
            }
            s.activate(LockedContainersChallenge.ID);
            p.getInventory().clear();
            var named = new ItemStack(Items.BLUE_SHULKER_BOX);
            named.set(DataComponentTypes.CUSTOM_NAME, Text.literal("foreign-shulker"));
            p.getInventory().setStack(0, named.copy());
            p.getEnderChestInventory().setStack(3, new ItemStack(Items.DIAMOND, 17));
            p.experienceLevel = 7;
            p.experienceProgress = .625F;
            p.setHealth(17);
            var uuid = p.getUuid();
            disconnect(p);
            context.assertTrue(server.getPlayerManager().getPlayerList().isEmpty(), "Saved with no online players");
            var loaded = reloadState(context);
            p = s.player(uuid);
            assertPlayerdata(context, p, named);
            openProbe(context, p, false);
            disconnect(p);
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            loaded = reloadState(context);
            p = s.player(uuid);
            assertPlayerdata(context, p, named);
            openProbe(context, p, true);
            p.setSprinting(true);
            context.assertFalse(p.isSprinting(), "Offline completion preserves no sprint");
            disconnect(p);
            CycleManager.activateChallenge(server, loaded, LockedContainersChallenge.ID);
            loaded = reloadState(context);
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            reloadState(context);
            p = s.player(uuid);
            assertPlayerdata(context, p, named);
            openProbe(context, p, true);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "locked_containers_shuffle", tickLimit = 240)
    public void shufflePairPreservesAllowedScreenAndCursorAfterEnd(TestContext context) {
        var s = new ChallengeScenario(context);
        var p = s.player();
        var world = context.getWorld();
        var pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
        var original = world.getBlockState(pos);
        Runnable cleanup = () -> {
            p.closeHandledScreen();
            disconnect(p);
            world.setBlockState(pos, original);
            s.close();
        };
        java.util.function.Consumer<Runnable> checked = action -> {
            try { action.run(); } catch (Throwable failure) { cleanup.run(); throw failure; }
        };
        checked.accept(() -> {
            p.teleport(world, pos.getX() + .5, pos.getY(), pos.getZ() + 2, 0, 0);
            world.setBlockState(pos, Blocks.BARREL.getDefaultState());
            p.getInventory().clear();
            for (int i = 0; i < 9; i++) {
                var stack = new ItemStack(Items.STONE, i + 1);
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("hotbar-" + i));
                p.getInventory().setStack(i, stack);
            }
            p.getInventory().selectedSlot = 3;
            var initial = hotbar(p);
            var cursor = new ItemStack(Items.GOLD_INGOT, 13);
            cursor.set(DataComponentTypes.CUSTOM_NAME, Text.literal("pair-cursor"));
            ((Inventory) world.getBlockEntity(pos)).setStack(0, cursor.copy());
            s.activate("double_challenge:locked_containers,hotbar_shuffle");
            openProbe(context, p, false);
            use(p, pos, Hand.MAIN_HAND);
            var allowed = p.currentScreenHandler;
            allowed.onSlotClick(0, 0, SlotActionType.PICKUP, p);
            context.waitAndRun(101, () -> checked.accept(() -> {
                context.assertTrue(p.currentScreenHandler == allowed, "Shuffle pair keeps allowed barrel open");
                context.assertTrue(ItemStack.areEqual(cursor, allowed.getCursorStack()), "Shuffle pair preserves cursor components");
                context.assertFalse(initial.equals(hotbar(p)), "Other mechanic actually shuffled during pair");
                context.assertEquals(45, p.getInventory().count(Items.STONE), "Pair retains every hotbar item");
                context.assertEquals(3, p.getInventory().selectedSlot, "Pair retains selected index");
                s.activate("hotbar_shuffle");
                context.assertTrue(p.currentScreenHandler == allowed, "Ending only container rule leaves allowed screen open");
                p.closeHandledScreen();
                openProbe(context, p, true);
                var afterEnd = hotbar(p);
                context.waitAndRun(101, () -> checked.accept(() -> {
                    context.assertFalse(afterEnd.equals(hotbar(p)), "Shuffle still runs after container rule ends");
                    context.assertEquals(45, p.getInventory().count(Items.STONE), "No item loss after end");
                    context.assertEquals(13, p.getInventory().count(Items.GOLD_INGOT), "Cursor returned once after end");
                    cleanup.run();
                    context.complete();
                }));
            }));
        });
    }

    private static List<String> hotbar(ServerPlayerEntity p) {
        var result = new ArrayList<String>();
        for (int i = 0; i < 9; i++) result.add(p.getInventory().getStack(i).toString());
        return result;
    }

    private static void assertPlayerdata(TestContext c, ServerPlayerEntity p, ItemStack named) {
        c.assertTrue(ItemStack.areEqual(named, p.getInventory().getStack(0)), "Real playerdata retains foreign item and components");
        c.assertEquals(17, p.getEnderChestInventory().getStack(3).getCount(), "Real playerdata retains ender contents");
        c.assertEquals(7, p.experienceLevel, "Foreign XP retained");
        c.assertEquals(.625F, p.experienceProgress, "Foreign XP progress retained");
        c.assertEquals(17F, p.getHealth(), "Foreign HP retained");
    }

    private static CurseCycleState reloadState(TestContext c) throws Exception {
        var manager = c.getWorld().getServer().getOverworld().getPersistentStateManager();
        CurseCycleState.getServerState(c.getWorld().getServer()).markDirty();
        manager.save();
        var disk = manager.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
        var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager());
        c.assertFalse(loaded.getChallengeData().contains(LockedContainersChallenge.ID), "No private storage snapshot or cache persisted");
        manager.set(CurseCycleState.STATE_KEY, loaded);
        return loaded;
    }

    private static void disconnect(ServerPlayerEntity p) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                .onPlayDisconnect(p.networkHandler, p.getServer());
        p.getServer().getPlayerManager().remove(p);
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
