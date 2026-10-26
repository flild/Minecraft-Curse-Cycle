package com.cursecycle.mixin;

import com.cursecycle.challenge.DamageHooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class AppliedDamageLivingEntityMixin {
    @WrapMethod(method = "damage")
    private boolean cursecycle$completedDamage(DamageSource source, float amount, Operation<Boolean> original) {
        if (!((Object) this instanceof ServerPlayerEntity player) || !DamageHooks.needed(player))
            return original.call(source, amount);
        // The primary melee call may already own this receipt, including incoming reactions.
        if (DamageHooks.isPending(player, source)) return original.call(source, amount);
        var receipt = DamageHooks.begin(player, source);
        boolean completed = false;
        try {
            boolean result = original.call(source, amount);
            completed = true;
            return result;
        } finally { DamageHooks.finish(receipt, completed); }
    }

    @WrapOperation(method = "applyDamage", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;setHealth(F)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;setAbsorptionAmount(F)V")})
    private void cursecycle$realReduction(LivingEntity target, float value, Operation<Void> original) {
        float health = target.getHealth(), absorption = target.getAbsorptionAmount();
        original.call(target, value);
        if (target.getHealth() < health || target.getAbsorptionAmount() < absorption) DamageHooks.accepted(target);
    }
}
