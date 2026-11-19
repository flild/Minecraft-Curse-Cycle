package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.LowGravityChallenge;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LowGravityLivingEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void cursecycle$attributes(CallbackInfo ci) {
        LowGravityChallenge.reconcile((LivingEntity) (Object) this);
    }

    // Scale incoming strength once, before vanilla resistance and the grounded vertical cap.
    @ModifyVariable(method = "takeKnockback", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double cursecycle$knockback(double strength) {
        return LowGravityChallenge.active((LivingEntity) (Object) this)
                ? strength * LowGravityChallenge.KNOCKBACK : strength;
    }
}
