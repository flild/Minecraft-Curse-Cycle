package com.cursecycle.mixin;

import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.impl.CraftingTaxChallenge;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CraftingScreenHandler.class, PlayerScreenHandler.class})
public abstract class CraftingTaxQuickMoveMixin {
    @Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
    private void cursecycle$checkEachExecution(PlayerEntity player, int index,
                                               CallbackInfoReturnable<ItemStack> cir) {
        if (index != 0 || !ChallengeActivity.active(player, CraftingTaxChallenge.ID)) return;
        var handler = (ScreenHandler) (Object) this;
        if (!CraftingTaxChallenge.canPay(player)
                || !CraftingTaxChallenge.fitsQuickMove(handler, handler.getSlot(0).getStack())) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
