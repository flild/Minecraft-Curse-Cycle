package com.cursecycle;

import com.cursecycle.challenge.impl.OneTimeMenuChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CakeBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class OneTimeMenuGameTest implements FabricGameTest {
    private static final String ID = OneTimeMenuChallenge.ID;

    private static net.minecraft.util.ActionResult start(net.minecraft.server.network.ServerPlayerEntity p,
                                                       ItemStack stack, Hand hand) {
        p.setStackInHand(hand, stack);
        return stack.use(p.getWorld(), p, hand).getResult();
    }

    private static NbtCompound history(net.minecraft.server.network.ServerPlayerEntity p) {
        return CurseCycleState.getServerState(p.getServer()).getChallengeData().getCompound(ID).copy();
    }

    private static void disconnect(net.minecraft.server.network.ServerPlayerEntity p) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                .onPlayDisconnect(p.networkHandler, p.getServer());
        p.getServer().getPlayerManager().remove(p);
    }

    private static java.util.Map<?, ?> feedback() throws Exception {
        var field = OneTimeMenuChallenge.class.getDeclaredField("feedback");
        field.setAccessible(true);
        return (java.util.Map<?, ?>) field.get(null);
    }

    private static java.util.List<Object> packets(ChallengeScenario s,
                                                 net.minecraft.server.network.ServerPlayerEntity p) {
        var channel = s.channel(p);
        channel.runPendingTasks();
        channel.flush();
        var result = new java.util.ArrayList<Object>();
        Object packet;
        while ((packet = channel.readOutbound()) != null) result.add(packet);
        return result;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "menu_food")
    public void foodComponentsEffectsAndContainers(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            s.activate(ID);
            p.getHungerManager().setFoodLevel(0);
            var stew = new ItemStack(Items.SUSPICIOUS_STEW);
            stew.set(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS,
                    new net.minecraft.component.type.SuspiciousStewEffectsComponent(java.util.List.of(
                            new net.minecraft.component.type.SuspiciousStewEffectsComponent.StewEffect(
                                    net.minecraft.entity.effect.StatusEffects.SPEED, 200))));
            var bowl = stew.finishUsing(p.getWorld(), p);
            c.assertTrue(bowl.isOf(Items.BOWL) && p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SPEED),
                    "First stew retains its effects and bowl");
            int food = p.getHungerManager().getFoodLevel();
            p.clearStatusEffects();
            var otherStew = new ItemStack(Items.SUSPICIOUS_STEW);
            otherStew.set(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS,
                    new net.minecraft.component.type.SuspiciousStewEffectsComponent(java.util.List.of(
                            new net.minecraft.component.type.SuspiciousStewEffectsComponent.StewEffect(
                                    net.minecraft.entity.effect.StatusEffects.POISON, 200))));
            var rejected = otherStew.finishUsing(p.getWorld(), p);
            c.assertTrue(rejected == otherStew && rejected.isOf(Items.SUSPICIOUS_STEW) && rejected.getCount() == 1,
                    "Different stew components do not create a new food type or bowl");
            c.assertTrue(p.getStatusEffects().isEmpty(), "Rejected stew applies no effects");
            c.assertEquals(food, p.getHungerManager().getFoodLevel(), "Rejected stew adds no nutrition");
            p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.POISON, 200));
            var honey = new ItemStack(Items.HONEY_BOTTLE, 2);
            honey.finishUsing(p.getWorld(), p);
            c.assertEquals(1, honey.getCount(), "Honey consumes exactly one bottle");
            c.assertTrue(!p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.POISON), "First honey clears poison");
            int bottles = p.getInventory().count(Items.GLASS_BOTTLE);
            c.assertEquals(1, bottles, "One glass bottle for stacked honey");
            p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.POISON, 200));
            honey.finishUsing(p.getWorld(), p);
            c.assertTrue(honey.isOf(Items.HONEY_BOTTLE) && honey.getCount() == 1, "Second honey stays intact");
            c.assertTrue(p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.POISON), "Second honey cannot clear poison");
            c.assertEquals(bottles, p.getInventory().count(Items.GLASS_BOTTLE), "No duplicate glass bottle");
            var before = history(p);
            for (int i = 0; i < 2; i++) {
                c.assertTrue(new ItemStack(Items.MILK_BUCKET).finishUsing(p.getWorld(), p).isOf(Items.BUCKET), "Milk remains consumable");
                var potion = new ItemStack(Items.POTION);
                potion.set(DataComponentTypes.POTION_CONTENTS,
                        new net.minecraft.component.type.PotionContentsComponent(net.minecraft.potion.Potions.SWIFTNESS));
                c.assertTrue(potion.finishUsing(p.getWorld(), p).isOf(Items.GLASS_BOTTLE), "Potion remains consumable");
            }
            c.assertEquals(before, history(p), "Milk and potions neither record nor erase food history");
            p.clearStatusEffects();
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "menu_completion")
    public void startCompletionRacesAndVanillaRejection(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            s.activate(ID);
            p.getHungerManager().setFoodLevel(20);
            var bread = new ItemStack(Items.BREAD, 3);
            c.assertTrue(!start(p, bread, Hand.MAIN_HAND).isAccepted(), "Vanilla rejects full-hunger bread");
            c.assertTrue(history(p).isEmpty(), "Vanilla rejection records nothing");
            p.getHungerManager().setFoodLevel(1);
            c.assertTrue(start(p, bread, Hand.OFF_HAND).isAccepted(), "Offhand starts normally");
            p.clearActiveItem();
            c.assertTrue(history(p).isEmpty() && bread.getCount() == 3, "Interrupted food records and consumes nothing");
            start(p, bread, Hand.OFF_HAND);
            new ItemStack(Items.BREAD).finishUsing(p.getWorld(), p);
            int food = p.getHungerManager().getFoodLevel();
            bread.finishUsing(p.getWorld(), p);
            c.assertEquals(3, bread.getCount(), "History gained during animation is checked at completion");
            c.assertEquals(food, p.getHungerManager().getFoodLevel(), "Raced completion has no nutrition");
            p.clearActiveItem();
            s.activate("no_sprint");
            p.getHungerManager().setFoodLevel(1);
            start(p, bread, Hand.MAIN_HAND);
            s.activate(ID);
            new ItemStack(Items.BREAD).finishUsing(p.getWorld(), p);
            bread.finishUsing(p.getWorld(), p);
            c.assertEquals(3, bread.getCount(), "Activation during an existing use cannot bypass finish gate");
            s.activate("no_sprint");
            bread.finishUsing(p.getWorld(), p);
            c.assertEquals(2, bread.getCount(), "Deactivation during animation restores ordinary completion");
            c.assertTrue(history(p).isEmpty(), "Inactive completion stores no data");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "menu_lifecycle")
    public void modesReconnectionPersonalAndCleanup(TestContext c) throws Exception {
        var challenge = com.cursecycle.challenge.ChallengeRegistry.get(ID);
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            s.activate(ID);
            new ItemStack(Items.BREAD).finishUsing(p.getWorld(), p);
            var before = history(p);
            for (var mode : new net.minecraft.world.GameMode[]{net.minecraft.world.GameMode.CREATIVE, net.minecraft.world.GameMode.SPECTATOR}) {
                p.changeGameMode(mode);
                new ItemStack(Items.APPLE).finishUsing(p.getWorld(), p);
                c.assertTrue(!OneTimeMenuChallenge.blocked(p, "minecraft:bread"), "Excluded mode has no restriction");
                c.assertEquals(before, history(p), "Excluded mode neither records nor erases history");
            }
            p.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
            c.assertTrue(OneTimeMenuChallenge.blocked(p, "minecraft:bread"), "Adventure restores old restriction");
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            challenge.setEnabled(false);
            new ItemStack(Items.APPLE).finishUsing(p.getWorld(), p);
            c.assertEquals(before, history(p), "Disabled mechanic records nothing");
            challenge.setEnabled(true);
            challenge.onStart(p.getServer(), p);
            challenge.onPlayerJoin(p.getServer(), p);
            c.assertEquals(before, history(p), "Duplicate start/join never resets history");
            disconnect(p);
            var returned = s.player(p.getUuid());
            c.assertTrue(OneTimeMenuChallenge.blocked(returned, "minecraft:bread"), "Actual JOIN restores history for same UUID");
            var late = s.player();
            c.assertTrue(!OneTimeMenuChallenge.blocked(late, "minecraft:bread"), "Late UUID starts independently");
            returned.teleport(returned.getServer().getWorld(net.minecraft.world.World.NETHER), 0.5, 100, 0.5, 0, 0);
            c.assertTrue(OneTimeMenuChallenge.blocked(returned, "minecraft:bread"), "Dimension does not reset history");
            returned.teleport(c.getWorld(), 0.5, 100, 0.5, 0, 0);
            returned.onTeleportationDone();
            returned = s.respawn(returned, true);
            c.assertTrue(OneTimeMenuChallenge.blocked(returned, "minecraft:bread"), "Alive replacement retains UUID history");
            disconnect(returned);
            s.activate("no_sprint");
            returned = s.player(p.getUuid());
            c.assertTrue(!OneTimeMenuChallenge.blocked(returned, "minecraft:bread") && history(returned).isEmpty(), "Offline-ended history never revives");
            s.state.getPersonalActiveChallengeIds(returned.getUuid()).add(ID);
            s.state.markDirty();
            new ItemStack(Items.BREAD).finishUsing(returned.getWorld(), returned);
            c.assertTrue(OneTimeMenuChallenge.blocked(returned, "minecraft:bread") && !OneTimeMenuChallenge.blocked(late, "minecraft:bread"), "PERSONAL assignment is isolated");
            disconnect(returned);
        } finally {
            challenge.setEnabled(true);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "menu_cache")
    public void feedbackCacheEndsWithActivation(TestContext c) throws Exception {
        var challenge = com.cursecycle.challenge.ChallengeRegistry.get(ID);
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            for (int i = 0; i < 32; i++) {
                s.activate(ID);
                new ItemStack(Items.BREAD).finishUsing(p.getWorld(), p);
                OneTimeMenuChallenge.blocked(p, "minecraft:bread");
                c.assertTrue(feedback().containsKey(p.getUuid()), "Rejected use owns a feedback entry");
                s.activate("no_sprint");
                c.assertTrue(!feedback().containsKey(p.getUuid()), "Stop releases UUID before later inactive disconnect");
            }
            s.activate(ID);
            new ItemStack(Items.BREAD).finishUsing(p.getWorld(), p);
            OneTimeMenuChallenge.blocked(p, "minecraft:bread");
            challenge.setEnabled(false);
            c.assertTrue(feedback().isEmpty(), "Disable releases feedback without relying on active dispatch");
            disconnect(p);
            c.assertTrue(feedback().isEmpty(), "Inactive disconnect retains no feedback entries");
        } finally {
            challenge.setEnabled(true);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void registryIdentityCakeAndPersistence(TestContext context) throws Exception {
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            scenario.activate("one_time_menu");
            player.getHungerManager().setFoodLevel(4);
            var bread = new ItemStack(Items.BREAD, 2);
            bread.finishUsing(context.getWorld(), player);
            context.assertEquals(1, bread.getCount(), "First bread is consumed");
            context.assertTrue(OneTimeMenuChallenge.blocked(player, "minecraft:bread"), "Registry ID recorded");
            bread.set(DataComponentTypes.CUSTOM_NAME, Text.literal("other bread"));
            int food = player.getHungerManager().getFoodLevel();
            bread.finishUsing(context.getWorld(), player);
            context.assertEquals(1, bread.getCount(), "Renaming cannot bypass repeat prohibition");
            context.assertEquals(food, player.getHungerManager().getFoodLevel(), "No nutrition on rejected completion");
            context.assertTrue(bread.use(context.getWorld(), player, Hand.MAIN_HAND).getResult().equals(net.minecraft.util.ActionResult.FAIL), "Start also rejected");
            new ItemStack(Items.BEEF).finishUsing(context.getWorld(), player);
            new ItemStack(Items.COOKED_BEEF).finishUsing(context.getWorld(), player);
            context.assertTrue(OneTimeMenuChallenge.blocked(player, "minecraft:beef"), "Raw type recorded");
            context.assertTrue(OneTimeMenuChallenge.blocked(player, "minecraft:cooked_beef"), "Cooked type separate");
            context.assertTrue(!OneTimeMenuChallenge.blocked(player, "minecraft:milk_bucket"), "Milk not recorded");
            var cake = context.getAbsolutePos(new BlockPos(2, 2, 2));
            context.getWorld().setBlockState(cake.down(), Blocks.STONE.getDefaultState());
            context.getWorld().setBlockState(cake, Blocks.CAKE.getDefaultState());
            player.getHungerManager().setFoodLevel(4);
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            var hit = new BlockHitResult(Vec3d.ofCenter(cake), Direction.UP, cake, false);
            player.interactionManager.interactBlock(player, context.getWorld(), ItemStack.EMPTY, Hand.MAIN_HAND, hit);
            player.interactionManager.interactBlock(player, context.getWorld(), ItemStack.EMPTY, Hand.MAIN_HAND, hit);
            context.assertEquals(1, context.getWorld().getBlockState(cake).get(CakeBlock.BITES), "Only one cake bite");
            var persistence = context.getWorld().getPersistentStateManager();
            scenario.state.markDirty();
            persistence.save();
            var disk = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), context.getWorld().getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, loaded);
            context.assertTrue(OneTimeMenuChallenge.blocked(player, "minecraft:bread"), "History survives actual disk roundtrip");
            var late = scenario.player();
            context.assertTrue(!OneTimeMenuChallenge.blocked(late, "minecraft:bread"), "Independent UUID history");
            player.kill();
            player = scenario.respawn(player, false);
            context.assertTrue(OneTimeMenuChallenge.blocked(player, "minecraft:bread"), "Death does not reset history");
            com.cursecycle.state.CycleManager.activateChallenge(player.getServer(), loaded, "no_sprint");
            com.cursecycle.state.CycleManager.activateChallenge(player.getServer(), loaded, "one_time_menu");
            context.assertTrue(!OneTimeMenuChallenge.blocked(player, "minecraft:bread"), "New activation resets history");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "menu_candles")
    public void cakeCandleAndFullHunger(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            s.activate(ID);
            var pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
            c.getWorld().setBlockState(pos.down(), Blocks.STONE.getDefaultState());
            c.getWorld().setBlockState(pos, Blocks.CANDLE_CAKE.getDefaultState());
            var hit = new BlockHitResult(Vec3d.ofCenter(pos).add(0, 0.4, 0), Direction.UP, pos, false);
            var lighter = new ItemStack(Items.FLINT_AND_STEEL);
            p.setStackInHand(Hand.MAIN_HAND, lighter);
            p.interactionManager.interactBlock(p, c.getWorld(), lighter, Hand.MAIN_HAND, hit);
            c.assertTrue(c.getWorld().getBlockState(pos).get(net.minecraft.block.CandleCakeBlock.LIT), "Candle lights normally");
            c.assertTrue(history(p).isEmpty(), "Lighting is not eating");
            p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            p.interactionManager.interactBlock(p, c.getWorld(), ItemStack.EMPTY, Hand.MAIN_HAND, hit);
            c.assertTrue(!c.getWorld().getBlockState(pos).get(net.minecraft.block.CandleCakeBlock.LIT), "Candle extinguishes normally");
            c.assertTrue(history(p).isEmpty(), "Extinguishing is not eating");
            p.getHungerManager().setFoodLevel(20);
            p.interactionManager.interactBlock(p, c.getWorld(), ItemStack.EMPTY, Hand.MAIN_HAND, hit);
            c.assertTrue(history(p).isEmpty(), "Vanilla-rejected cake is not recorded");
            p.getHungerManager().setFoodLevel(4);
            p.interactionManager.interactBlock(p, c.getWorld(), ItemStack.EMPTY, Hand.MAIN_HAND, hit);
            c.assertTrue(c.getWorld().getBlockState(pos).isOf(Blocks.CAKE), "Eating candle cake follows ordinary cake path");
            c.assertEquals(1, c.getWorld().getBlockState(pos).get(CakeBlock.BITES), "One bite on candle cake");
            int food = p.getHungerManager().getFoodLevel();
            c.getWorld().setBlockState(pos, Blocks.CAKE.getDefaultState());
            p.interactionManager.interactBlock(p, c.getWorld(), ItemStack.EMPTY, Hand.MAIN_HAND, hit);
            c.assertEquals(0, c.getWorld().getBlockState(pos).get(CakeBlock.BITES), "Another whole cake shares one ID");
            c.assertEquals(food, p.getHungerManager().getFoodLevel(), "Rejected cake adds no nutrition");
            c.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState());
            c.getWorld().setBlockState(pos.down(), Blocks.AIR.getDefaultState());
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "menu_pairs")
    public void independentChallengePairs(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            for (String pair : new String[]{"no_sprint", "infinite_diet", "no_healing", "inventory_lockdown"}) {
                p.getHungerManager().setFoodLevel(1);
                p.setHealth(10);
                s.activate("double_challenge:" + ID + "," + pair);
                var bread = new ItemStack(Items.BREAD, 2);
                bread.finishUsing(p.getWorld(), p);
                c.assertEquals(1, bread.getCount(), "First food consumed with " + pair);
                int food = p.getHungerManager().getFoodLevel();
                bread.finishUsing(p.getWorld(), p);
                c.assertEquals(1, bread.getCount(), "Repeat blocked with " + pair);
                c.assertEquals(food, p.getHungerManager().getFoodLevel(), "No repeat nutrition with " + pair);
                if (pair.equals("infinite_diet")) c.assertEquals(1, food, "Diet prevents nutrition but not food history");
                if (pair.equals("no_healing")) {
                    p.heal(5);
                    c.assertEquals(10f, p.getHealth(), "No-healing remains independent");
                }
                if (pair.equals("no_sprint")) {
                    p.setSprinting(true);
                    c.assertTrue(!p.isSprinting(), "Sprint remains blocked");
                }
                if (pair.equals("inventory_lockdown"))
                {
                    p.getInventory().setStack(9, new ItemStack(Items.APPLE));
                    p.playerScreenHandler.onSlotClick(9, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, p);
                    c.assertTrue(p.getInventory().getStack(9).isOf(Items.APPLE)
                            && p.playerScreenHandler.getCursorStack().isEmpty(), "Real click still respects locked inventory slots");
                }
                s.activate(pair);
                bread.finishUsing(p.getWorld(), p);
                c.assertTrue(bread.isEmpty(), "Removing menu permits food with " + pair + " retained");
                c.assertTrue(s.state.getActiveChallengeIds().contains(pair), "Other challenge remains active");
                if (pair.equals("no_sprint")) {
                    p.setSprinting(true);
                    c.assertTrue(!p.isSprinting(), "Removing menu does not remove no_sprint");
                }
                if (pair.equals("no_healing")) {
                    p.heal(5);
                    c.assertEquals(10f, p.getHealth(), "Removing menu does not remove no_healing");
                }
            }
            s.activate("double_challenge:" + ID + ",hotbar_shuffle");
            p.getHungerManager().setFoodLevel(1);
            new ItemStack(Items.BREAD).finishUsing(p.getWorld(), p);
            for (int i = 0; i < 9; i++) p.getInventory().setStack(i, new ItemStack(i == 0 ? Items.APPLE : Items.BREAD, i + 2));
            p.getInventory().selectedSlot = 0;
            var apple = p.getMainHandStack();
            start(p, apple, Hand.MAIN_HAND);
            var shuffle = com.cursecycle.challenge.ChallengeRegistry.get("hotbar_shuffle");
            try {
                var timers = shuffle.getClass().getDeclaredField("timers");
                timers.setAccessible(true);
                var serverTimers = (java.util.Map<?, ?>) ((java.util.Map<?, ?>) timers.get(shuffle)).get(p.getServer());
                var timer = serverTimers.get(p.getUuid());
                var clockAccessor = timer.getClass().getDeclaredMethod("clock");
                clockAccessor.setAccessible(true);
                var clock = clockAccessor.invoke(timer);
                var next = clock.getClass().getDeclaredField("next");
                next.setAccessible(true);
                next.setLong(clock, p.getServer().getTicks());
                // Force an actual shuffle away from the actively used stack, with bounded test-only seed search.
                long seed = 0;
                for (; seed < 1000; seed++) {
                    var rng = net.minecraft.util.math.random.Random.create(seed);
                    var indices = new java.util.ArrayList<Integer>();
                    for (int i = 0; i < 9; i++) indices.add(i);
                    var shuffled = com.cursecycle.challenge.impl.HotbarShuffleChallenge.shuffled(indices, Integer::equals, rng::nextInt);
                    if (shuffled.getFirst() != 0) break;
                }
                c.assertTrue(seed < 1000, "Bounded seed fixture found");
                p.getRandom().setSeed(seed);
                shuffle.onTick(p.getServer(), p);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            c.assertTrue(!p.isUsingItem(), "Real hotbar shuffle cancels replaced active food");
            c.assertTrue(!OneTimeMenuChallenge.blocked(p, "minecraft:apple"), "Interrupted shuffled food records nothing");
            c.assertTrue(start(p, p.getMainHandStack(), Hand.MAIN_HAND) == net.minecraft.util.ActionResult.FAIL, "Replacement bread checked by registry ID");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "menu_real_feedback", tickLimit = 100)
    public void feedbackUsesFortyRealServerTicks(TestContext c) {
        var s = new ChallengeScenario(c);
        var p = s.player();
        int[] started = {-1}, messages = {0};
        boolean[] closed = {false};
        c.runAtEveryTick(() -> {
            if (closed[0] || c.getTick() < 1) return;
            try {
                if (started[0] < 0) {
                    var dispatcher = p.getServer().getCommandManager().getDispatcher();
                    for (String command : new String[]{"fdc list", "fdc activate " + ID, "fdc active"})
                        c.assertTrue(dispatcher.execute(command, p.getServer().getCommandSource().withLevel(2)) > 0, "Command " + command);
                    new ItemStack(Items.BREAD).finishUsing(p.getWorld(), p);
                    packets(s, p);
                    started[0] = p.getServer().getTicks();
                }
                int elapsed = p.getServer().getTicks() - started[0];
                var bread = new ItemStack(Items.BREAD);
                start(p, bread, Hand.MAIN_HAND);
                start(p, bread, Hand.OFF_HAND);
                bread.finishUsing(p.getWorld(), p);
                for (Object packet : packets(s, p))
                    if (packet instanceof net.minecraft.network.packet.s2c.play.GameMessageS2CPacket overlay
                            && overlay.overlay() && overlay.content().getString().equals("Этот вид уже съеден")) messages[0]++;
                c.assertEquals(elapsed < 40 ? 1 : 2, messages[0], "One actionbar at 0 and 40 despite repeated hooks");
                c.assertEquals(1, bread.getCount(), "Feedback never consumes food");
                if (elapsed >= 45) {
                    closed[0] = true;
                    disconnect(p);
                    s.close();
                    c.complete();
                }
            } catch (Throwable failure) {
                closed[0] = true;
                s.close();
                throw failure instanceof RuntimeException runtime ? runtime : new RuntimeException(failure);
            }
        });
    }
}
