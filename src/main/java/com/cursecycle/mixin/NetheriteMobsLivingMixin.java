package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NetheriteMobSpawn;
import com.cursecycle.challenge.impl.NetheriteMobsChallenge;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class NetheriteMobsLivingMixin {
    @Shadow protected abstract void damageEquipment(DamageSource source, float amount, EquipmentSlot... slots);

    @Inject(method = "damage", at = @At("HEAD"))
    private void cursecycle$armorBeforeFirstHit(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof MobEntity mob && !mob.getWorld().isClient())
            ((NetheriteMobSpawn) mob).cursecycle$finishBirth();
    }

    // Vanilla LivingEntity.damageArmor is empty for mobs. Use the vanilla durability helper
    // only for our items, so their explicitly required wear/Unbreaking/break behavior works.
    @Inject(method = "applyArmorToDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;damageArmor(Lnet/minecraft/entity/damage/DamageSource;F)V"))
    private void cursecycle$wearIssuedArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof MobEntity mob) || mob.getWorld().isClient()) return;
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            if (NetheriteMobsChallenge.isIssued(mob.getEquippedStack(slot))) damageEquipment(source, amount, slot);
    }
}
