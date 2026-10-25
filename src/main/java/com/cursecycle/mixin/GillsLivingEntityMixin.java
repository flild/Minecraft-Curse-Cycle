package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.GillsChallenge;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class GillsLivingEntityMixin {
    @Inject(method = {"getNextAirUnderwater", "getNextAirOnLand"}, at = @At("HEAD"), cancellable = true)
    private void cursecycle$exclusiveBreathing(int air, CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof PlayerEntity player && GillsChallenge.active(player)) cir.setReturnValue(air);
    }
}
