package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.GillsChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.Potions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;

public class NoHealingGameTest implements FabricGameTest {
    private static void disconnect(ServerPlayerEntity p) {
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler, p.getServer());
        p.getServer().getPlayerManager().remove(p);
    }

    private static void tickEffects(ServerPlayerEntity p, int count) {
        for (int i = 0; i < count; i++) { p.tick(); p.playerTick(); }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_healing_copy")
    public void replacementCopiesHealthAboveDefaultMaximum(TestContext c) {
        var server = c.getWorld().getServer();
        var mode = server.getDefaultGameMode();
        server.setDefaultGameMode(GameMode.SURVIVAL);
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(40);
            p.setHealth(30);
            s.activate("no_healing");
            p = s.respawn(p, true);
            c.assertEquals(40F, p.getMaxHealth(), "Vanilla base max health is copied");
            c.assertEquals(30F, p.getHealth(), "Live replacement copies HP above constructor's default 20");
            p.heal(2);
            c.assertEquals(30F, p.getHealth(), "Restriction resumes after live copy");
            p.kill();
            p = s.respawn(p, false);
            c.assertEquals(40F, p.getHealth(), "New life initializes the actual vanilla max, including base modifiers");
            p.setHealth(24);
            p.heal(2);
            c.assertEquals(24F, p.getHealth(), "New life resumes restriction after damage");
            c.assertTrue(!((com.cursecycle.challenge.HealthInitialization) p).cursecycle$copyingHealth(), "Copy scope is closed");
            s.activate("double_challenge:no_healing,one_heart");
            p.setHealth(1);
            p = s.respawn(p, true);
            c.assertEquals(1F, p.getHealth(), "Copy exemption preserves one-heart live HP");
            c.assertEquals(2F, p.getMaxHealth(), "Copy exemption does not bypass one-heart cap");
            p.heal(1);
            c.assertEquals(1F, p.getHealth(), "Pair blocks healing immediately after copy");
            p.kill();
            p = s.respawn(p, false);
            c.assertEquals(2F, p.getHealth(), "Pair initializes new life within one-heart cap");
        } finally { server.setDefaultGameMode(mode); }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_healing_sources")
    public void vanillaPotionFoodRegenerationAndDamagePaths(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            tickEffects(p, 61); // Expire vanilla login protection for the damage assertions.
            p.setHealth(6);
            p.heal(2);
            c.assertEquals(8F, p.getHealth(), "Inactive positive control");
            var controlPotion = new ItemStack(Items.POTION);
            controlPotion.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.HEALING));
            controlPotion.finishUsing(c.getWorld(), p);
            c.assertEquals(12F, p.getHealth(), "Inactive drinking positive control");
            p.setHealth(8);
            var rules = c.getWorld().getGameRules();
            boolean natural = rules.getBoolean(net.minecraft.world.GameRules.NATURAL_REGENERATION);
            rules.get(net.minecraft.world.GameRules.NATURAL_REGENERATION).set(true, p.getServer());
            try {
                s.activate("no_healing");
                c.assertEquals(8F, p.getHealth(), "Activation does not alter HP");
                p.getHungerManager().setFoodLevel(20);
                p.getHungerManager().setSaturationLevel(20);
                for (int i = 0; i < 100; i++) p.getHungerManager().update(p);
                c.assertEquals(8F, p.getHealth(), "Fast natural regeneration blocked");
                p.getHungerManager().setFoodLevel(20);
                p.getHungerManager().setSaturationLevel(0);
                for (int i = 0; i < 160; i++) p.getHungerManager().update(p);
                c.assertEquals(8F, p.getHealth(), "Slow natural regeneration blocked");
                var potion = new ItemStack(Items.POTION);
                potion.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.HEALING));
                var bottle = potion.finishUsing(c.getWorld(), p);
                c.assertTrue(bottle.isOf(Items.GLASS_BOTTLE), "Drinking consumes potion and returns bottle");
                c.assertEquals(8F, p.getHealth(), "Drunk healing blocked");
                var pos = c.getAbsolutePos(new net.minecraft.util.math.BlockPos(3, 2, 3));
                p.teleport(c.getWorld(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
                p.onTeleportationDone();
                // Call the real collision entrypoint, rather than only the instant-effect helper.
                for (var item : new net.minecraft.item.Item[]{Items.SPLASH_POTION, Items.LINGERING_POTION}) {
                  var challenge = ChallengeRegistry.get("no_healing");
                  try { for (boolean restricted : new boolean[]{false, true}) {
                    challenge.setEnabled(restricted);
                    p.setHealth(8);
                    var projectile = new net.minecraft.entity.projectile.thrown.PotionEntity(c.getWorld(), p) {
                        void collide() { super.onCollision(new EntityHitResult(p)); }
                    };
                    var stack = new ItemStack(item);
                    stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.HEALING));
                    projectile.setItem(stack);
                    projectile.setPosition(p.getPos());
                    projectile.collide();
                    c.assertTrue(projectile.isRemoved(), "Vanilla potion shatters");
                    if (item == Items.LINGERING_POTION) {
                        var clouds = c.getWorld().getEntitiesByClass(AreaEffectCloudEntity.class,
                                p.getBoundingBox().expand(4), cloud -> true);
                        c.assertEquals(1, clouds.size(), "Lingering collision creates real cloud");
                        var cloud = clouds.getFirst();
                        cloud.setWaitTime(0);
                        cloud.setRadiusOnUse(0);
                        for (int i = 1; i <= 10; i++) { cloud.age = i; cloud.tick(); }
                        c.assertTrue(cloud.age >= 10, "Cloud executes vanilla area lookup/application");
                        cloud.discard();
                    }
                    if (restricted) c.assertEquals(8F, p.getHealth(), "Splash/lingering health remains unchanged");
                    else c.assertTrue(p.getHealth() > 8, "Same splash/cloud path heals in inactive control");
                  } } finally { challenge.setEnabled(true); }
                }
                var cow = c.spawnEntity(net.minecraft.entity.EntityType.COW, 5, 2, 5);
                try {
                    cow.setAiDisabled(true);
                    cow.setHealth(5);
                    cow.heal(2);
                    c.assertEquals(7F, cow.getHealth(), "Non-player healing is unaffected");
                } finally { cow.discard(); }
                p.getHungerManager().setFoodLevel(10);
                p.getHungerManager().setSaturationLevel(0);
                var apple = new ItemStack(Items.GOLDEN_APPLE);
                apple.finishUsing(c.getWorld(), p);
                c.assertTrue(apple.isEmpty(), "Apple consumed");
                c.assertEquals(14, p.getHungerManager().getFoodLevel(), "Food still restores hunger");
                c.assertTrue(p.getHungerManager().getSaturationLevel() > 0, "Food still restores saturation");
                c.assertEquals(4F, p.getAbsorptionAmount(), "Golden hearts allowed");
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, 600, 1));
                c.assertEquals(28F, p.getMaxHealth(), "Health boost changes maximum");
                tickEffects(p, 60);
                c.assertEquals(8F, p.getHealth(), "Actual regeneration pulses cannot heal");
                c.assertTrue(p.hasStatusEffect(StatusEffects.REGENERATION), "Foreign regeneration preserved");
                p.timeUntilRegen = 0;
                p.damage(p.getDamageSources().generic(), 2);
                c.assertEquals(8F, p.getHealth(), "Absorption absorbs ordinary damage");
                c.assertEquals(2F, p.getAbsorptionAmount(), "Absorption is spent normally");
                p.setAbsorptionAmount(0);
                p.timeUntilRegen = 0;
                p.damage(p.getDamageSources().generic(), 2);
                c.assertEquals(6F, p.getHealth(), "Ordinary health damage is allowed");
                s.activate("no_sprint");
                c.assertEquals(6F, p.getHealth(), "End never compensates lost HP");
                tickEffects(p, 50);
                c.assertTrue(p.getHealth() > 6, "Future real regeneration resumes after ending");
                c.assertTrue(rules.getBoolean(net.minecraft.world.GameRules.NATURAL_REGENERATION), "Gamerule unchanged");
            } finally { rules.get(net.minecraft.world.GameRules.NATURAL_REGENERATION).set(natural, p.getServer()); }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_healing_lifecycle")
    public void diskJoinModesPersonalDisableAndRepeatedActivation(TestContext c) throws Exception {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            var q = s.player();
            var server = p.getServer();
            p.setHealth(7);
            q.setHealth(11);
            var dispatcher = server.getCommandManager().getDispatcher();
            for (String command : new String[]{"fdc list", "fdc activate no_healing", "fdc active"})
                c.assertTrue(dispatcher.execute(command, server.getCommandSource().withLevel(2)) > 0, "OP2 " + command);
            var late = s.player();
            late.setHealth(9);
            for (var player : new ServerPlayerEntity[]{p, q, late}) {
                float hp = player.getHealth();
                player.heal(2);
                c.assertEquals(hp, player.getHealth(), "Global gate includes late join");
            }
            for (var mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
                p.changeGameMode(mode);
                float hp = p.getHealth();
                p.heal(2);
                c.assertEquals(hp + 2, p.getHealth(), "Excluded mode heals");
            }
            p.changeGameMode(GameMode.ADVENTURE);
            p.setHealth(7);
            p.heal(2);
            c.assertEquals(7F, p.getHealth(), "Adventure gate");
            p.changeGameMode(GameMode.SURVIVAL);
            var origin = p.getPos();
            p.teleport(server.getWorld(net.minecraft.world.World.NETHER), .5, 80, .5, 0, 0);
            p.onTeleportationDone();
            p.heal(2);
            c.assertEquals(7F, p.getHealth(), "Other dimension still restricted");
            p.teleport(c.getWorld(), origin.x, origin.y, origin.z, 0, 0);
            p.onTeleportationDone();
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 500));
            var uuid = p.getUuid();
            disconnect(p); // PlayerManager saves vanilla playerdata to disk.
            var pm = server.getOverworld().getPersistentStateManager();
            pm.save();
            var disk = pm.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager());
            pm.set(CurseCycleState.STATE_KEY, loaded);
            p = s.player(uuid); // Actual onPlayerConnect reads the saved playerdata.
            c.assertEquals(7F, p.getHealth(), "Disk player HP restored without healing/clipping");
            c.assertTrue(p.hasStatusEffect(StatusEffects.REGENERATION), "Foreign effect survives disk load");
            p.heal(2);
            c.assertEquals(7F, p.getHealth(), "Gate resumes after load");
            q.heal(2);
            c.assertEquals(11F, q.getHealth(), "Other player unaffected by disconnect/load");
            var challenge = ChallengeRegistry.get("no_healing");
            loaded.setDirty(false);
            for (int i = 0; i < 100; i++) p.heal(1);
            c.assertTrue(!loaded.isDirty() && loaded.getChallengeData().isEmpty(), "Healing gate creates no persistent snapshot/dirty writes");
            c.assertTrue(loaded.getPersonalActiveChallenges().isEmpty(), "Global checks create no PERSONAL UUID records");
            challenge.setEnabled(false);
            try { p.heal(1); c.assertEquals(8F, p.getHealth(), "Disabled immediately allows healing"); }
            finally { challenge.setEnabled(true); }
            loaded.getActiveChallengeIds().clear();
            loaded.getPersonalActiveChallengeIds(uuid).add("no_healing");
            p.heal(2);
            c.assertEquals(8F, p.getHealth(), "Personal gate");
            q.heal(2);
            c.assertEquals(13F, q.getHealth(), "Personal does not affect another UUID");
            loaded.getPersonalActiveChallenges().clear();
            for (int i = 0; i < 32; i++) {
                CycleManager.activateChallenge(server, loaded, "no_healing");
                float hp = p.getHealth();
                p.heal(1);
                p.heal(1);
                c.assertEquals(hp, p.getHealth(), "Repeated activation/attempts stay idempotent");
                CycleManager.activateChallenge(server, loaded, "no_sprint");
                c.assertEquals(hp, p.getHealth(), "Stop never restores HP");
            }
            CycleManager.activateChallenge(server, loaded, "no_healing");
            disconnect(p);
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            pm.save();
            disk = pm.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            pm.set(CurseCycleState.STATE_KEY, CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager()));
            p = s.player(uuid);
            c.assertEquals(8F, p.getHealth(), "Offline end does not restore HP");
            p.heal(2);
            c.assertEquals(10F, p.getHealth(), "Ended disk state does not resurrect restriction");
        }
        c.complete();
    }

    private static long seedFor(int index) {
        for (long seed = 0; ; seed++) if (Random.create(seed).nextInt(19) == index) return seed;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_healing_pairs")
    public void independentSprintHealthAirAndRandomPotion(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            var q = s.player();
            p.setHealth(1);
            s.activate("double_challenge:no_healing,no_sprint");
            for (var player : new ServerPlayerEntity[]{p, q}) {
                player.setHealth(1);
                player.heal(1);
                player.setSprinting(true);
                ChallengeRegistry.get("no_sprint").onTick(p.getServer(), player);
                c.assertEquals(1F, player.getHealth(), "Double healing restriction");
                c.assertTrue(!player.isSprinting(), "Double sprint restriction");
            }
            s.activate("no_sprint");
            p.heal(1);
            p.setSprinting(true);
            ChallengeRegistry.get("no_sprint").onTick(p.getServer(), p);
            c.assertEquals(2F, p.getHealth(), "Healing restored independently");
            c.assertTrue(!p.isSprinting(), "Sprint restriction survives");
            s.activate("double_challenge:no_healing,one_heart");
            p.setHealth(1);
            p.heal(1);
            c.assertEquals(2F, p.getMaxHealth(), "One heart keeps max cap");
            c.assertEquals(1F, p.getHealth(), "No healing with one heart");
            s.activate("one_heart");
            p.heal(1);
            c.assertEquals(2F, p.getHealth(), "One heart remains after no_healing removed");
            s.activate("double_challenge:no_healing,gills");
            p.setHealth(1);
            GillsChallenge.updateBreathing(p);
            c.assertEquals(299, p.getAir(), "Gills remains independent");
            p.heal(1);
            c.assertEquals(1F, p.getHealth(), "No healing with gills");
            s.activate("gills");
            p.heal(1);
            GillsChallenge.updateBreathing(p);
            c.assertEquals(2F, p.getHealth(), "Healing restored with gills active");
            c.assertEquals(299, p.getAir(), "Gills new activation keeps working");
            s.activate("test_easy");
            p.setHealth(6);
            p.getRandom().setSeed(seedFor(3));
            q.getRandom().setSeed(seedFor(3));
            s.activate("double_challenge:no_healing,random_potion");
            c.assertEquals(6F, p.getHealth(), "Real random_potion instant-health selection cannot heal");
            s.activate("test_easy");
            p.getRandom().setSeed(seedFor(6));
            q.getRandom().setSeed(seedFor(6));
            s.activate("double_challenge:no_healing,random_potion");
            tickEffects(p, 50);
            c.assertEquals(6F, p.getHealth(), "Owned random regeneration cannot heal");
            c.assertTrue(p.hasStatusEffect(StatusEffects.REGENERATION), "Random potion owner not deleted");
            p.getRandom().setSeed(seedFor(6));
            q.getRandom().setSeed(seedFor(6));
            s.activate("random_potion");
            tickEffects(p, 50);
            c.assertTrue(p.getHealth() > 6, "Remaining random potion heals after removing no_healing");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void allGrowthPathsLoadingTotemAndRespawn(TestContext context) {
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            for (int i = 0; i < 61; i++) player.tick();
            player.setHealth(10);
            scenario.activate("no_healing");
            player.heal(4);
            player.setHealth(20);
            StatusEffects.INSTANT_HEALTH.value().applyInstantEffect(null, null, player, 0, 1);
            context.assertEquals(10F, player.getHealth(), "heal, direct setter and instant health cannot heal");
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(20);
            for (int i = 0; i < 100; i++) player.getHungerManager().update(player);
            context.assertEquals(10F, player.getHealth(), "Natural regeneration cannot heal");
            var apple = new ItemStack(Items.GOLDEN_APPLE);
            apple.finishUsing(context.getWorld(), player);
            context.assertTrue(apple.isEmpty(), "Apple is consumed");
            context.assertTrue(player.getAbsorptionAmount() > 0, "Absorption is allowed");
            context.assertTrue(player.hasStatusEffect(StatusEffects.REGENERATION), "Foreign regeneration is not removed");
            var saved = new NbtCompound();
            player.writeCustomDataToNbt(saved);
            player.setHealth(5);
            player.readCustomDataFromNbt(saved);
            context.assertEquals(10F, player.getHealth(), "Loading saved health is not blocked as healing");
            player.heal(4);
            context.assertEquals(10F, player.getHealth(), "Restriction resumes immediately after loading");
            player.changeGameMode(GameMode.CREATIVE);
            player.setHealth(15);
            context.assertEquals(15F, player.getHealth(), "Creative is exempt");
            player.changeGameMode(GameMode.ADVENTURE);
            player.heal(2);
            context.assertEquals(15F, player.getHealth(), "Adventure is restricted");
            player.changeGameMode(GameMode.SURVIVAL);
            player.setAbsorptionAmount(0);
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().generic(), 100);
            context.assertEquals(1F, player.getHealth(), "Totem saves with one health");
            context.assertTrue(player.isAlive(), "Totem survives");
            player.heal(10);
            tickEffects(player, 50);
            context.assertEquals(1F, player.getHealth(), "Totem regeneration cannot heal afterwards");
            player = scenario.respawn(player, true);
            context.assertEquals(1F, player.getHealth(), "Live dimension return preserves health");
            player.kill();
            player = scenario.respawn(player, false);
            context.assertEquals(20F, player.getHealth(), "New life initializes vanilla health");
            player.setHealth(8);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 300));
            scenario.activate("no_sprint");
            context.assertEquals(8F, player.getHealth(), "Stopping does not compensate missed healing");
            player.heal(2);
            context.assertEquals(10F, player.getHealth(), "Future healing works after stopping");
            context.assertTrue(player.hasStatusEffect(StatusEffects.REGENERATION), "Foreign effect survives ending");
        }
        context.complete();
    }
}
