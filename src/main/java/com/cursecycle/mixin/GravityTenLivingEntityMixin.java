package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.GravityTenChallenge;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class GravityTenLivingEntityMixin {
    // First double local, after getFinalGravity AND the Slow Falling min, before all travel branches.
    // A return hook on getGravity would let Slow Falling overwrite our multiplier.
    @ModifyVariable(method = "travel", ordinal = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getFluidState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/fluid/FluidState;"))
    private double cursecycle$gravity(double gravity) {
        return (Object) this instanceof PlayerEntity player && GravityTenChallenge.active(player)
                ? gravity * GravityTenChallenge.MULTIPLIER : gravity;
    }

    // Multiply vanilla's rounded result; retain safe distance, block multiplier and damage mitigation.
    @Inject(method = "computeFallDamage", at = @At("RETURN"), cancellable = true)
    private void cursecycle$fallDamage(float distance, float multiplier, CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof PlayerEntity player && GravityTenChallenge.active(player)
                && cir.getReturnValueI() > 0)
            cir.setReturnValue(cir.getReturnValueI() * GravityTenChallenge.MULTIPLIER);
    }
}
