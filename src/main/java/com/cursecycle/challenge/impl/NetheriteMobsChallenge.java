package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

public final class NetheriteMobsChallenge extends Challenge {
    public static final String ID = "netherite_mobs";
    public static final String ITEM_MARKER = "cursecycle:netherite_mobs";
    public static final String SESSION_MARKER = "cursecycle:netherite_mobs_session";

    // Технические нюансы; не включать в описание для игрока.
    // Новые мобы во всех измерениях получают полный комплект без чар, включая мирных мобов и боссов.
    // Броня не выпадает и удаляется после завершения; выгруженная — при загрузке, даже в следующем испытании.
    // Существующие мобы не переодеваются; обычная экипировка не удаляется.
    public NetheriteMobsChallenge() {
        super(ID, "Незеритовая вечеринка",
                "Новые мобы появляются в незеритовой броне без чар.",
                "Все новые мобы получают полный комплект незеритовой брони без чар. После завершения испытания выданная броня исчезает.",
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

    /** Called after GLOBAL changes and each schedule tick, including on an empty server. */
    public static void reconcile(MinecraftServer server) {
        var state = CurseCycleState.getServerState(server);
        if (active(server.getOverworld())) {
            if (state.getNetheriteMobsSession().isEmpty())
                state.setNetheriteMobsSession(UUID.randomUUID().toString());
        } else if (!state.getNetheriteMobsSession().isEmpty()) {
            state.setNetheriteMobsSession("");
            for (var world : server.getWorlds())
                for (var entity : world.iterateEntities())
                    if (entity instanceof MobEntity mob) {
                        removeExpiredArmor(mob, "");
                        ((NetheriteMobSpawn) mob).cursecycle$finishBirth();
                    }
        }
    }

    public static String currentSession(ServerWorld world) {
        if (!active(world)) return "";
        var state = CurseCycleState.getServerState(world.getServer());
        if (state.getNetheriteMobsSession().isEmpty()) reconcile(world.getServer());
        return state.getNetheriteMobsSession();
    }

    /** Legacy marked armor loses its old enchantments when loaded during an active interval. */
    public static boolean removeExpiredArmor(MobEntity mob, String session) {
        boolean retained = false;
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var stack = mob.getEquippedStack(slot);
            if (!isIssued(stack)) continue;
            var data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
            String issuedSession = data.getString(SESSION_MARKER);
            if (session.isEmpty() || (!issuedSession.isEmpty() && !issuedSession.equals(session))) {
                mob.equipStack(slot, ItemStack.EMPTY);
            } else {
                if (issuedSession.isEmpty()) {
                    data.putString(SESSION_MARKER, session);
                    stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
                }
                if (stack.hasEnchantments()) stack.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
                retained = true;
            }
        }
        return retained;
    }

    public static void equip(MobEntity mob) {
        if (!(mob.getWorld() instanceof ServerWorld world)) return;
        String session = currentSession(world);
        if (session.isEmpty()) return;
        equip(mob, session, EquipmentSlot.HEAD, Items.NETHERITE_HELMET);
        equip(mob, session, EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE);
        equip(mob, session, EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS);
        equip(mob, session, EquipmentSlot.FEET, Items.NETHERITE_BOOTS);
    }

    private static void equip(MobEntity mob, String session, EquipmentSlot slot, Item item) {
        var stack = new ItemStack(item);
        var origin = new NbtCompound();
        origin.putBoolean(ITEM_MARKER, true);
        origin.putString(SESSION_MARKER, session);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(origin));
        mob.equipStack(slot, stack);
        mob.setEquipmentDropChance(slot, 0);
    }
}
