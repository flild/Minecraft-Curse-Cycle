package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.OneHeartChallenge;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class OneHeartLivingEntityMixin {
    @Inject(method = "getMaxHealth", at = @At("RETURN"), cancellable = true)
    private void cursecycle$maxHealth(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof PlayerEntity player && OneHeartChallenge.active(player))
            cir.setReturnValue(Math.min(cir.getReturnValueF(), 2F));
    }
    @Inject(method = "getMaxAbsorption", at = @At("RETURN"), cancellable = true)
    private void cursecycle$maxAbsorption(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof PlayerEntity player && OneHeartChallenge.active(player)) cir.setReturnValue(0F);
    }
}
