package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

public class OneHeartGameTest implements FabricGameTest {
    private static void disconnect(ServerPlayerEntity player) {
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.networkHandler, player.getServer());
        player.getServer().getPlayerManager().remove(player);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "one_heart_sources")
    public void externalModifiersFoodHealingAndNoDamageOnCut(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            var max = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            var modifier = Identifier.of("validation", "external_health");
            max.setBaseValue(30);
            max.addPersistentModifier(new EntityAttributeModifier(modifier, 10, EntityAttributeModifier.Operation.ADD_VALUE));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, 900, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 800, 0));
            c.assertEquals(48F, p.getMaxHealth(), "Inactive external effective maximum");
            c.assertEquals(4F, p.getAbsorptionAmount(), "Inactive absorption control");
            p.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 7));
            p.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            var position = p.getPos();
            var boost = p.getStatusEffect(StatusEffects.HEALTH_BOOST);
            var absorption = p.getStatusEffect(StatusEffects.ABSORPTION);
            // PERSONAL overlays make both confirmed-damage consequences eligible during the cut.
            s.state.getPersonalActiveChallengeIds(p.getUuid()).addAll(java.util.List.of("damage_slot_drop", "damage_teleport"));
            s.activate("one_heart");
            c.assertEquals(position, p.getPos(), "Activation is not teleport-triggering damage");
            c.assertEquals(7, p.getInventory().getStack(0).getCount(), "Cut does not drop inventory");
            c.assertEquals(0, p.getEquippedStack(EquipmentSlot.CHEST).getDamage(), "Cut does not wear armor");
            c.assertEquals(0F, p.getAbsorptionAmount(), "Existing golden hearts removed");
            c.assertTrue(p.getStatusEffect(StatusEffects.HEALTH_BOOST) == boost && boost.getDuration() == 900,
                    "Foreign Health Boost object and clock preserved");
            c.assertTrue(p.getStatusEffect(StatusEffects.ABSORPTION) == absorption && absorption.getDuration() == 800,
                    "Foreign Absorption object and clock preserved");
            s.state.getPersonalActiveChallenges().clear();
            p.setHealth(1);
            p.heal(100);
            c.assertEquals(2F, p.getHealth(), "Healing allowed up to cap");
            p.setHealth(1);
            var potion = new ItemStack(Items.POTION);
            potion.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS,
                    new net.minecraft.component.type.PotionContentsComponent(net.minecraft.potion.Potions.HEALING));
            c.assertTrue(potion.finishUsing(c.getWorld(), p).isOf(Items.GLASS_BOTTLE), "Real drinking returns bottle");
            c.assertEquals(2F, p.getHealth(), "Real potion heals within cap");
            p.getHungerManager().setFoodLevel(10);
            var apple = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
            apple.finishUsing(c.getWorld(), p);
            c.assertTrue(apple.isEmpty(), "Food remains usable");
            c.assertTrue(p.hasStatusEffect(StatusEffects.RESISTANCE) && p.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)
                    && p.hasStatusEffect(StatusEffects.REGENERATION) && p.hasStatusEffect(StatusEffects.ABSORPTION),
                    "Non-forbidden apple effects preserved");
            c.assertEquals(14, p.getHungerManager().getFoodLevel(), "Food restores hunger");
            c.assertEquals(0F, p.getAbsorptionAmount(), "Enchanted apple grants no golden hearts");
            max.setBaseValue(40);
            c.assertEquals(2F, p.getMaxHealth(), "Changed current attributes cannot lift cap");
            p.setHealth(1);
            s.state.setDirty(false);
            for (int i = 0; i < 100; i++) {
                ChallengeRegistry.get("one_heart").onStart(p.getServer(), p);
                p.setAbsorptionAmount(30);
            }
            c.assertEquals(1F, p.getHealth(), "Repeated reconciliation never heals");
            c.assertTrue(!s.state.isDirty() && s.state.getChallengeData().isEmpty(), "No own persistent snapshot or repeated dirty writes");
            c.assertTrue(s.state.getPersonalActiveChallenges().isEmpty(), "Read gates create no UUID records");
            s.activate("no_sprint");
            c.assertEquals(58F, p.getMaxHealth(), "Stop uses latest base, modifier and Health Boost");
            c.assertTrue(max.getModifier(modifier) != null, "Foreign modifier survives");
            c.assertEquals(1F, p.getHealth(), "Stop never heals");
            c.assertEquals(0F, p.getAbsorptionAmount(), "Stop never restores lost absorption");
            p.setAbsorptionAmount(4);
            c.assertEquals(4F, p.getAbsorptionAmount(), "Future vanilla absorption allowed");
            new ItemStack(Items.MILK_BUCKET).finishUsing(c.getWorld(), p);
            c.assertEquals(50F, p.getMaxHealth(), "Milk removes only effects, external modifier remains");
            max.removeModifier(modifier);
            max.setBaseValue(1);
            s.activate("one_heart");
            c.assertEquals(1F, p.getMaxHealth(), "Never raises a smaller maximum");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "one_heart_disk")
    public void modesPersonalAndActualDiskReconnect(TestContext c) throws Exception {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            var q = s.player();
            var server = p.getServer();
            p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(40);
            p.setHealth(1);
            var dispatcher = server.getCommandManager().getDispatcher();
            for (String cmd : new String[]{"fdc list", "fdc activate one_heart", "fdc active"})
                c.assertTrue(dispatcher.execute(cmd, server.getCommandSource().withLevel(2)) > 0, "OP2 " + cmd);
            var late = s.player();
            // EmbeddedChannel has no real network tick to fire Fabric's deferred play JOIN.
            ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(late.networkHandler, null, server);
            for (var player : new ServerPlayerEntity[]{p, q, late}) {
                c.assertEquals(2F, player.getMaxHealth(), "Includes late join");
                c.assertTrue(player.getHealth() <= 2, "Join health clamped before play");
            }
            c.assertEquals(1F, p.getHealth(), "Activation never raises low HP");
            for (var mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
                p.changeGameMode(mode);
                c.assertEquals(40F, p.getMaxHealth(), "Excluded mode immediately releases maximum");
                p.setHealth(30);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 900));
                p.setAbsorptionAmount(4);
                c.assertEquals(4F, p.getAbsorptionAmount(), "Excluded mode allows absorption");
                p.changeGameMode(GameMode.ADVENTURE);
                ChallengeRegistry.get("one_heart").onTick(server, p);
                c.assertEquals(2F, p.getHealth(), "Adventure reconciliation clamps current HP");
                c.assertEquals(0F, p.getAbsorptionAmount(), "Adventure clears absorption");
            }
            p.changeGameMode(GameMode.SURVIVAL);
            p.setHealth(1);
            var origin = p.getPos();
            p.teleport(server.getWorld(net.minecraft.world.World.NETHER), .5, 80, .5, 0, 0);
            p.onTeleportationDone();
            c.assertEquals(2F, p.getMaxHealth(), "Cap in Nether");
            p.teleport(c.getWorld(), origin.x, origin.y, origin.z, 0, 0);
            p.onTeleportationDone();
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, 700, 1));
            var uuid = p.getUuid();
            disconnect(p);
            var pm = server.getOverworld().getPersistentStateManager();
            pm.save();
            var disk = pm.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager());
            pm.set(CurseCycleState.STATE_KEY, loaded);
            p = s.player(uuid);
            ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(p.networkHandler, null, server);
            c.assertEquals(1F, p.getHealth(), "Actual playerdata load does not heal");
            c.assertEquals(2F, p.getMaxHealth(), "Active PersistentState reload caps before play");
            c.assertEquals(40D, p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).getBaseValue(), "External base persisted");
            c.assertEquals(700, p.getStatusEffect(StatusEffects.HEALTH_BOOST).getDuration(), "Foreign clock persisted");
            c.assertEquals(2F, q.getMaxHealth(), "Other UUID unaffected by reconnect");
            var challenge = ChallengeRegistry.get("one_heart");
            challenge.setEnabled(false);
            try { c.assertEquals(48F, p.getMaxHealth(), "Disabled gate releases cap immediately"); }
            finally { challenge.setEnabled(true); }
            loaded.getActiveChallengeIds().clear();
            loaded.getPersonalActiveChallengeIds(uuid).add("one_heart");
            c.assertEquals(2F, p.getMaxHealth(), "PERSONAL gate");
            c.assertEquals(20F, q.getMaxHealth(), "PERSONAL does not affect other UUID");
            loaded.getPersonalActiveChallenges().clear();
            for (int i = 0; i < 32; i++) {
                CycleManager.activateChallenge(server, loaded, "one_heart");
                CycleManager.activateChallenge(server, loaded, "one_heart");
                c.assertEquals(1F, p.getHealth(), "New activation never heals");
                c.assertEquals(2F, p.getMaxHealth(), "Repeated activation does not stack");
                CycleManager.activateChallenge(server, loaded, "no_sprint");
                c.assertEquals(48F, p.getMaxHealth(), "Every end releases own cap");
            }
            CycleManager.activateChallenge(server, loaded, "one_heart");
            disconnect(p);
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            pm.save();
            disk = pm.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            pm.set(CurseCycleState.STATE_KEY, CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager()));
            p = s.player(uuid);
            c.assertEquals(48F, p.getMaxHealth(), "Offline end has no stale cap to restore");
            c.assertEquals(1F, p.getHealth(), "Offline end does not heal");
            c.assertEquals(0F, p.getAbsorptionAmount(), "Offline end does not restore absorption");
            p.heal(2);
            c.assertEquals(3F, p.getHealth(), "Future healing can exceed two after ending");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "one_heart_pairs")
    public void independentSprintHealingAndExternalEffects(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            var q = s.player();
            s.activate("double_challenge:one_heart,no_sprint");
            for (var player : new ServerPlayerEntity[]{p, q}) {
                c.assertEquals(2F, player.getMaxHealth(), "Both players capped");
                player.setSprinting(true);
                ChallengeRegistry.get("no_sprint").onTick(p.getServer(), player);
                c.assertTrue(!player.isSprinting(), "Both players cannot sprint");
            }
            s.activate("no_sprint");
            c.assertEquals(20F, p.getMaxHealth(), "Own maximum released independently");
            p.setSprinting(true);
            ChallengeRegistry.get("no_sprint").onTick(p.getServer(), p);
            c.assertTrue(!p.isSprinting(), "Remaining sprint restriction works");
            s.activate("double_challenge:one_heart,no_healing");
            p.setHealth(1);
            p.heal(1);
            c.assertEquals(1F, p.getHealth(), "Other challenge forbids healing below cap");
            s.activate("no_healing");
            p.heal(20);
            c.assertEquals(1F, p.getHealth(), "Removing cap preserves healing ban");
            c.assertEquals(20F, p.getMaxHealth(), "No own limit left");
            s.activate("one_heart");
            p.heal(20);
            c.assertEquals(2F, p.getHealth(), "Removing healing ban preserves cap");
            for (var player : new ServerPlayerEntity[]{p, q})
                player.getRandom().setSeed(seedFor(3));
            s.activate("double_challenge:one_heart,random_potion");
            c.assertEquals(2F, p.getHealth(), "Actual instant-health random result cannot lift cap");
            for (var player : new ServerPlayerEntity[]{p, q})
                player.getRandom().setSeed(seedFor(7));
            s.activate("double_challenge:one_heart,random_potion");
            c.assertTrue(p.hasStatusEffect(StatusEffects.RESISTANCE), "Other random result exists");
            s.activate("random_potion");
            c.assertEquals(20F, p.getMaxHealth(), "Remaining potion permits normal maximum");
            c.assertTrue(p.getStatusEffects().size() > 0, "Remaining potion mechanic still works");
            s.activate("double_challenge:one_heart,all_debuffs");
            c.assertEquals(2F, p.getMaxHealth(), "All debuffs cannot lift cap");
            c.assertTrue(p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Debuff mechanic still active");
            s.activate("all_debuffs");
            c.assertEquals(20F, p.getMaxHealth(), "Cap removed while debuffs remain");
            c.assertTrue(p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Other owner survives");
        }
        c.complete();
    }

    private static long seedFor(int result) {
        for (long seed = 0; ; seed++) if (net.minecraft.util.math.random.Random.create(seed).nextInt(19) == result) return seed;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "one_heart_heartbeat", tickLimit = 180)
    public void realMissedHeartbeatAndIndependentCleanup(TestContext c) {
        var s = new ChallengeScenario(c);
        try {
            var p = s.player();
            var q = s.player();
            for (var player : new ServerPlayerEntity[]{p, q}) {
                for (int i = 0; i < 61; i++) player.playerTick();
                player.setNoGravity(true);
                player.setPosition(player.getX(), 200, player.getZ());
                player.getHungerManager().setFoodLevel(18);
            }
            p.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            s.activate("double_challenge:one_heart,heartbeat");
            int start = p.getServer().getTicks();
            c.waitAndRun(127, () -> {
                try {
                    c.assertEquals(127, p.getServer().getTicks() - start, "127 real server ticks elapsed");
                    c.assertEquals(1F, p.getHealth(), "Missed beat saves with totem under cap");
                    c.assertTrue(p.getOffHandStack().isEmpty(), "Totem actually consumed");
                    c.assertEquals(0F, p.getAbsorptionAmount(), "No totem golden hearts");
                    c.assertTrue(!q.isAlive(), "Same missed beat kills unprotected one-heart player");
                    s.activate("heartbeat");
                    c.assertEquals(20F, p.getMaxHealth(), "Removing own cap preserves heartbeat");
                    c.assertEquals(1F, p.getHealth(), "Ending cap does not heal");
                    c.complete();
                } finally { s.close(); }
            });
        } catch (Throwable failure) { s.close(); throw failure; }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void effectiveCapAbsorptionAndNoHealing(TestContext context) {
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            for (int i = 0; i < 61; i++) player.tick();
            var max = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            max.setBaseValue(30);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1000));
            scenario.activate("one_heart");
            context.assertEquals(2F, player.getMaxHealth(), "Effective maximum two");
            context.assertEquals(2F, player.getHealth(), "Current health clamped");
            context.assertEquals(0F, player.getAbsorptionAmount(), "Existing absorption removed");
            context.assertEquals(30D, max.getBaseValue(), "External base attribute unchanged");
            player.setAbsorptionAmount(12);
            context.assertEquals(0F, player.getAbsorptionAmount(), "New absorption blocked");
            player.setHealth(1);
            ChallengeRegistry.get("one_heart").onStart(player.getServer(), player);
            context.assertEquals(1F, player.getHealth(), "Repeated start never heals");
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, 1000, 1));
            max.setBaseValue(40);
            context.assertEquals(2F, player.getMaxHealth(), "Health boost cannot raise the cap");
            scenario.activate("no_sprint");
            context.assertEquals(48F, player.getMaxHealth(), "Latest external attributes restored without snapshot");
            context.assertEquals(1F, player.getHealth(), "No healing on ending");
            context.assertEquals(0F, player.getAbsorptionAmount(), "No absorption restoration on ending");
            context.assertTrue(player.hasStatusEffect(StatusEffects.HEALTH_BOOST), "External effect preserved");
            player.removeStatusEffect(StatusEffects.HEALTH_BOOST);
            max.setBaseValue(1);
            scenario.activate("one_heart");
            context.assertEquals(1F, player.getMaxHealth(), "Does not raise a smaller external maximum");
            scenario.activate("no_sprint");
            max.setBaseValue(20);
            player.setHealth(10);
            scenario.activate("double_challenge:one_heart,no_healing");
            player.setHealth(1);
            player.heal(10);
            context.assertEquals(1F, player.getHealth(), "No healing still blocks growth below cap");
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().generic(), 100);
            context.assertEquals(1F, player.getHealth(), "Totem still saves within cap");
            context.assertEquals(0F, player.getAbsorptionAmount(), "Totem grants no extra absorption");
            player.kill();
            player = scenario.respawn(player, false);
            context.assertEquals(2F, player.getHealth(), "New life initializes at one heart before play");
            context.assertEquals(2F, player.getMaxHealth(), "Respawn cap active");
        }
        context.complete();
    }
}
