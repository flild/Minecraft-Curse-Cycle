package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;

public final class NetheriteMobsChallenge extends Challenge {
    public static final String ID = "netherite_mobs";
    public static final String ITEM_MARKER = "cursecycle:netherite_mobs";

    public NetheriteMobsChallenge() {
        super(ID, "Незеритовая вечеринка", "Кто-то выдал всем мобам незеритовую броню.",
                "Все новые мобы, появившиеся во время испытания во всех измерениях, получают полный комплект незеритовой брони с максимальными совместимыми ванильными чарами выбранного набора. Это касается враждебных, нейтральных, мирных мобов и боссов, включая тех, у кого броня визуально не отображается. Выданная броня не выпадает. Уже существующие мобы не переодеваются; после завершения новые комплекты не выдаются, ранее созданные остаются на мобах.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    public static boolean active(ServerWorld world) {
        var challenge = ChallengeRegistry.get(ID);
        // This is a GLOBAL world consequence, including an empty server and Creative summoners.
        return challenge != null && challenge.isEnabled()
                && CurseCycleState.getServerState(world.getServer()).getActiveChallengeIds().contains(ID);
    }

    public static boolean isIssued(ItemStack stack) {
        return !stack.isEmpty() && stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .copyNbt().getBoolean(ITEM_MARKER);
    }

    public static void equip(MobEntity mob) {
        if (!(mob.getWorld() instanceof ServerWorld world)) return;
        equip(mob, world, EquipmentSlot.HEAD, Items.NETHERITE_HELMET);
        equip(mob, world, EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE);
        equip(mob, world, EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS);
        equip(mob, world, EquipmentSlot.FEET, Items.NETHERITE_BOOTS);
    }

    private static void equip(MobEntity mob, ServerWorld world, EquipmentSlot slot, Item item) {
        var stack = new ItemStack(item);
        var origin = new NbtCompound();
        origin.putBoolean(ITEM_MARKER, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(origin));
        enchant(stack, world, Enchantments.PROTECTION, 4);
        enchant(stack, world, Enchantments.UNBREAKING, 3);
        enchant(stack, world, Enchantments.MENDING, 1);
        enchant(stack, world, Enchantments.THORNS, 3);
        switch (slot) {
            case HEAD -> {
                enchant(stack, world, Enchantments.RESPIRATION, 3);
                enchant(stack, world, Enchantments.AQUA_AFFINITY, 1);
            }
            case LEGS -> enchant(stack, world, Enchantments.SWIFT_SNEAK, 3);
            case FEET -> {
                enchant(stack, world, Enchantments.FEATHER_FALLING, 4);
                enchant(stack, world, Enchantments.DEPTH_STRIDER, 3);
                enchant(stack, world, Enchantments.SOUL_SPEED, 3);
            }
            default -> { }
        }
        mob.equipStack(slot, stack);
        mob.setEquipmentDropChance(slot, 0);
    }

    private static void enchant(ItemStack stack, ServerWorld world, RegistryKey<Enchantment> key, int level) {
        stack.addEnchantment(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(key).orElseThrow(), level);
    }
}
