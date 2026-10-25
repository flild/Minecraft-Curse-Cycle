package com.cursecycle.mixin;

import com.cursecycle.challenge.DamageHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
public abstract class AppliedDamagePlayerEntityMixin {
    @WrapOperation(method = "applyDamage", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setHealth(F)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setAbsorptionAmount(F)V")})
    private void cursecycle$realReduction(PlayerEntity player, float value, Operation<Void> original) {
        float health = player.getHealth(), absorption = player.getAbsorptionAmount();
        original.call(player, value);
        if (player.getHealth() < health || player.getAbsorptionAmount() < absorption) DamageHooks.accepted(player);
    }
}
