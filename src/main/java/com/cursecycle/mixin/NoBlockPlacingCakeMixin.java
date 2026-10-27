package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NoBlockPlacingChallenge;
import net.minecraft.block.BlockState;
import net.minecraft.block.CakeBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CakeBlock.class)
public abstract class NoBlockPlacingCakeMixin {
    @Inject(method = "onUseWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/item/ItemStack;decrementUnlessCreative(ILnet/minecraft/entity/LivingEntity;)V"),
            cancellable = true)
    private void cursecycle$denyCandle(ItemStack stack, BlockState state, World world, BlockPos pos,
                                       PlayerEntity player, Hand hand, BlockHitResult hit,
                                       CallbackInfoReturnable<ItemActionResult> cir) {
        // Adding a candle bypasses BlockItem.place; eating cake still follows its normal path.
        if (NoBlockPlacingChallenge.blocks(player)) cir.setReturnValue(ItemActionResult.FAIL);
    }
}
