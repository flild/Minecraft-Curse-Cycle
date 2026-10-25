package com.cursecycle.mixin;

import com.cursecycle.challenge.DamageHooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class AppliedDamageLivingEntityMixin {
    @WrapMethod(method = "damage")
    private boolean cursecycle$completedDamage(DamageSource source, float amount, Operation<Boolean> original) {
        if (!((Object) this instanceof ServerPlayerEntity player) || !DamageHooks.needed(player))
            return original.call(source, amount);
        var receipt = DamageHooks.begin(player, source);
        boolean completed = false;
        try {
            boolean result = original.call(source, amount);
            completed = true;
            return result;
        } finally { DamageHooks.finish(receipt, completed); }
    }
}
