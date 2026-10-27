package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NoBlockPlacingChallenge;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.PowderSnowBucketItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BucketItem.class, PowderSnowBucketItem.class})
public abstract class NoBlockPlacingFluidMixin {
    @Inject(method = "placeFluid", at = @At("HEAD"), cancellable = true)
    private void cursecycle$denyFluid(PlayerEntity player, World world, BlockPos pos, BlockHitResult hit,
                                     CallbackInfoReturnable<Boolean> cir) {
        // Includes use-in-air raycast, waterlogging, fish buckets and Nether evaporation.
        // Dispensers pass null; empty-bucket extraction never calls placeFluid.
        if (NoBlockPlacingChallenge.blocks(player)) cir.setReturnValue(false);
    }
}
