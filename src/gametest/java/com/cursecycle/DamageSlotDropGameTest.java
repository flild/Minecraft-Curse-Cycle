package com.cursecycle;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.world.GameRules;

public class DamageSlotDropGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void realDamageAbsorptionDeathAndLockedSlots(TestContext context) {
        var rules = context.getWorld().getGameRules();
        boolean keep = rules.getBoolean(GameRules.KEEP_INVENTORY);
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            for (int i = 0; i < 61; i++) player.tick();
            var pos = context.getAbsolutePos(new net.minecraft.util.math.BlockPos(2, 2, 2));
            player.teleport(context.getWorld(), pos.getX()+0.5, pos.getY(), pos.getZ()+0.5, 0, 0);
            var stack = new ItemStack(Items.DIAMOND, 64);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("damage receipt"));
            player.getInventory().setStack(10, stack.copy());
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
            player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1200));
            var area = player.getBoundingBox().expand(3);
            int before = context.getWorld().getEntitiesByClass(ItemEntity.class, area, e -> true).size();
            scenario.activate("damage_slot_drop");
            player.damage(player.getDamageSources().generic(), 2);
            context.assertEquals(20F, player.getHealth(), "Only absorption lost");
            context.assertTrue(player.getInventory().getStack(10).isEmpty(), "Whole occupied slot dropped");
            var drops = context.getWorld().getEntitiesByClass(ItemEntity.class, area, e -> e.getStack().isOf(Items.DIAMOND));
            context.assertEquals(1, drops.size(), "One drop per accepted damage");
            context.assertEquals(64, drops.getFirst().getStack().getCount(), "Whole stack count");
            context.assertTrue(ItemStack.areItemsAndComponentsEqual(stack, drops.getFirst().getStack()), "Components preserved");
            context.assertTrue(player.getStackInHand(Hand.OFF_HAND).isOf(Items.SHIELD), "Offhand untouched");
            player.getInventory().setStack(10, stack.copy());
            player.damage(player.getDamageSources().generic(), 2);
            context.assertEquals(64, player.getInventory().getStack(10).getCount(), "Ignored i-frame creates no drop");
            rules.get(GameRules.KEEP_INVENTORY).set(true, player.getServer());
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().generic(), 100);
            context.assertTrue(!player.isAlive(), "Lethal damage without totem");
            context.assertEquals(64, player.getInventory().getStack(10).getCount(), "Death with keepInventory creates no extra challenge drop");
            player = scenario.respawn(player, false);
            for (int i = 0; i < 61; i++) player.tick();
            player.teleport(context.getWorld(), pos.getX()+0.5, pos.getY(), pos.getZ()+0.5, 0, 0);
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().generic(), 100);
            context.assertTrue(player.isAlive(), "Totem saves");
            context.assertTrue(player.getInventory().getStack(10).isEmpty(), "Totem-survived damage drops stack");
            scenario.activate("double_challenge:damage_slot_drop,inventory_lockdown");
            player.clearStatusEffects();
            player.setHealth(20);
            player.getInventory().setStack(10, stack.copy());
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().generic(), 2);
            context.assertTrue(player.getInventory().getStack(10).isEmpty(), "Closed main slot still participates in automatic drop");
        } finally { rules.get(GameRules.KEEP_INVENTORY).set(keep, context.getWorld().getServer()); }
        context.complete();
    }
}
