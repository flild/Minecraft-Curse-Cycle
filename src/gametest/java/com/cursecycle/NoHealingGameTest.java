package com.cursecycle;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;

public class NoHealingGameTest implements FabricGameTest {
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
