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
        var target = (LivingEntity) (Object) this;
        // Borrow a primary-attack receipt once; recursive damage owns a separate result,
        // including mobs and player targets without incoming-damage challenges.
        if (DamageHooks.claimAttackDamage(target, source)) return original.call(source, amount);
        if (!DamageHooks.isPending(target)
                && (!(target instanceof ServerPlayerEntity player) || !DamageHooks.needed(player)))
            return original.call(source, amount);
        var receipt = DamageHooks.begin(target, source);
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
