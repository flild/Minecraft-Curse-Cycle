package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.ArrayList;

public class DamageSlotDropChallenge extends Challenge {
    public static final String ID = "damage_slot_drop";
    public DamageSlotDropChallenge() {
        super(ID, "Дырявые карманы", "При прошедшем уроне выпадает один случайный стек инвентаря.",
                "В Survival и Adventure каждый фактически прошедший несмертельный урон выбрасывает весь стек из одного случайного непустого слота основного инвентаря или хотбара. Броня, вторая рука и предмет на курсоре не затрагиваются. Если подходящих предметов нет, ничего не происходит. Выпавший предмет можно подобрать снова.", ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }
    @Override public void onPlayerDamageApplied(MinecraftServer server, ServerPlayerEntity player, DamageReceipt damage) {
        if (!ChallengeActivity.active(player, ID)) return;
        var inventory = player.getInventory();
        var slots = new ArrayList<Integer>();
        for (int i = 0; i < 36; i++) if (!inventory.getStack(i).isEmpty()) slots.add(i);
        if (slots.isEmpty()) return;
        int slot = slots.get(player.getRandom().nextInt(slots.size()));
        ItemStack stack = inventory.getStack(slot);
        var drop = new ItemEntity(damage.world, damage.position.x, damage.position.y, damage.position.z, stack.copy());
        drop.setPickupDelay(40);
        if (damage.world.spawnEntity(drop)) {
            inventory.setStack(slot, ItemStack.EMPTY);
            inventory.markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }
}
