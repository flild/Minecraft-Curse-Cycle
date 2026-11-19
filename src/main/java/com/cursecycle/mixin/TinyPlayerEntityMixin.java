package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.TinyPlayerChallenge;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class TinyPlayerEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void cursecycle$tinyAttributes(CallbackInfo ci) {
        TinyPlayerChallenge.reconcile((PlayerEntity) (Object) this);
    }
}
