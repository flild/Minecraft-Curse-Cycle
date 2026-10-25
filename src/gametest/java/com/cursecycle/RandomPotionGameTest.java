package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.RandomPotionChallenge;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.random.Random;

public class RandomPotionGameTest implements FabricGameTest {
    private static long seedFor(int index) {
        for (long seed = 0; ; seed++) if (Random.create(seed).nextInt(19) == index) return seed;
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void milkInstantAndForeignContributions(TestContext context) {
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            for (int i = 0; i < 61; i++) player.tick();
            var challenge = ChallengeRegistry.get("random_potion");
            player.getRandom().setSeed(seedFor(0));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200, 1));
            scenario.activate("random_potion");
            context.assertEquals(1, player.getStatusEffect(StatusEffects.SPEED).getAmplifier(), "Foreign stronger speed is preserved");
            player.clearStatusEffects();
            challenge.onTick(player.getServer(), player);
            context.assertTrue(player.getStatusEffects().isEmpty(), "Milk does not cause early choice");
            player = scenario.respawn(player, true);
            context.assertTrue(player.getStatusEffects().isEmpty(), "Live dimension return preserves the interval after milk");
            scenario.activate("no_sprint");
            for (int i = 0; i < 61; i++) player.tick();
            player.getRandom().setSeed(seedFor(4));
            player.timeUntilRegen = 0;
            scenario.activate("random_potion");
            context.assertEquals(14F, player.getHealth(), "Instant damage once at selection");
            player.timeUntilRegen = 0;
            challenge.onTick(player.getServer(), player);
            context.assertEquals(14F, player.getHealth(), "Duplicate tick does not repeat instant damage");
            scenario.activate("no_sprint");
            context.assertEquals(14F, player.getHealth(), "Stopping does not heal damage");
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 900, 2));
            player.getRandom().setSeed(seedFor(0));
            scenario.activate("double_challenge:random_potion,all_debuffs");
            challenge.onStop(player.getServer(), player);
            context.assertEquals(2, player.getStatusEffect(StatusEffects.SPEED).getAmplifier(), "Foreign speed survives owned cleanup");
            context.assertTrue(player.hasStatusEffect(StatusEffects.POISON), "Other challenge contributions remain");
        }
        context.complete();
    }
}
