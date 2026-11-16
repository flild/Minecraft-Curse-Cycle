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

    // Технические нюансы; не включать в описание для игрока.
    // Новые мобы во всех измерениях получают полный комплект с максимальными совместимыми ванильными чарами
    // выбранного набора. Включены враждебные, нейтральные, мирные мобы и боссы, даже без отображения брони.
    // Броня не выпадает. Существующие мобы не переодеваются; после завершения выданные комплекты остаются,
    // новые не выдаются.
    public NetheriteMobsChallenge() {
        super(ID, "Незеритовая вечеринка",
                "Новые мобы появляются в зачарованной незеритовой броне.",
                "Всех новых мобов снарядили полным комплектом зачарованного незерита. Даже тех, от кого такого совсем не ждёшь.",
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
