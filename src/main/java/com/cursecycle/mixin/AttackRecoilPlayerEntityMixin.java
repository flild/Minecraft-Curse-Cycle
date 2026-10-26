package com.cursecycle.mixin;

import com.cursecycle.challenge.DamageHooks;
import com.cursecycle.challenge.DamageReceipt;
import com.cursecycle.challenge.impl.AttackRecoilChallenge;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class AttackRecoilPlayerEntityMixin {
    // The primary hit invokes Entity.damage; the sweep loop invokes LivingEntity.damage.
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean cursecycle$primaryHit(Entity target, DamageSource source, float amount,
                                         Operation<Boolean> original,
                                         @Share("cursecycle$primaryDamage") LocalRef<DamageReceipt> primary) {
        if (!((Object) this instanceof ServerPlayerEntity player) || !AttackRecoilChallenge.eligible(player, target))
            return original.call(target, source, amount);
        var receipt = DamageHooks.begin((LivingEntity) target, source);
        boolean completed = false;
        try {
            boolean result = original.call(target, source, amount);
            completed = true;
            primary.set(receipt);
            return result;
        } finally { DamageHooks.finish(receipt, completed); }
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void cursecycle$afterAttack(Entity target, CallbackInfo ci,
                                       @Share("cursecycle$primaryDamage") LocalRef<DamageReceipt> primary) {
        var receipt = primary.get();
        if (receipt != null && receipt.wasApplied())
            AttackRecoilChallenge.recoil((ServerPlayerEntity) (Object) this, receipt.target);
    }
}
