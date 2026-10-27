package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NoBlockPlacingChallenge;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class NoBlockPlacingBlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"), cancellable = true)
    private void cursecycle$denyPlacement(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        // Before even the first half of a bed/door, NBT, sound, criteria or item consumption.
        if (NoBlockPlacingChallenge.blocks(context.getPlayer())) cir.setReturnValue(ActionResult.FAIL);
    }
}
