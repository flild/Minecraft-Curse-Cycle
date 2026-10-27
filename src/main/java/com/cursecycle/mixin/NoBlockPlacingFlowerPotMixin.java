package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NoBlockPlacingChallenge;
import net.minecraft.block.BlockState;
import net.minecraft.block.FlowerPotBlock;
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

@Mixin(FlowerPotBlock.class)
public abstract class NoBlockPlacingFlowerPotMixin {
    @Inject(method = "onUseWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"),
            cancellable = true)
    private void cursecycle$denyPlanting(ItemStack stack, BlockState state, World world, BlockPos pos,
                                         PlayerEntity player, Hand hand, BlockHitResult hit,
                                         CallbackInfoReturnable<ItemActionResult> cir) {
        // Plant insertion bypasses BlockItem.place. Removing a plant uses onUse and is unaffected.
        if (NoBlockPlacingChallenge.blocks(player)) cir.setReturnValue(ItemActionResult.FAIL);
    }
}
