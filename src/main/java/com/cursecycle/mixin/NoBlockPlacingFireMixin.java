package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NoBlockPlacingChallenge;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.CandleCakeBlock;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({FlintAndSteelItem.class, FireChargeItem.class})
public abstract class NoBlockPlacingFireMixin {
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void cursecycle$denyNewFire(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (!NoBlockPlacingChallenge.blocks(context.getPlayer())) return;
        var state = context.getWorld().getBlockState(context.getBlockPos());
        // Lighting an existing block changes its properties and remains a normal interaction.
        if (!CampfireBlock.canBeLit(state) && !CandleBlock.canBeLit(state) && !CandleCakeBlock.canBeLit(state)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
