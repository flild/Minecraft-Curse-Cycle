package com.cursecycle;

import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.CraftingTaxChallenge;
import com.cursecycle.state.CurseCycleState;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

public class CraftingTaxGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "crafting_tax")
    public void individualRoutesAndRepeatedPackets(TestContext context) {
        try (var scenario = new ChallengeScenario(context)) {
            var p = scenario.player();
            var server = p.getServer();
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc list");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc activate crafting_tax");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc active");
            context.assertTrue(ChallengeActivity.active(p, CraftingTaxChallenge.ID), "Registered command activates tax");
            var h = p.playerScreenHandler;
            for (var action : List.of(SlotActionType.PICKUP, SlotActionType.QUICK_MOVE,
                    SlotActionType.SWAP, SlotActionType.THROW, SlotActionType.PICKUP_ALL, SlotActionType.CLONE)) {
                for (int button : List.of(0, 1)) {
                    reset(p, h, 0);
                    p.experienceProgress = 0.9F;
                    logs(h, 2);
                    h.onSlotClick(0, button, action, p);
                    context.assertEquals(2, h.getSlot(1).getStack().getCount(), action + " cannot consume without a full level");
                    context.assertEquals(4, h.getSlot(0).getStack().getCount(), action + " preserves preview");
                    context.assertTrue(h.getCursorStack().isEmpty() && p.getInventory().isEmpty(), action + " gives nothing");
                    context.assertEquals(0, p.experienceLevel, action + " does not charge a failed attempt");
                    context.assertEquals(0.9F, p.experienceProgress, "Partial XP bar is preserved");
                }
            }
            for (int button : List.of(0, 1)) {
                reset(p, h, 1);
                p.experienceProgress = 0.375F;
                p.totalExperience = 100;
                logs(h, 1);
                context.assertEquals(1, p.experienceLevel, "Preview is free");
                h.onSlotClick(0, button, SlotActionType.PICKUP, p);
                context.assertEquals(4, h.getCursorStack().getCount(), "Either mouse button takes one whole recipe output");
                context.assertTrue(h.getSlot(1).getStack().isEmpty(), "One ingredient set consumed");
                context.assertEquals(0, p.experienceLevel, "Four planks cost one level");
                context.assertEquals(0.375F, p.experienceProgress, "Vanilla level payment retains fractional progress");
                context.assertEquals(100, p.totalExperience, "Vanilla addExperienceLevels does not subtract fixed XP points");
                h.onSlotClick(0, button, SlotActionType.PICKUP, p);
                context.assertEquals(4, h.getCursorStack().getCount(), "Empty result cannot charge or duplicate");
            }
            for (int button : List.of(0, 8, 40)) {
                reset(p, h, 2);
                logs(h, 1);
                h.onSlotClick(0, button, SlotActionType.SWAP, p);
                context.assertEquals(4, p.getInventory().getStack(button).getCount(), "Number/F swap receives output");
                context.assertEquals(1, p.experienceLevel, "Swap pays once");
                context.assertTrue(h.getSlot(1).getStack().isEmpty(), "Swap consumes once");
            }
            for (int button : List.of(0, 1)) {
                reset(p, h, 2);
                logs(h, 1);
                h.onSlotClick(0, button, SlotActionType.THROW, p);
                context.assertTrue(h.getSlot(1).getStack().isEmpty(), "Throw consumes one recipe");
                context.assertEquals(1, p.experienceLevel, "Throw pays once regardless of requested item count");
            }
            reset(p, h, 3);
            logs(h, 1);
            var packet = new ClickSlotC2SPacket(h.syncId, h.getRevision(), 0, 0, SlotActionType.PICKUP,
                    ItemStack.EMPTY, new Int2ObjectOpenHashMap<>());
            p.networkHandler.onClickSlot(packet);
            p.networkHandler.onClickSlot(packet);
            context.assertEquals(4, h.getCursorStack().getCount(), "Repeated stale packet cannot duplicate a consumed recipe");
            context.assertEquals(2, p.experienceLevel, "Repeated packet cannot double-charge");
            reset(p, h, 3);
            logs(h, 1);
            h.setCursorStack(new ItemStack(Items.OAK_PLANKS, 62));
            h.onSlotClick(0, 0, SlotActionType.PICKUP, p);
            context.assertEquals(62, h.getCursorStack().getCount(), "Partial cursor capacity cannot take part of a recipe");
            context.assertEquals(1, h.getSlot(1).getStack().getCount(), "Failed partial take preserves input");
            context.assertEquals(3, p.experienceLevel, "Failed partial take is free");
            h.setCursorStack(new ItemStack(Items.OAK_PLANKS, 60));
            h.onSlotClick(0, 0, SlotActionType.PICKUP, p);
            context.assertEquals(64, h.getCursorStack().getCount(), "Exact cursor capacity consumes complete result");
            context.assertEquals(2, p.experienceLevel, "Exact cursor fit pays once");
            reset(p, h, 3);
            logs(h, 1);
            p.getInventory().setStack(0, new ItemStack(Items.STONE));
            h.onSlotClick(0, 0, SlotActionType.SWAP, p);
            context.assertTrue(p.getInventory().getStack(0).isOf(Items.STONE), "Incompatible occupied swap is rejected by vanilla");
            context.assertEquals(3, p.experienceLevel, "Rejected swap is free");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "crafting_tax")
    public void bulkCapacityRecipeBookAndRemainders(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            s.activate(CraftingTaxChallenge.ID);
            var workbench = new CraftingScreenHandler(8, p.getInventory(),
                    ScreenHandlerContext.create(context.getWorld(), context.getAbsolutePos(net.minecraft.util.math.BlockPos.ORIGIN)));
            for (ScreenHandler h : List.of(p.playerScreenHandler, workbench)) {
                reset(p, h, 2);
                logs(h, 3);
                h.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, p);
                context.assertEquals(8, count(p, Items.OAK_PLANKS), "Bulk craft limited to two affordable executions");
                context.assertEquals(1, h.getSlot(1).getStack().getCount(), "Unaffordable third ingredient set remains");
                context.assertEquals(0, p.experienceLevel, "Bulk craft charges each recipe, not each item or click");

                reset(p, h, 5);
                fill(p);
                logs(h, 3);
                h.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, p);
                context.assertEquals(3, h.getSlot(1).getStack().getCount(), "Full inventory leaves input untouched");
                context.assertEquals(5, p.experienceLevel, "Full inventory is free");
                p.getInventory().setStack(8, new ItemStack(Items.OAK_PLANKS, 62));
                h.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, p);
                context.assertEquals(62, count(p, Items.OAK_PLANKS), "Two free spaces cannot consume a four-item recipe");
                context.assertEquals(5, p.experienceLevel, "Partial fit is free without dropping overflow");
                p.getInventory().setStack(8, new ItemStack(Items.OAK_PLANKS, 60));
                h.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, p);
                context.assertEquals(64, count(p, Items.OAK_PLANKS), "Mass craft stops at exact remaining capacity");
                context.assertEquals(2, h.getSlot(1).getStack().getCount(), "Only one full execution fits");
                context.assertEquals(4, p.experienceLevel, "One fitting execution costs one level");

                reset(p, h, 5);
                fill(p);
                p.getInventory().setStack(0, new ItemStack(Items.OAK_PLANKS, 62));
                p.getInventory().setStack(8, new ItemStack(Items.OAK_PLANKS, 62));
                logs(h, 2);
                h.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, p);
                context.assertEquals(128, count(p, Items.OAK_PLANKS), "One result can fit across two partial stacks");
                context.assertEquals(4, p.experienceLevel, "Split insertion still pays once");
            }
            reset(p, workbench, 2);
            // Cake is a real shaped recipe with three returned milk buckets.
            for (int i = 1; i <= 3; i++) workbench.getSlot(i).setStack(new ItemStack(Items.MILK_BUCKET));
            workbench.getSlot(4).setStack(new ItemStack(Items.SUGAR));
            workbench.getSlot(5).setStack(new ItemStack(Items.EGG));
            workbench.getSlot(6).setStack(new ItemStack(Items.SUGAR));
            for (int i = 7; i <= 9; i++) workbench.getSlot(i).setStack(new ItemStack(Items.WHEAT));
            context.assertTrue(workbench.getSlot(0).getStack().isOf(Items.CAKE), "Vanilla cake recipe resolves");
            fill(p);
            workbench.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, p);
            context.assertTrue(workbench.getSlot(1).getStack().isOf(Items.MILK_BUCKET), "Failed craft does not consume containers");
            context.assertEquals(2, p.experienceLevel, "Failed cake is free");
            p.getInventory().setStack(8, ItemStack.EMPTY);
            workbench.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, p);
            context.assertTrue(p.getInventory().getStack(8).isOf(Items.CAKE), "Successful cake delivered");
            for (int i = 1; i <= 3; i++) context.assertTrue(workbench.getSlot(i).getStack().isOf(Items.BUCKET), "Vanilla bucket remainder retained");
            context.assertEquals(1, p.experienceLevel, "Cake and its three remainders pay once");

            reset(p, p.playerScreenHandler, 0);
            p.getInventory().setStack(0, new ItemStack(Items.OAK_LOG, 2));
            var recipe = p.getWorld().getRecipeManager().get(Identifier.ofVanilla("oak_planks")).orElseThrow();
            p.unlockRecipes(List.of(recipe));
            p.playerScreenHandler.fillInputSlots(false, recipe, p);
            context.assertTrue(p.playerScreenHandler.getSlot(0).getStack().isOf(Items.OAK_PLANKS), "Recipe book fills without XP");
            context.assertEquals(0, p.experienceLevel, "Recipe book does not charge");
            var furnace = new FurnaceScreenHandler(9, p.getInventory());
            furnace.getSlot(2).setStack(new ItemStack(Items.IRON_INGOT, 2));
            furnace.onSlotClick(2, 0, SlotActionType.PICKUP, p);
            context.assertEquals(2, furnace.getCursorStack().getCount(), "Furnace output remains available at level zero");
            context.assertEquals(0, p.experienceLevel, "No additional furnace tax");

            s.activate("double_challenge:crafting_tax,inventory_lockdown");
            reset(p, workbench, 3);
            for (int i = 0; i < 9; i++) p.getInventory().setStack(i, new ItemStack(Items.STONE, 64));
            logs(workbench, 2);
            workbench.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, p);
            context.assertEquals(3, p.experienceLevel, "Locked main inventory cannot supply output capacity");
            p.getInventory().setStack(8, ItemStack.EMPTY);
            workbench.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, p);
            context.assertEquals(8, p.getInventory().getStack(8).getCount(), "Tax respects hotbar-only shift insertion");
            context.assertTrue(p.getInventory().getStack(9).isEmpty(), "Locked inventory is untouched");
            context.assertEquals(1, p.experienceLevel, "Lockdown pair pays for two executions");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "crafting_tax")
    public void activityLifecycleAndDiskState(TestContext context) throws Exception {
        try (var s = new ChallengeScenario(context)) {
            var p = s.player();
            s.activate("double_challenge:crafting_tax,no_sprint");
            for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL)) {
                p.changeGameMode(mode);
                context.assertEquals(mode == GameMode.ADVENTURE || mode == GameMode.SURVIVAL,
                        ChallengeActivity.active(p, CraftingTaxChallenge.ID), "Mode eligibility " + mode);
            }
            p.changeGameMode(GameMode.CREATIVE);
            craftAtZero(context, p, true, "Creative exemption");
            p.changeGameMode(GameMode.SURVIVAL);
            craftAtZero(context, p, false, "Survival restored");
            p.setSprinting(true);
            context.assertFalse(p.isSprinting(), "Double challenge retains no_sprint");
            var late = s.player();
            craftAtZero(context, late, false, "Late joining player independently restricted");
            s.state.getPersonalActiveChallengeIds(p.getUuid()).add(CraftingTaxChallenge.ID);
            s.state.getActiveChallengeIds().clear();
            craftAtZero(context, p, false, "Personal restriction");
            craftAtZero(context, late, true, "Personal restriction does not affect other player");
            s.state.getActiveChallengeIds().add(CraftingTaxChallenge.ID);
            reset(p, p.playerScreenHandler, 2);
            logs(p.playerScreenHandler, 1);
            p.playerScreenHandler.onSlotClick(0, 0, SlotActionType.PICKUP, p);
            context.assertEquals(1, p.experienceLevel, "Duplicate global/personal ID charges once");
            s.state.getPersonalActiveChallengeIds(p.getUuid()).clear();
            var challenge = ChallengeRegistry.get(CraftingTaxChallenge.ID);
            challenge.setEnabled(false);
            try { craftAtZero(context, p, true, "Disabled challenge"); }
            finally { challenge.setEnabled(true); }
            p.setHealth(0);
            context.assertTrue(CraftingTaxChallenge.canPay(p), "Dead object is exempt");
            p = s.respawn(p, false);
            craftAtZero(context, p, false, "Respawn replacement checks current activity");
            for (var dimension : List.of(World.NETHER, World.OVERWORLD)) {
                p.teleport(p.getServer().getWorld(dimension), 0.5, 90, 0.5, 0, 0);
                craftAtZero(context, p, false, "Dimension " + dimension);
            }
            var persistence = p.getServer().getOverworld().getPersistentStateManager();
            s.state.markDirty();
            persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(saved.getCompound("data"), p.getRegistryManager());
            context.assertTrue(loaded.getActiveChallengeIds().contains(CraftingTaxChallenge.ID), "Active ID saved to disk");
            context.assertTrue(loaded.getChallengeData().isEmpty(), "No recipe credit or XP snapshot persisted");
            persistence.set(CurseCycleState.STATE_KEY, loaded);
            craftAtZero(context, p, false, "Disk-restored activity enforces payment");
            UUID uuid = p.getUuid();
            p.getServer().getPlayerManager().remove(p);
            var returning = s.player(uuid);
            craftAtZero(context, returning, false, "Same UUID reconnect uses active rules");
            p.getServer().getPlayerManager().remove(returning);
            // The disk round trip replaced the scenario's original state instance.
            com.cursecycle.state.CycleManager.activateChallenge(p.getServer(), loaded, "no_sprint");
            returning = s.player(uuid);
            craftAtZero(context, returning, true, "Stopped challenge no longer taxes");
            returning.setSprinting(true);
            context.assertFalse(returning.isSprinting(), "Other challenge remains enforced");
            com.cursecycle.state.CycleManager.activateChallenge(p.getServer(), loaded, "double_challenge:crafting_tax,bitter_experience");
            reset(returning, returning.playerScreenHandler, 2);
            returning.setHealth(20);
            logs(returning.playerScreenHandler, 1);
            returning.playerScreenHandler.onSlotClick(0, 0, SlotActionType.PICKUP, returning);
            context.assertEquals(1, returning.experienceLevel, "Bitter experience pair still pays one level");
            context.assertEquals(20F, returning.getHealth(), "Level payment does not trigger orb pickup damage");
        }
        context.waitAndRun(5, context::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "crafting_tax_feedback", tickLimit = 100)
    public void feedbackIsLimitedToFortyServerTicks(TestContext context) {
        var s = new ChallengeScenario(context);
        var server = context.getWorld().getServer();
        var data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), "tax-feedback"), false);
        var p = new ServerPlayerEntity(server, context.getWorld(), data.gameProfile(), data.syncedOptions());
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, p, data);
        p.changeGameMode(GameMode.SURVIVAL);
        s.activate(CraftingTaxChallenge.ID);
        reset(p, p.playerScreenHandler, 0);
        logs(p.playerScreenHandler, 1);
        CraftingTaxChallenge.canPay(p);
        CraftingTaxChallenge.canPay(p);
        try { context.assertEquals(1, notices(channel), "Repeated attempts in one tick send one actionbar notice"); }
        catch (Throwable failure) { cleanup(s, p, channel); throw failure; }
        context.waitAndRun(39, () -> {
            try {
                CraftingTaxChallenge.canPay(p);
                context.assertEquals(0, notices(channel), "No repeat before forty ticks");
            } catch (Throwable failure) { cleanup(s, p, channel); throw failure; }
        });
        context.waitAndRun(40, () -> {
            try {
                CraftingTaxChallenge.canPay(p);
                context.assertEquals(1, notices(channel), "Next denial at forty ticks sends another notice");
                context.complete();
            } finally { cleanup(s, p, channel); }
        });
    }

    private static void cleanup(ChallengeScenario s, ServerPlayerEntity p, EmbeddedChannel channel) {
        ChallengeRegistry.get(CraftingTaxChallenge.ID).onPlayerLeave(p.getServer(), p);
        p.getServer().getPlayerManager().remove(p);
        s.close();
        channel.finishAndReleaseAll();
    }
    private static int notices(EmbeddedChannel channel) {
        channel.runPendingTasks();
        channel.flush();
        int count = 0;
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof GameMessageS2CPacket overlay && overlay.overlay()
                    && overlay.content().getString().equals("Нужен 1 уровень опыта")) count++;
            ReferenceCountUtil.release(packet);
        }
        return count;
    }
    private static void craftAtZero(TestContext c, ServerPlayerEntity p, boolean allowed, String message) {
        var h = p.playerScreenHandler;
        reset(p, h, 0);
        logs(h, 1);
        h.onSlotClick(0, 0, SlotActionType.PICKUP, p);
        c.assertEquals(allowed, !h.getCursorStack().isEmpty(), message);
        c.assertEquals(0, p.experienceLevel, message + " never creates negative XP");
    }
    private static void reset(ServerPlayerEntity p, ScreenHandler h, int levels) {
        p.getInventory().clear();
        h.setCursorStack(ItemStack.EMPTY);
        int size = h instanceof CraftingScreenHandler ? 9 : 4;
        for (int i = 1; i <= size; i++) h.getSlot(i).setStack(ItemStack.EMPTY);
        p.experienceLevel = levels;
        p.experienceProgress = 0;
        p.totalExperience = 0;
    }
    private static void logs(ScreenHandler h, int count) {
        h.getSlot(1).setStack(new ItemStack(Items.OAK_LOG, count));
        h.onContentChanged(h.getSlot(1).inventory);
    }
    private static void fill(ServerPlayerEntity p) {
        for (int i = 0; i < 36; i++) p.getInventory().setStack(i, new ItemStack(Items.STONE, 64));
    }
    private static int count(ServerPlayerEntity p, net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < 36; i++) if (p.getInventory().getStack(i).isOf(item)) count += p.getInventory().getStack(i).getCount();
        return count;
    }
}
