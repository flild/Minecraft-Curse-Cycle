package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NoSprintChallenge;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Intercept the override before it changes either the flag or the speed attribute.
@Mixin(LivingEntity.class)
public abstract class EntitySprintingMixin {

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void onSetSprinting(boolean sprinting, CallbackInfo ci) {
        if (!sprinting) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof PlayerEntity player) {
            if (NoSprintChallenge.isNoSprintActiveForPlayer(player)) {
                ci.cancel();
                player.setSprinting(false);
            }
        }
    }
}
