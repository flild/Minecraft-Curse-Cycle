package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.CraftingTaxChallenge;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingResultSlot.class)
public abstract class CraftingTaxResultSlotMixin extends Slot {
    private CraftingTaxResultSlotMixin(Inventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override public boolean canTakeItems(PlayerEntity player) {
        return super.canTakeItems(player) && CraftingTaxChallenge.canPay(player);
    }

    // Exactly one call per ingredient-consuming execution, including right click, swap and throw.
    // onCrafted/onQuickTransfer are statistics hooks and must not charge a second time.
    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void cursecycle$payForIngredients(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        CraftingTaxChallenge.pay(player);
    }
}
