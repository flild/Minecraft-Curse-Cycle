package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.InventoryLockdownChallenge;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MerchantScreenHandler.class)
public abstract class InventoryLockdownMerchantScreenHandlerMixin {
    @Redirect(method = "autofill", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/screen/slot/Slot;getStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack cursecycle$tradeOnlyFromHotbar(Slot slot) {
        // Selecting an offer bypasses slot clicks and decrements source stacks directly.
        return InventoryLockdownChallenge.isLockedSlot(slot) ? ItemStack.EMPTY : slot.getStack();
    }
}
