package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.ArrayList;

public class DamageSlotDropChallenge extends Challenge {
    public static final String ID = "damage_slot_drop";
    // Технические нюансы; не включать в описание для игрока.
    // Выбирается непустой слот основного инвентаря или хотбара. Броня, вторая рука и предмет на курсоре
    // исключены. При пустом инвентаре ничего не происходит; выброшенный стек можно подобрать снова.
    public DamageSlotDropChallenge() {
        super(ID, "Дырявые карманы",
                "Получаешь урон — теряешь случайную стопку вещей.",
                "Каждый удар вытряхивает из карманов одну случайную стопку предметов целиком.", ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
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
