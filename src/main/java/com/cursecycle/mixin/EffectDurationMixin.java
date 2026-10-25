package com.cursecycle.mixin;

import com.cursecycle.challenge.effect.EffectDurationAccess;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(StatusEffectInstance.class)
public abstract class EffectDurationMixin implements EffectDurationAccess {
    @Shadow private StatusEffectInstance hiddenEffect;
    @Shadow private int duration;

    @Override
    public void cursecycle$advanceDuration() {
        if (hiddenEffect != null) ((EffectDurationAccess) hiddenEffect).cursecycle$advanceDuration();
        if (duration > 0) duration--;
    }

    @Override public StatusEffectInstance cursecycle$liveChain() {
        if (duration != 0) return (StatusEffectInstance) (Object) this;
        return hiddenEffect == null ? null : ((EffectDurationAccess) hiddenEffect).cursecycle$liveChain();
    }
}
