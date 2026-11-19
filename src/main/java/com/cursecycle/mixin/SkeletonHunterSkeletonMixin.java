package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.HunterMark;
import net.minecraft.entity.mob.SkeletonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkeletonEntity.class)
public abstract class SkeletonHunterSkeletonMixin {
    @Inject(method = "convertToStray", at = @At("HEAD"), cancellable = true)
    private void cursecycle$keepSkeletonType(CallbackInfo ci) {
        if (((HunterMark) this).cursecycle$hunterOwner() != null) ci.cancel();
    }
}
