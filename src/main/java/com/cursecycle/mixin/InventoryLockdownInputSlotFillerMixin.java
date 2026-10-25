package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.InventoryLockdownChallenge;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.InputSlotFiller;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;

@Mixin(InputSlotFiller.class)
public abstract class InventoryLockdownInputSlotFillerMixin {
    @Shadow protected PlayerInventory inventory;

    @Redirect(method = "getFreeInventorySlots", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/collection/DefaultedList;iterator()Ljava/util/Iterator;"))
    private Iterator<ItemStack> cursecycle$countOnlyHotbar(DefaultedList<ItemStack> stacks) {
        return InventoryLockdownChallenge.isActiveForPlayer(inventory.player)
                ? stacks.subList(0, PlayerInventory.getHotbarSize()).iterator() : stacks.iterator();
    }
}
