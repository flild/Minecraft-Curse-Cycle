package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;

public class OneHeartGameTest implements FabricGameTest {
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
