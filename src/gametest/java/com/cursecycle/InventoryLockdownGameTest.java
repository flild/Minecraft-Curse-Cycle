package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.InventoryLockdownChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InventoryLockdownGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void slotTransfersPickupAndLifecycle(TestContext context) throws Exception {
        var world = context.getWorld();
        var server = world.getServer();
        var manager = server.getPlayerManager();
        var state = CurseCycleState.getServerState(server);
        var oldState = state.writeNbt(new NbtCompound(), world.getRegistryManager());
        boolean keepInventory = world.getGameRules().getBoolean(GameRules.KEEP_INVENTORY);
        var challenge = ChallengeRegistry.get(InventoryLockdownChallenge.ID);
        List<ServerPlayerEntity> players = new ArrayList<>();
        try {
            var player = createPlayer(context, UUID.randomUUID(), players);
            var inv = player.getInventory();
            inv.setStack(9, new ItemStack(Items.STONE, 12));
            inv.setStack(38, new ItemStack(Items.IRON_CHESTPLATE));
            inv.setStack(40, new ItemStack(Items.TORCH, 10));
            var screen = player.playerScreenHandler;
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc list");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc activate inventory_lockdown");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc active");
            context.assertTrue(state.getActiveChallengeIds().contains(InventoryLockdownChallenge.ID), "Command activates registered challenge");
            challenge.onPlayerJoin(server, player);
            challenge.onPlayerJoin(server, player);

            for (SlotActionType action : List.of(SlotActionType.PICKUP, SlotActionType.QUICK_MOVE,
                    SlotActionType.SWAP, SlotActionType.THROW, SlotActionType.CLONE, SlotActionType.PICKUP_ALL)) {
                click(screen, inv, 9, 0, action, player);
                context.assertEquals(12, inv.getStack(9).getCount(), action + " cannot take from locked inventory");
                context.assertTrue(screen.getCursorStack().isEmpty(), action + " leaves cursor empty");
            }
            screen.setCursorStack(new ItemStack(Items.STONE, 3));
            click(screen, inv, 10, 0, SlotActionType.PICKUP, player);
            context.assertTrue(inv.getStack(10).isEmpty(), "Cannot place cursor stack in locked inventory");
            click(screen, inv, 38, 0, SlotActionType.PICKUP, player);
            click(screen, inv, 40, 0, SlotActionType.PICKUP, player);
            context.assertTrue(inv.getStack(38).isOf(Items.IRON_CHESTPLATE), "Armor cannot be removed");
            context.assertEquals(10, inv.getStack(40).getCount(), "Offhand cannot be removed");
            screen.setCursorStack(ItemStack.EMPTY);

            inv.setStack(0, new ItemStack(Items.DIAMOND_SWORD));
            click(screen, inv, 0, 0, SlotActionType.PICKUP, player);
            click(screen, inv, 1, 0, SlotActionType.PICKUP, player);
            context.assertTrue(inv.getStack(0).isEmpty() && inv.getStack(1).isOf(Items.DIAMOND_SWORD), "Hotbar drag works");
            click(screen, inv, 1, 2, SlotActionType.SWAP, player);
            context.assertTrue(inv.getStack(2).isOf(Items.DIAMOND_SWORD), "Hotbar number-key swap works");
            click(screen, inv, 2, 40, SlotActionType.SWAP, player);
            context.assertTrue(inv.getStack(2).isOf(Items.DIAMOND_SWORD), "GUI F swap is blocked");
            inv.selectedSlot = 2;
            player.networkHandler.onPlayerAction(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            context.assertTrue(inv.getStack(2).isOf(Items.DIAMOND_SWORD), "World F swap is blocked before mutation");
            context.assertEquals(10, inv.getStack(40).getCount(), "World F swap preserves offhand");
            click(screen, inv, 2, 1, SlotActionType.THROW, player);
            context.assertTrue(inv.getStack(2).isEmpty(), "Hotbar drop works");

            screen.setCursorStack(new ItemStack(Items.STONE));
            click(screen, inv, 0, 0, SlotActionType.PICKUP_ALL, player);
            context.assertEquals(1, screen.getCursorStack().getCount(), "Double click skips matching locked stacks");
            screen.setCursorStack(new ItemStack(Items.STONE, 8));
            screen.onSlotClick(-999, ScreenHandler.packQuickCraftData(0, 0), SlotActionType.QUICK_CRAFT, player);
            click(screen, inv, 0, ScreenHandler.packQuickCraftData(1, 0), SlotActionType.QUICK_CRAFT, player);
            click(screen, inv, 10, ScreenHandler.packQuickCraftData(1, 0), SlotActionType.QUICK_CRAFT, player);
            click(screen, inv, 1, ScreenHandler.packQuickCraftData(1, 0), SlotActionType.QUICK_CRAFT, player);
            screen.onSlotClick(-999, ScreenHandler.packQuickCraftData(2, 0), SlotActionType.QUICK_CRAFT, player);
            context.assertEquals(4, inv.getStack(0).getCount(), "Drag splits into allowed hotbar slots");
            context.assertEquals(4, inv.getStack(1).getCount(), "Second hotbar drag target works");
            context.assertTrue(inv.getStack(10).isEmpty(), "Drag skips locked slot");
            context.assertTrue(screen.getCursorStack().isEmpty(), "Drag neither loses nor duplicates items");

            inv.selectedSlot = 0;
            inv.setStack(0, new ItemStack(Items.DIAMOND_CHESTPLATE));
            inv.getStack(0).use(world, player, Hand.MAIN_HAND);
            context.assertTrue(inv.getStack(0).isOf(Items.DIAMOND_CHESTPLATE), "Right click cannot equip armor");
            context.assertTrue(inv.getStack(38).isOf(Items.IRON_CHESTPLATE), "Right click preserves equipped armor");
            inv.setStack(38, ItemStack.EMPTY);
            context.assertFalse(new EntityPredicates.Equipable(new ItemStack(Items.IRON_CHESTPLATE)).test(player),
                    "Dispenser cannot select player for a locked armor slot");
            inv.setStack(38, new ItemStack(Items.IRON_CHESTPLATE));
            inv.setStack(0, new ItemStack(Items.DIAMOND_CHESTPLATE));
            click(screen, inv, 0, 0, SlotActionType.QUICK_MOVE, player);
            context.assertTrue(inv.getStack(0).isOf(Items.DIAMOND_CHESTPLATE), "Shift-click cannot equip armor or store it in main inventory");
            inv.swapSlotWithHotbar(9);
            context.assertEquals(12, inv.getStack(9).getCount(), "Pick-block cannot extract a locked stack");

            testContainers(context, player);
            testPickup(context, player);
            testRecipeBook(context, player);

            var late = createPlayer(context, UUID.randomUUID(), players);
            context.assertTrue(InventoryLockdownChallenge.isActiveForPlayer(late), "Late join is immediately restricted");
            for (GameMode mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
                player.changeGameMode(mode);
                context.assertFalse(InventoryLockdownChallenge.isActiveForPlayer(player), mode + " is exempt");
            }
            player.changeGameMode(GameMode.CREATIVE);
            inv.clear();
            inv.setStack(9, new ItemStack(Items.STONE));
            click(screen, inv, 9, 0, SlotActionType.PICKUP, player);
            context.assertTrue(screen.getCursorStack().isOf(Items.STONE), "Creative inventory is interactive");
            screen.setCursorStack(ItemStack.EMPTY);
            player.changeGameMode(GameMode.ADVENTURE);
            context.assertTrue(InventoryLockdownChallenge.isActiveForPlayer(player), "Adventure is affected");
            player.changeGameMode(GameMode.SURVIVAL);

            UUID id = player.getUuid();
            manager.remove(player);
            player = createPlayer(context, id, players);
            context.assertTrue(InventoryLockdownChallenge.isActiveForPlayer(player), "Reconnect retains restriction");
            world.getGameRules().get(GameRules.KEEP_INVENTORY).set(false, server);
            player.getInventory().setStack(9, new ItemStack(Items.DIAMOND));
            player.getInventory().setStack(38, new ItemStack(Items.IRON_CHESTPLATE));
            player.getInventory().setStack(40, new ItemStack(Items.SHIELD));
            player.onDeath(player.getDamageSources().create(DamageTypes.GENERIC_KILL));
            context.assertTrue(player.getInventory().isEmpty(), "Death empties even locked inventory, armor and offhand");
            player = manager.respawnPlayer(player, false, Entity.RemovalReason.KILLED);
            player.networkHandler.player = player;
            players.add(player);
            player.changeGameMode(GameMode.SURVIVAL);
            assertLockedClick(context, player, true, "Respawn object remains restricted");
            for (var dimension : List.of(World.NETHER, World.OVERWORLD)) {
                player.teleport(server.getWorld(dimension), 0.5, 90, 0.5, 0, 0);
                assertLockedClick(context, player, true, "Restriction follows dimension " + dimension);
            }

            var persistence = server.getOverworld().getPersistentStateManager();
            persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    SharedConstants.getGameVersion().getSaveVersion().getId());
            state = CurseCycleState.createFromNbt(saved.getCompound("data"), world.getRegistryManager());
            context.assertTrue(state.getActiveChallengeIds().contains(InventoryLockdownChallenge.ID), "Active ID is saved on disk");
            persistence.set(CurseCycleState.STATE_KEY, state);
            assertLockedClick(context, player, true, "Disk-restored state resumes slot restriction");

            CycleManager.activateChallenge(server, state, "double_challenge:inventory_lockdown,no_sprint");
            player.setSprinting(true);
            context.assertFalse(player.isSprinting(), "Second challenge remains active");
            assertLockedClick(context, player, true, "Double challenge keeps inventory locked");
            state.getActiveChallengeIds().clear();
            state.getPersonalActiveChallengeIds(player.getUuid()).add(InventoryLockdownChallenge.ID);
            assertLockedClick(context, player, true, "Personal ID affects its owner");
            assertLockedClick(context, late, false, "Personal ID does not affect another player");
            challenge.setEnabled(false);
            assertLockedClick(context, player, false, "Disabled challenge does not restrict clicks");
            challenge.setEnabled(true);
            state.getPersonalActiveChallengeIds(player.getUuid()).clear();

            CycleManager.activateChallenge(server, state, InventoryLockdownChallenge.ID);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200));
            CycleManager.activateChallenge(server, state, "test_easy");
            assertLockedClick(context, player, false, "Switching challenge restores normal clicks");
            context.assertTrue(player.hasStatusEffect(StatusEffects.SPEED), "Stopping preserves unrelated effects");
            player.getInventory().clear();
            player.getInventory().selectedSlot = 0;
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_CHESTPLATE));
            player.getStackInHand(Hand.MAIN_HAND).use(player.getWorld(), player, Hand.MAIN_HAND);
            context.assertTrue(player.getInventory().getStack(38).isOf(Items.IRON_CHESTPLATE), "Right-click equipping is restored");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND));
            player.networkHandler.onPlayerAction(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            context.assertTrue(player.getStackInHand(Hand.OFF_HAND).isOf(Items.DIAMOND), "World F swap is restored");
            player.getInventory().clear();
            for (int i = 0; i < 9; i++) player.getInventory().setStack(i, new ItemStack(Items.COBBLESTONE, 64));
            context.assertTrue(player.getInventory().insertStack(new ItemStack(Items.DIAMOND)), "Normal pickup uses main inventory again");
            context.assertTrue(player.getInventory().getStack(9).isOf(Items.DIAMOND), "Main storage is restored");

            CycleManager.activateChallenge(server, state, InventoryLockdownChallenge.ID);
            UUID lateId = late.getUuid();
            manager.remove(late);
            CycleManager.activateChallenge(server, state, "test_easy");
            late = createPlayer(context, lateId, players);
            assertLockedClick(context, late, false, "Ending while offline leaves no stale restriction");
        } finally {
            challenge.setEnabled(true);
            for (var player : players) if (manager.getPlayerList().contains(player)) manager.remove(player);
            world.getGameRules().get(GameRules.KEEP_INVENTORY).set(keepInventory, server);
            server.getOverworld().getPersistentStateManager().set(CurseCycleState.STATE_KEY,
                    CurseCycleState.createFromNbt(oldState, world.getRegistryManager()));
        }
        context.complete();
    }

    private static void testContainers(TestContext context, ServerPlayerEntity player) {
        var inv = player.getInventory();
        inv.clear();
        inv.setStack(9, new ItemStack(Items.STONE, 12));
        var chestInventory = new SimpleInventory(27);
        var chest = GenericContainerScreenHandler.createGeneric9x3(1, inv, chestInventory);
        chestInventory.setStack(0, new ItemStack(Items.STONE, 5));
        chest.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, player);
        context.assertEquals(12, inv.getStack(9).getCount(), "Chest shift-click cannot merge into locked stack");
        context.assertEquals(5, inv.getStack(8).getCount(), "Chest shift-click routes to hotbar");
        click(chest, inv, 9, 0, SlotActionType.QUICK_MOVE, player);
        context.assertEquals(12, inv.getStack(9).getCount(), "Chest screen uses inventory indices, not screen IDs");
        click(chest, inv, 8, 0, SlotActionType.QUICK_MOVE, player);
        context.assertEquals(5, chestInventory.getStack(0).getCount(), "Hotbar shift-click inserts into chest");
        for (int i = 0; i < 9; i++) inv.setStack(i, new ItemStack(Items.COBBLESTONE, 64));
        chest.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, player);
        context.assertEquals(5, chestInventory.getStack(0).getCount(), "Full hotbar leaves external stack in chest");
        context.assertTrue(inv.getStack(10).isEmpty(), "Chest cannot insert into empty locked slot");

        inv.clear();
        var crafting = new CraftingScreenHandler(2, inv, ScreenHandlerContext.create(context.getWorld(),
                context.getAbsolutePos(new BlockPos(1, 1, 1))));
        crafting.getSlot(1).setStack(new ItemStack(Items.OAK_LOG));
        crafting.onContentChanged(crafting.getSlot(1).inventory);
        context.assertTrue(crafting.getSlot(0).getStack().isOf(Items.OAK_PLANKS), "Workbench calculates vanilla crafting result");
        crafting.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, player);
        context.assertEquals(4, inv.getStack(8).getCount(), "Shift-crafted output goes into hotbar");
        context.assertTrue(inv.getStack(9).isEmpty(), "Crafted output cannot enter main inventory");
        crafting.getSlot(1).setStack(new ItemStack(Items.OAK_LOG));
        crafting.onSlotClick(1, 0, SlotActionType.QUICK_MOVE, player);
        context.assertTrue(inv.getStack(0).isOf(Items.OAK_LOG), "Workbench input returns to hotbar");
        var furnace = new FurnaceScreenHandler(3, inv);
        inv.setStack(1, new ItemStack(Items.COAL, 3));
        click(furnace, inv, 1, 0, SlotActionType.QUICK_MOVE, player);
        context.assertEquals(3, furnace.getSlot(1).getStack().getCount(), "Hotbar fuel goes into furnace");
        furnace.getSlot(2).setStack(new ItemStack(Items.IRON_INGOT, 2));
        furnace.onSlotClick(2, 0, SlotActionType.QUICK_MOVE, player);
        context.assertTrue(inv.contains(new ItemStack(Items.IRON_INGOT)), "Furnace output goes into hotbar");

        inv.clear();
        inv.setStack(9, new ItemStack(Items.EMERALD, 20));
        var merchant = new MerchantScreenHandler(4, inv);
        var offers = new TradeOfferList();
        offers.add(new TradeOffer(new TradedItem(Items.EMERALD), new ItemStack(Items.BREAD), 10, 1, 0));
        merchant.setOffers(offers);
        merchant.switchTo(0);
        context.assertTrue(merchant.getSlot(0).getStack().isEmpty(), "Trade autofill cannot extract locked emeralds");
        inv.setStack(0, new ItemStack(Items.EMERALD, 3));
        merchant.switchTo(0);
        context.assertEquals(3, merchant.getSlot(0).getStack().getCount(), "Trade autofill uses hotbar emeralds");
        context.assertEquals(20, inv.getStack(9).getCount(), "Trade preserves locked emeralds");
    }

    private static void testPickup(TestContext context, ServerPlayerEntity player) {
        var inv = player.getInventory();
        inv.clear();
        inv.setStack(9, new ItemStack(Items.DIAMOND, 5));
        inv.setStack(40, new ItemStack(Items.DIAMOND, 5));
        for (int i = 0; i < 9; i++) inv.setStack(i, new ItemStack(Items.COBBLESTONE, 64));
        var drop = new ItemEntity(player.getWorld(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.DIAMOND, 3));
        drop.resetPickupDelay();
        drop.onPlayerCollision(player);
        context.assertEquals(3, drop.getStack().getCount(), "Full hotbar leaves pickup on ground");
        context.assertEquals(5, inv.getStack(9).getCount(), "Pickup cannot merge into locked main stack");
        context.assertEquals(5, inv.getStack(40).getCount(), "Pickup cannot merge into offhand");
        context.assertTrue(inv.getStack(10).isEmpty(), "Pickup cannot use free main slot");
        inv.setStack(2, new ItemStack(Items.DIAMOND, 63));
        drop.onPlayerCollision(player);
        context.assertEquals(64, inv.getStack(2).getCount(), "Pickup fills matching hotbar stack");
        context.assertEquals(2, drop.getStack().getCount(), "Excess pickup remains on ground");
        inv.setStack(1, ItemStack.EMPTY);
        drop.onPlayerCollision(player);
        context.assertEquals(2, inv.getStack(1).getCount(), "Pickup uses empty hotbar slot");
        context.assertTrue(drop.isRemoved(), "Fully collected entity is discarded");
        ItemStack explicit = new ItemStack(Items.GOLD_INGOT);
        context.assertFalse(inv.insertStack(10, explicit), "Explicit locked insertion is refused");
        context.assertEquals(1, explicit.getCount(), "Rejected insertion does not consume item");
    }

    private static void testRecipeBook(TestContext context, ServerPlayerEntity player) {
        var inv = player.getInventory();
        inv.clear();
        inv.setStack(9, new ItemStack(Items.OAK_LOG, 4));
        var recipe = player.getServer().getRecipeManager().get(Identifier.ofVanilla("oak_planks")).orElseThrow();
        player.unlockRecipes(List.of(recipe));
        var matcher = new RecipeMatcher();
        inv.populateRecipeFinder(matcher);
        context.assertFalse(matcher.match(recipe.value(), null), "Recipe matcher excludes locked ingredients");
        player.playerScreenHandler.fillInputSlots(false, recipe, player);
        context.assertEquals(4, inv.getStack(9).getCount(), "Recipe request cannot consume locked ingredients");
        inv.setStack(0, new ItemStack(Items.OAK_LOG, 2));
        player.playerScreenHandler.fillInputSlots(false, recipe, player);
        context.assertEquals(1, player.playerScreenHandler.getSlot(1).getStack().getCount(), "Recipe book fills from hotbar");
        context.assertEquals(4, inv.getStack(9).getCount(), "Recipe book preserves main storage");
        player.playerScreenHandler.clearCraftingSlots();
    }

    private static void assertLockedClick(TestContext context, ServerPlayerEntity player, boolean locked, String message) {
        var inv = player.getInventory();
        var handler = player.playerScreenHandler;
        handler.setCursorStack(ItemStack.EMPTY);
        inv.setStack(9, new ItemStack(Items.STONE));
        click(handler, inv, 9, 0, SlotActionType.PICKUP, player);
        context.assertEquals(locked, !inv.getStack(9).isEmpty(), message);
        handler.setCursorStack(ItemStack.EMPTY);
    }

    private static void click(ScreenHandler handler, PlayerInventory inventory, int inventoryIndex,
                              int button, SlotActionType action, ServerPlayerEntity player) {
        handler.onSlotClick(handler.getSlotIndex(inventory, inventoryIndex).orElseThrow(), button, action, player);
    }

    private static ServerPlayerEntity createPlayer(TestContext context, UUID id, List<ServerPlayerEntity> players) {
        var server = context.getWorld().getServer();
        var client = ConnectedClientData.createDefault(new GameProfile(id, "lock-" + id.toString().substring(0, 8)), false);
        var player = new ServerPlayerEntity(server, context.getWorld(), client.gameProfile(), client.syncedOptions());
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, player, client);
        player.changeGameMode(GameMode.SURVIVAL);
        players.add(player);
        return player;
    }
}
