package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.AllDebuffsChallenge;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

public class AllDebuffsGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void exactSetMilkAndIndependentSources(TestContext context) {
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            scenario.activate("all_debuffs");
            context.assertTrue(!AllDebuffsChallenge.effects().isEmpty(), "Actual harmful registry set exists");
            for (var effect : Registries.STATUS_EFFECT.streamEntries().toList()) {
                boolean expected = effect.registryKey().getValue().getNamespace().equals("minecraft")
                        && effect.value().getCategory() == StatusEffectCategory.HARMFUL
                        && !effect.equals(StatusEffects.INSTANT_DAMAGE) && !effect.equals(StatusEffects.WITHER);
                context.assertTrue(player.hasStatusEffect(effect) == expected,
                        "Exact registry membership: " + effect.registryKey());
                if (expected) context.assertEquals(0, player.getStatusEffect(effect).getAmplifier(), "Level I");
            }
            var late = scenario.player();
            ChallengeRegistry.get("all_debuffs").onTick(late.getServer(), late);
            context.assertTrue(late.hasStatusEffect(StatusEffects.POISON), "Late join receives the shared activation on the first Survival tick");
            context.assertTrue(!player.hasStatusEffect(StatusEffects.WITHER), "No owned wither at activation");
            context.assertTrue(!player.hasStatusEffect(StatusEffects.INSTANT_DAMAGE), "No owned instant damage");
            context.assertEquals(20F, player.getHealth(), "No activation damage pulse");
            player.clearStatusEffects();
            ChallengeRegistry.get("all_debuffs").onTick(player.getServer(), player);
            for (var effect : AllDebuffsChallenge.effects()) context.assertTrue(player.hasStatusEffect(effect), "Next tick restores milk-cleared effects");
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 400));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 1));
            scenario.activate("no_sprint");
            context.assertTrue(player.hasStatusEffect(StatusEffects.WITHER), "External wither survives cleanup");
            context.assertTrue(player.hasStatusEffect(StatusEffects.SPEED), "Beneficial effect survives cleanup");
            for (var effect : AllDebuffsChallenge.effects()) context.assertTrue(!player.hasStatusEffect(effect), "Owned harmful effect removed");
            scenario.activate("double_challenge:all_debuffs,no_block_breaking");
            context.assertEquals(9, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Other owner fatigue X wins");
            ChallengeRegistry.get("no_block_breaking").onStop(player.getServer(), player);
            context.assertEquals(0, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Removing X retains owned fatigue I");
            scenario.activate("no_sprint");
            context.assertTrue(!player.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Both owned contributions clean up");
        }
        context.complete();
    }
}
