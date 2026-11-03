package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.HotbarShuffleChallenge;
import com.cursecycle.challenge.impl.OneTimeMenuChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HotbarShuffleGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hotbar_shuffle", tickLimit = 1100)
    public void intervalsScreensUseAndLifecycle(TestContext context) { new Scenario(context).schedule(); }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hotbar_shuffle_ownership", tickLimit = 520)
    public void packetBudgetOwnershipDimensionsAndDisk(TestContext context) { new Scenario(context).scheduleOwnership(); }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hotbar_shuffle_pairs", tickLimit = 250)
    public void foodAndCraftingAroundShuffle(TestContext context) { new Scenario(context).schedulePairs(); }

    private static final class Connected {
        ServerPlayerEntity player;
        final ClientConnection connection;
        final EmbeddedChannel channel;
        final List<ScreenHandlerSlotUpdateS2CPacket> updates = new ArrayList<>();
        Connected(ServerPlayerEntity player, ClientConnection connection, EmbeddedChannel channel) {
            this.player = player; this.connection = connection; this.channel = channel;
        }
    }

    private static final class Scenario {
        final TestContext context;
        final MinecraftServer server;
        final HotbarShuffleChallenge challenge;
        final NbtCompound original;
        final boolean keepInventory;
        final List<Connected> clients = new ArrayList<>();
        final SimpleInventory chest = new SimpleInventory(27);
        CurseCycleState state;
        Connected first, food, offhand, sameHand, adventure, creative, spectator, identical, empty, late, counts, components, sparse;
        List<ItemStack> countsLayout, componentsLayout, sparseLayout;
        List<ItemStack> firstLayout, lateLayout, stoppedLayout, modeLayout, respawnLayout, reconnectLayout;
        UUID returning;
        boolean finished;

        Scenario(TestContext context) {
            this.context = context;
            server = context.getWorld().getServer();
            state = CurseCycleState.getServerState(server);
            original = state.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager());
            keepInventory = context.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY);
            challenge = (HotbarShuffleChallenge) ChallengeRegistry.get(HotbarShuffleChallenge.ID);
        }

        void schedule() {
            context.runAtEveryTick(() -> {
                if (!finished) for (var client : List.copyOf(clients)) drain(client);
            });
            at(1, () -> {
                activate("test_easy");
                first = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                food = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                offhand = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                sameHand = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                adventure = connect(UUID.randomUUID(), GameMode.ADVENTURE);
                creative = connect(UUID.randomUUID(), GameMode.CREATIVE);
                spectator = connect(UUID.randomUUID(), GameMode.SPECTATOR);
                identical = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                empty = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                counts = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                components = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                sparse = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                for (int i = 0; i < 9; i++) {
                    counts.player.getInventory().setStack(i, new ItemStack(Items.DIAMOND, i + 1));
                    components.player.getInventory().setStack(i, named(new ItemStack(Items.DIAMOND), "component-" + i));
                    sparse.player.getInventory().setStack(i, ItemStack.EMPTY);
                }
                sparse.player.getInventory().setStack(3, named(new ItemStack(Items.IRON_PICKAXE), "sparse"));
                sparse.player.getInventory().getStack(3).setDamage(17);
                countsLayout = hotbar(counts); componentsLayout = hotbar(components); sparseLayout = hotbar(sparse);
                for (int i = 0; i < 9; i++) identical.player.getInventory().setStack(i, new ItemStack(Items.STONE, 4));
                empty.player.getInventory().clear();
                first.player.getInventory().setStack(3, named(Items.BOW.getDefaultStack(), "drawn bow"));
                first.player.getInventory().setStack(9, new ItemStack(Items.ARROW, 32));
                food.player.getInventory().setStack(3, new ItemStack(Items.APPLE, 7));
                food.player.getHungerManager().setFoodLevel(10);
                offhand.player.getInventory().setStack(40, new ItemStack(Items.SHIELD));
                chest.setStack(0, named(new ItemStack(Items.EMERALD, 13), "chest"));
                first.player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        (id, inv, p) -> GenericContainerScreenHandler.createGeneric9x3(id, inv, chest), Text.literal("fixture")));
                first.player.currentScreenHandler.setCursorStack(named(new ItemStack(Items.REDSTONE, 7), "cursor"));
                firstLayout = hotbar(first);
                command("fdc list");
                command("fdc activate hotbar_shuffle");
                command("fdc active");
                context.assertEquals(1L, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals("hotbar_shuffle")).count(), "Registered once");
                context.assertTrue(state.getActiveChallengeIds().contains("hotbar_shuffle"), "Command activation");
                context.assertEquals("Курчу верчу запутать хочу", challenge.getName(), "Exact Russian name");
                equal(firstLayout, hotbar(first), "No shuffle at activation");
            });
            at(33, () -> { late = connect(UUID.randomUUID(), GameMode.SURVIVAL); lateLayout = hotbar(late); });
            at(100, () -> {
                equal(firstLayout, hotbar(first), "Unchanged after 99 ticks");
                for (var client : List.of(first, food, offhand)) seedSelected(client, false);
                seedSelected(sameHand, true);
                first.player.setCurrentHand(Hand.MAIN_HAND);
                food.player.setCurrentHand(Hand.MAIN_HAND);
                offhand.player.setCurrentHand(Hand.OFF_HAND);
                sameHand.player.getInventory().setStack(3, new ItemStack(Items.BOW));
                sameHand.player.setCurrentHand(Hand.MAIN_HAND);
                first.updates.clear();
            });
            at(101, () -> {
                var protectedSlots = outside(first);
                var cursor = first.player.currentScreenHandler.getCursorStack().copy();
                var chestStack = chest.getStack(0).copy();
                var foodBefore = hotbar(food);
                challenge.onTick(server, first.player);
                challenge.onTick(server, food.player);
                challenge.onTick(server, offhand.player);
                challenge.onTick(server, sameHand.player);
                changed(firstLayout, hotbar(first), "Shuffle at tick 100");
                multiset(firstLayout, hotbar(first), "Full stacks/components preserved");
                multiset(foodBefore, hotbar(food), "Food not consumed during cancellation");
                equal(protectedSlots, outside(first), "Main inventory, armor and offhand unchanged");
                stack(cursor, first.player.currentScreenHandler.getCursorStack(), "Cursor unchanged");
                stack(chestStack, chest.getStack(0), "External inventory unchanged");
                context.assertEquals(3, first.player.getInventory().selectedSlot, "Selected slot stays fixed");
                context.assertFalse(first.player.isUsingItem(), "Bow cancelled without release");
                context.assertFalse(food.player.isUsingItem(), "Eating cancelled");
                context.assertTrue(offhand.player.isUsingItem(), "Offhand use continues");
                context.assertTrue(sameHand.player.isUsingItem(), "Unchanged selected contents keep their use");
                context.assertEquals(0, context.getWorld().getEntitiesByType(EntityType.ARROW, e -> true).size(), "Cancellation does not shoot");
                var once = hotbar(first);
                state.getPersonalActiveChallenges().put(first.player.getUuid(), new ArrayList<>(List.of("hotbar_shuffle")));
                challenge.onTick(server, first.player);
                equal(once, hotbar(first), "Global/personal duplicate dispatch cannot shuffle twice");
                state.getPersonalActiveChallenges().remove(first.player.getUuid());
                drain(first);
                for (int i = 0; i < 9; i++) {
                    if (ItemStack.areEqual(firstLayout.get(i), once.get(i))) continue;
                    int slot = hotbarSlot(first.player, i);
                    var expected = once.get(i);
                    context.assertTrue(first.updates.stream().anyMatch(p -> p.getSyncId() == first.player.currentScreenHandler.syncId
                            && p.getSlot() == slot && ItemStack.areEqual(p.getStack(), expected)),
                            "Changed hotbar slot " + slot + " sent through sync " + first.player.currentScreenHandler.syncId
                                    + "; packets=" + first.updates.stream().map(p -> p.getSyncId() + ":" + p.getSlot()).toList());
                }
                context.assertTrue(identical.player.getInventory().getStack(0).isOf(Items.STONE), "Equal hotbar completes");
                context.assertTrue(empty.player.getInventory().isEmpty(), "Empty hotbar completes");
                firstLayout = once;
            });
            at(102, () -> equal(firstLayout, hotbar(first), "END_SERVER_TICK duplicate also leaves layout alone"));
            at(103, () -> {
                for (var fixture : List.of(counts, components, sparse)) {
                    var before = fixture == counts ? countsLayout : fixture == components ? componentsLayout : sparseLayout;
                    changed(before, hotbar(fixture), "Count/component/sparse hotbar changes");
                    multiset(before, hotbar(fixture), "Counts, components, empty slots and durability preserved");
                }
            });
            at(132, () -> equal(lateLayout, hotbar(late), "Late join gets 99 unchanged ticks"));
            at(134, () -> changed(lateLayout, hotbar(late), "Late join has an independent interval"));
            at(140, () -> {
                equal(initialLayout(), hotbar(creative), "Creative exempt");
                equal(initialLayout(), hotbar(spectator), "Spectator exempt");
                changed(initialLayout(), hotbar(adventure), "Adventure participates");
                activate("double_challenge:hotbar_shuffle,inventory_lockdown");
            });
            at(239, () -> {
                // A real C2S click before the shuffle swaps with the existing cursor stack.
                click(first, hotbarSlot(first.player, 0), first.player.currentScreenHandler.getRevision());
                firstLayout = hotbar(first);
            });
            at(240, () -> {
                var total = contents(first);
                int staleRevision = first.player.currentScreenHandler.getRevision();
                challenge.onTick(server, first.player);
                changed(firstLayout, hotbar(first), "Automatic shuffle works with inventory_lockdown");
                multiset(total, contents(first), "Shuffle preserves GUI contents");
                click(first, hotbarSlot(first.player, 1), staleRevision);
                multiset(total, contents(first), "Stale revision click is reconciled without duplication/loss");
                var locked = first.player.getInventory().getStack(9).copy();
                click(first, hotbarSlot(first.player, 9), first.player.currentScreenHandler.getRevision());
                stack(locked, first.player.getInventory().getStack(9), "Manual lockdown still works");
                activate("double_challenge:hotbar_shuffle,damage_slot_drop");
            });
            at(340, () -> {
                challenge.onTick(server, first.player);
                var before = contents(first);
                var area = first.player.getBoundingBox().expand(5);
                var existingDrops = context.getWorld().getEntitiesByClass(ItemEntity.class, area, e -> true)
                        .stream().map(Entity::getUuid).toList();
                first.player.getAbilities().invulnerable = false;
                first.player.timeUntilRegen = 0;
                context.assertTrue(first.player.damage(first.player.getDamageSources().generic(), 1), "Real damage accepted");
                first.player.getAbilities().invulnerable = true;
                var drops = context.getWorld().getEntitiesByClass(ItemEntity.class, area, e -> !existingDrops.contains(e.getUuid()));
                context.assertEquals(1, drops.size(), "Damage drops exactly one current stack");
                var after = contents(first);
                after.add(drops.getFirst().getStack().copy());
                multiset(before, after, "Damage drop uses the shuffled inventory");
                drops.getFirst().discard();
                activate("hotbar_shuffle");
            });
            at(380, () -> { first.player.changeGameMode(GameMode.CREATIVE); modeLayout = hotbar(first); });
            at(410, () -> { first.player.changeGameMode(GameMode.ADVENTURE); challenge.onTick(server, first.player); });
            at(509, () -> equal(modeLayout, hotbar(first), "Mode return starts a full interval"));
            at(511, () -> {
                changed(modeLayout, hotbar(first), "Mode return shuffles after 100 ticks");
                context.getWorld().getGameRules().get(GameRules.KEEP_INVENTORY).set(true, server);
                first.player.setHealth(0);
                first.player.onDeath(first.player.getDamageSources().genericKill());
                var dead = hotbar(first);
                challenge.onTick(server, first.player);
                equal(dead, hotbar(first), "Dead entity is untouched");
                respawn(first, false);
                respawnLayout = hotbar(first);
                multiset(dead, respawnLayout, "Vanilla keepInventory respected");
            });
            at(550, () -> {
                respawn(first, true);
                equal(respawnLayout, hotbar(first), "Live dimension entity replacement keeps items");
            });
            at(610, () -> equal(respawnLayout, hotbar(first), "Respawn has 99 full ticks; live replacement does not restart it"));
            at(612, () -> {
                changed(respawnLayout, hotbar(first), "Live dimension replacement preserves remaining interval");
                returning = first.player.getUuid();
                disconnect(first);
            });
            at(630, () -> { first = connect(returning, GameMode.SURVIVAL); reconnectLayout = hotbar(first); });
            at(729, () -> equal(reconnectLayout, hotbar(first), "Reconnect gets a fresh interval"));
            at(731, () -> {
                changed(reconnectLayout, hotbar(first), "Reconnected player shuffles normally");
                command("fdc activate \"double_challenge:hotbar_shuffle,no_sprint\"");
                first.player.setSprinting(true);
                context.assertFalse(first.player.isSprinting(), "No sprint also applies during the pair");
                // Verify real shared-state disk persistence; this is not a process restart.
                var persistence = server.getOverworld().getPersistentStateManager();
                state.markDirty();
                persistence.save();
                var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
                state = CurseCycleState.createFromNbt(saved.getCompound("data"), context.getWorld().getRegistryManager());
                persistence.set(CurseCycleState.STATE_KEY, state);
                context.assertTrue(state.getActiveChallengeIds().contains("hotbar_shuffle"), "Active ID survives disk save/read");
                context.assertFalse(state.getChallengeData().contains("hotbar_shuffle"), "Timer/RNG are not persisted");
                disconnect(first);
                activate("no_sprint");
            });
            at(740, () -> {
                first = connect(returning, GameMode.SURVIVAL);
                stoppedLayout = hotbar(first);
                first.player.setSprinting(true);
                context.assertFalse(first.player.isSprinting(), "Other active restriction remains");
            });
            at(845, () -> {
                equal(stoppedLayout, hotbar(first), "Offline completion never resurrects old timer");
                // PERSONAL-only activation uses the same applicability and independent timer.
                state.getPersonalActiveChallenges().put(first.player.getUuid(), new ArrayList<>(List.of("hotbar_shuffle")));
                challenge.onPlayerJoin(server, first.player);
                stoppedLayout = hotbar(first);
            });
            at(944, () -> equal(stoppedLayout, hotbar(first), "Personal timer also waits 99 ticks"));
            at(946, () -> {
                changed(stoppedLayout, hotbar(first), "Personal activation works");
                state.getPersonalActiveChallenges().remove(first.player.getUuid());
                challenge.onStop(server, first.player);
                cleanup();
                context.waitAndRun(5, context::complete);
            });
        }

        // Read-only inspection of ephemeral ownership; cadence still runs on actual server ticks.
        Map<?, ?> ownedTimers() throws ReflectiveOperationException {
            var field = HotbarShuffleChallenge.class.getDeclaredField("timers");
            field.setAccessible(true);
            var byServer = (Map<?, ?>) field.get(challenge);
            var owned = (Map<?, ?>) byServer.get(server);
            return owned == null ? Map.of() : owned;
        }
        Object timer(UUID uuid) throws ReflectiveOperationException { return ownedTimers().get(uuid); }

        void reloadSharedState() throws Exception {
            var persistence = server.getOverworld().getPersistentStateManager();
            state.markDirty();
            persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    SharedConstants.getGameVersion().getSaveVersion().getId());
            state = CurseCycleState.createFromNbt(saved.getCompound("data"), context.getWorld().getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, state);
            context.assertFalse(state.getChallengeData().contains(HotbarShuffleChallenge.ID), "No timer/RNG in disk state");
        }

        void scheduleOwnership() {
            context.runAtEveryTick(() -> {
                if (!finished) for (var client : List.copyOf(clients)) drain(client);
            });
            at(1, () -> {
                activate("test_easy");
                first = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                empty = connect(UUID.randomUUID(), GameMode.ADVENTURE);
                empty.player.getInventory().clear();
                firstLayout = hotbar(first);
                for (int i = 0; i < 32; i++) {
                    activate("double_challenge:hotbar_shuffle,hotbar_shuffle");
                    context.assertEquals(2, ownedTimers().size(), "Repeated ID owns one timer per eligible UUID");
                    challenge.onStart(server, first.player);
                    context.assertEquals(2, ownedTimers().size(), "Repeated start does not grow state");
                    activate("no_sprint");
                    context.assertTrue(ownedTimers().isEmpty(), "Stop frees all clocks each cycle");
                    equal(firstLayout, hotbar(first), "No old inventory snapshot restored");
                }
                activate("hotbar_shuffle");
                challenge.setEnabled(false);
                context.assertTrue(ownedTimers().isEmpty(), "Disable clears clocks even without future dispatch");
                challenge.onTick(server, first.player);
                context.assertTrue(ownedTimers().isEmpty(), "Disabled direct call cannot reallocate a clock");
                challenge.setEnabled(true);
                activate("hotbar_shuffle");
                state.getPersonalActiveChallenges().put(first.player.getUuid(), new ArrayList<>(List.of("hotbar_shuffle", "hotbar_shuffle")));
                challenge.onTick(server, first.player);
                context.assertEquals(2, ownedTimers().size(), "GLOBAL/PERSONAL overlap keeps two independent clocks");
            });
            at(40, () -> {
                var oldTimer = timer(first.player.getUuid());
                activate("hotbar_shuffle");
                context.assertTrue(oldTimer != timer(first.player.getUuid()), "Same ID new activation resets its clock");
                firstLayout = hotbar(first);
                first.player.currentScreenHandler.sendContentUpdates();
            });
            at(139, () -> {
                equal(firstLayout, hotbar(first), "Reactivation waits its own 99 ticks");
                drain(first); first.updates.clear(); drain(empty); empty.updates.clear();
            });
            at(140, () -> {
                challenge.onTick(server, first.player);
                challenge.onTick(server, empty.player);
                var after = hotbar(first);
                changed(firstLayout, after, "Reactivated interval reaches 100");
                multiset(firstLayout, after, "Packet-budget shuffle preserves all components");
                drain(first); drain(empty);
                int changedSlots = 0;
                for (int i = 0; i < 9; i++) {
                    if (ItemStack.areEqual(firstLayout.get(i), after.get(i))) continue;
                    changedSlots++;
                    int slot = hotbarSlot(first.player, i);
                    var stack = after.get(i);
                    context.assertEquals(1L, first.updates.stream().filter(p -> p.getSyncId() == first.player.currentScreenHandler.syncId
                            && p.getSlot() == slot && ItemStack.areEqual(p.getStack(), stack)).count(), "Exactly one update for changed slot");
                }
                context.assertEquals(changedSlots, first.updates.size(), "Only changed hotbar slots sent, at most nine");
                context.assertTrue(empty.updates.isEmpty(), "Empty hotbar emits no slot updates");
                challenge.onTick(server, first.player);
                drain(first);
                context.assertEquals(changedSlots, first.updates.size(), "Duplicate dispatch emits nothing extra");
                firstLayout = after;
            });
            at(151, () -> {
                var clock = timer(first.player.getUuid());
                first.player.teleport(server.getWorld(World.NETHER), 0.5, 100, 0.5, 0, 0);
                challenge.onTick(server, first.player);
                context.assertTrue(clock == timer(first.player.getUuid()), "Actual Nether teleport retains timer identity");
            });
            at(200, () -> {
                var clock = timer(first.player.getUuid());
                first.player.teleport(server.getWorld(World.END), 0.5, 100, 0.5, 0, 0);
                challenge.onTick(server, first.player);
                context.assertTrue(clock == timer(first.player.getUuid()), "Actual End teleport retains timer identity");
            });
            at(239, () -> equal(firstLayout, hotbar(first), "Dimension travel does not shorten remaining interval"));
            at(240, () -> {
                challenge.onTick(server, first.player);
                changed(firstLayout, hotbar(first), "Dimension travel does not restart interval");
                firstLayout = hotbar(first);
                returning = first.player.getUuid();
                first.player.addExperience(123);
                // PlayerManager.remove writes actual playerdata; connect(false) does not replace loaded items.
                disconnect(first);
                context.assertEquals(1, ownedTimers().size(), "Disconnect frees only the departing UUID");
                reloadSharedState();
            });
            at(260, () -> {
                first = connect(returning, GameMode.SURVIVAL, false);
                equal(firstLayout, hotbar(first), "Actual playerdata disk load retains shuffled stacks");
                context.assertEquals(123, first.player.totalExperience, "Foreign XP survives disk load");
                context.assertEquals(2, ownedTimers().size(), "Loaded JOIN creates only one new clock");
            });
            at(359, () -> equal(firstLayout, hotbar(first), "Disk-loaded JOIN gets a full interval"));
            at(360, () -> {
                challenge.onTick(server, first.player);
                changed(firstLayout, hotbar(first), "Disk-loaded active state shuffles on its new boundary");
                firstLayout = hotbar(first);
                state.getPersonalActiveChallenges().remove(first.player.getUuid());
                disconnect(first); disconnect(empty);
                context.assertTrue(ownedTimers().isEmpty(), "Empty server has no player timers");
                activate("no_sprint");
                reloadSharedState();
            });
            at(370, () -> {
                first = connect(returning, GameMode.SURVIVAL, false);
                equal(firstLayout, hotbar(first), "Ended disk load leaves actual inventory as saved");
                context.assertTrue(ownedTimers().isEmpty(), "Ended JOIN does not resurrect a timer");
            });
            at(475, () -> {
                equal(firstLayout, hotbar(first), "Offline-ended state never shuffles after 100 ticks");
                first.player.setSprinting(true);
                context.assertFalse(first.player.isSprinting(), "No sprint survives ending shuffle");
                cleanup(); context.complete();
            });
        }

        void schedulePairs() {
            context.runAtEveryTick(() -> {
                if (!finished) for (var client : List.copyOf(clients)) drain(client);
            });
            at(1, () -> {
                activate("test_easy");
                first = connect(UUID.randomUUID(), GameMode.SURVIVAL);
                first.player.getInventory().setStack(3, new ItemStack(Items.APPLE, 7));
                first.player.getHungerManager().setFoodLevel(10);
                activate("double_challenge:hotbar_shuffle,one_time_menu");
                firstLayout = hotbar(first);
            });
            at(100, () -> {
                seedSelected(first, false);
                context.assertTrue(first.player.interactionManager.interactItem(first.player, first.player.getWorld(),
                        first.player.getMainHandStack(), Hand.MAIN_HAND).isAccepted(), "Actual allowed food use starts");
            });
            at(101, () -> {
                challenge.onTick(server, first.player);
                context.assertFalse(first.player.isUsingItem(), "Shuffle cancels actual food use");
                multiset(firstLayout, hotbar(first), "Cancelled food consumes neither old nor replacement stack");
                context.assertFalse(OneTimeMenuChallenge.blocked(first.player, "minecraft:apple"), "Cancelled food is not recorded as eaten");
                int apple = -1;
                for (int i = 0; i < 9; i++) if (first.player.getInventory().getStack(i).isOf(Items.APPLE)) apple = i;
                context.assertTrue(apple >= 0, "Whole apple stack moved");
                first.player.getInventory().getStack(apple).finishUsing(first.player.getWorld(), first.player);
                context.assertEquals(6, first.player.getInventory().getStack(apple).getCount(), "Successful finish after shuffle consumes exactly one");
                context.assertTrue(OneTimeMenuChallenge.blocked(first.player, "minecraft:apple"), "Other mechanic records actual consumption");
                // Retain OTM via PERSONAL while removing shuffle from GLOBAL.
                state.getPersonalActiveChallenges().put(first.player.getUuid(), new ArrayList<>(List.of("one_time_menu")));
                state.getActiveChallengeIds().remove("hotbar_shuffle");
                challenge.onStop(server, first.player);
                context.assertTrue(OneTimeMenuChallenge.blocked(first.player, "minecraft:apple"), "Ending shuffle does not erase food history");
                state.getPersonalActiveChallenges().remove(first.player.getUuid());
                activate("double_challenge:hotbar_shuffle,crafting_tax");
                var h = first.player.playerScreenHandler;
                h.getSlot(1).setStack(new ItemStack(Items.OAK_LOG, 2));
                h.onContentChanged(h.getSlot(1).inventory);
                first.player.experienceLevel = 2;
                firstLayout = hotbar(first);
            });
            at(200, () -> {
                var h = first.player.playerScreenHandler;
                click(first, 0, h.getRevision());
                context.assertEquals(1, first.player.experienceLevel, "Craft before shuffle charges one level");
                context.assertEquals(4, h.getCursorStack().getCount(), "Craft before shuffle produces one whole output");
                firstLayout = hotbar(first);
            });
            at(201, () -> {
                var h = first.player.playerScreenHandler;
                challenge.onTick(server, first.player);
                changed(firstLayout, hotbar(first), "Shuffle continues with crafting tax");
                context.assertEquals(4, h.getCursorStack().getCount(), "Shuffle leaves crafted cursor unchanged");
                context.assertEquals(1, h.getSlot(1).getStack().getCount(), "Shuffle leaves crafting input unchanged");
                click(first, 0, h.getRevision());
                context.assertEquals(0, first.player.experienceLevel, "Craft after shuffle charges one more level");
                context.assertEquals(8, h.getCursorStack().getCount(), "Both crafts conserve exact output");
                state.getActiveChallengeIds().remove("hotbar_shuffle");
                challenge.onStop(server, first.player);
                h.getSlot(1).setStack(new ItemStack(Items.OAK_LOG));
                h.onContentChanged(h.getSlot(1).inventory);
                click(first, 0, h.getRevision());
                context.assertEquals(8, h.getCursorStack().getCount(), "Tax still blocks unaffordable craft after shuffle ends");
                context.assertEquals(1, h.getSlot(1).getStack().getCount(), "Rejected craft preserves input");
                cleanup(); context.complete();
            });
        }

        Connected connect(UUID uuid, GameMode mode) { return connect(uuid, mode, true); }
        Connected connect(UUID uuid, GameMode mode, boolean seedInventory) {
            var data = ConnectedClientData.createDefault(new GameProfile(uuid, "shuffle-" + uuid.toString().substring(0, 8)), false);
            var player = new ServerPlayerEntity(server, context.getWorld(), data.gameProfile(), data.syncedOptions());
            var connection = new ClientConnection(NetworkSide.SERVERBOUND);
            var channel = new EmbeddedChannel(connection);
            server.getPlayerManager().onPlayerConnect(connection, player, data);
            server.getNetworkIo().getConnections().add(connection);
            var client = new Connected(player, connection, channel);
            clients.add(client);
            prepare(player, mode);
            if (!seedInventory) return client;
            var layout = initialLayout();
            for (int i = 0; i < 9; i++) player.getInventory().setStack(i, layout.get(i));
            player.getInventory().selectedSlot = 3;
            player.getInventory().setStack(38, new ItemStack(Items.DIAMOND_CHESTPLATE));
            player.getInventory().setStack(40, named(new ItemStack(Items.TORCH, 11), "offhand"));
            return client;
        }
        void prepare(ServerPlayerEntity player, GameMode mode) {
            player.changeGameMode(mode);
            player.getAbilities().invulnerable = true;
            player.setNoGravity(true);
            var pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
            player.teleport(context.getWorld(), pos.getX() + 0.5, 80, pos.getZ() + 0.5, 0, 0);
            player.setVelocity(0, 0, 0);
        }
        void respawn(Connected client, boolean alive) {
            client.player = server.getPlayerManager().respawnPlayer(client.player, alive,
                    alive ? Entity.RemovalReason.CHANGED_DIMENSION : Entity.RemovalReason.KILLED);
            client.player.networkHandler.player = client.player;
            prepare(client.player, GameMode.SURVIVAL);
        }
        void disconnect(Connected client) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                    .onPlayDisconnect(client.player.networkHandler, server);
            server.getPlayerManager().remove(client.player);
            server.getNetworkIo().getConnections().remove(client.connection);
            client.channel.finishAndReleaseAll();
            clients.remove(client);
        }
        void drain(Connected client) {
            client.channel.runPendingTasks();
            // Test callbacks run inside the server's packet-flush batch. Expose queued writes now.
            client.channel.flush();
            Object packet;
            while ((packet = client.channel.readOutbound()) != null) {
                if (packet instanceof KeepAliveS2CPacket keep) client.player.networkHandler.onKeepAlive(new KeepAliveC2SPacket(keep.getId()));
                if (packet instanceof PlayerPositionLookS2CPacket teleport) client.player.networkHandler.onTeleportConfirm(new TeleportConfirmC2SPacket(teleport.getTeleportId()));
                if (packet instanceof ScreenHandlerSlotUpdateS2CPacket update) client.updates.add(update);
                ReferenceCountUtil.release(packet);
            }
        }
        void click(Connected client, int slot, int revision) {
            var handler = client.player.currentScreenHandler;
            client.player.networkHandler.onClickSlot(new ClickSlotC2SPacket(handler.syncId, revision, slot, 0,
                    SlotActionType.PICKUP, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
        }
        int hotbarSlot(ServerPlayerEntity player, int index) {
            return player.currentScreenHandler.slots.stream().filter(s -> s.inventory == player.getInventory() && s.getIndex() == index)
                    .findFirst().orElseThrow().id;
        }
        void seedSelected(Connected client, boolean unchanged) {
            var before = hotbar(client);
            for (long seed = 0; ; seed++) {
                var result = HotbarShuffleChallenge.shuffled(before, ItemStack::areEqual, Random.create(seed)::nextInt);
                if (ItemStack.areEqual(before.get(3), result.get(3)) == unchanged) {
                    client.player.getRandom().setSeed(seed); return;
                }
            }
        }
        List<ItemStack> initialLayout() {
            var result = new ArrayList<ItemStack>();
            for (int i = 0; i < 9; i++) result.add(named(new ItemStack(Items.DIAMOND, i + 1), "slot-" + i));
            return result;
        }
        List<ItemStack> hotbar(Connected c) { return range(c, 0, 9); }
        List<ItemStack> outside(Connected c) { return range(c, 9, c.player.getInventory().size()); }
        List<ItemStack> range(Connected c, int start, int end) {
            var result = new ArrayList<ItemStack>();
            for (int i = start; i < end; i++) result.add(c.player.getInventory().getStack(i).copy());
            return result;
        }
        List<ItemStack> contents(Connected c) {
            var result = range(c, 0, c.player.getInventory().size());
            result.add(c.player.currentScreenHandler.getCursorStack().copy());
            for (int i = 0; i < chest.size(); i++) result.add(chest.getStack(i).copy());
            return result;
        }
        void stack(ItemStack a, ItemStack b, String message) { context.assertTrue(ItemStack.areEqual(a, b), message); }
        void equal(List<ItemStack> a, List<ItemStack> b, String message) {
            context.assertEquals(a.size(), b.size(), message);
            for (int i = 0; i < a.size(); i++) stack(a.get(i), b.get(i), message + " slot " + i);
        }
        void changed(List<ItemStack> a, List<ItemStack> b, String message) {
            int differences = 0;
            for (int i = 0; i < a.size(); i++) if (!ItemStack.areEqual(a.get(i), b.get(i))) differences++;
            context.assertTrue(differences >= 2, message);
        }
        void multiset(List<ItemStack> before, List<ItemStack> after, String message) {
            var remaining = new ArrayList<>(after.stream().filter(s -> !s.isEmpty()).toList());
            for (var stack : before) {
                if (stack.isEmpty()) continue;
                int index = -1;
                for (int i = 0; i < remaining.size(); i++) if (ItemStack.areEqual(stack, remaining.get(i))) { index = i; break; }
                context.assertTrue(index >= 0, message + ": missing " + stack);
                remaining.remove(index);
            }
            context.assertTrue(remaining.isEmpty(), message + ": extra stacks");
        }
        void command(String command) { server.getCommandManager().executeWithPrefix(server.getCommandSource(), command); }
        void activate(String id) { CycleManager.activateChallenge(server, state, id); }
        void at(long tick, Step action) {
            context.runAtTick(tick, () -> {
                try { action.run(); }
                catch (Exception | AssertionError failure) {
                    cleanup();
                    throw new RuntimeException("hotbar_shuffle tick " + tick + ": " + failure.getMessage(), failure);
                }
            });
        }
        void cleanup() {
            finished = true;
            challenge.setEnabled(true);
            for (var client : List.copyOf(clients)) disconnect(client);
            context.getWorld().getGameRules().get(GameRules.KEEP_INVENTORY).set(keepInventory, server);
            server.getOverworld().getPersistentStateManager().set(CurseCycleState.STATE_KEY,
                    CurseCycleState.createFromNbt(original, context.getWorld().getRegistryManager()));
        }
    }

    @FunctionalInterface
    private interface Step { void run() throws Exception; }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }
}
