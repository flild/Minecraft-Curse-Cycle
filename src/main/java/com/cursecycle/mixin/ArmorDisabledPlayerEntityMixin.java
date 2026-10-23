package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.ArmorDisabledChallenge;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class ArmorDisabledPlayerEntityMixin {
    @Inject(method = "checkFallFlying", at = @At("HEAD"), cancellable = true)
    private void cursecycle$blockElytraTakeoff(CallbackInfoReturnable<Boolean> cir) {
        if (ArmorDisabledChallenge.isActiveForPlayer((PlayerEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startFallFlying", at = @At("HEAD"), cancellable = true)
    private void cursecycle$blockElytraFlight(CallbackInfo ci) {
        if (ArmorDisabledChallenge.isActiveForPlayer((PlayerEntity) (Object) this)) ci.cancel();
    }
}
