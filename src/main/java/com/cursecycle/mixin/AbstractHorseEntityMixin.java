package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NoJumpingChallenge;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorseEntity.class)
public abstract class AbstractHorseEntityMixin {

    @Inject(method = "canJump", at = @At("HEAD"), cancellable = true)
    private void onHorseCanJump(CallbackInfoReturnable<Boolean> cir) {
        AbstractHorseEntity horse = (AbstractHorseEntity) (Object) this;
        if (horse.getControllingPassenger() instanceof PlayerEntity player) {
            if (NoJumpingChallenge.isNoJumpingActiveForPlayer(player)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "startJumping", at = @At("HEAD"), cancellable = true)
    private void onHorseStartJumping(int height, CallbackInfo ci) {
        AbstractHorseEntity horse = (AbstractHorseEntity) (Object) this;
        if (horse.getControllingPassenger() instanceof PlayerEntity player) {
            if (NoJumpingChallenge.isNoJumpingActiveForPlayer(player)) {
                ci.cancel();
            }
        }
    }
}
