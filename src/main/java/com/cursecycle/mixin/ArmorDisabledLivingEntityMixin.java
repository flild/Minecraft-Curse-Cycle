package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.ArmorDisabledChallenge;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ArmorDisabledLivingEntityMixin {
    @Inject(method = "getArmor", at = @At("HEAD"), cancellable = true)
    private void cursecycle$disableArmorDisplay(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof PlayerEntity player && ArmorDisabledChallenge.isActiveForPlayer(player)) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "applyArmorToDamage", at = @At("HEAD"), cancellable = true)
    private void cursecycle$bypassArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof PlayerEntity player && ArmorDisabledChallenge.isActiveForPlayer(player)) {
            // Bypass armor/toughness without changing attributes, durability or status effects.
            cir.setReturnValue(amount);
        }
    }

    @Redirect(method = "damage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;getEquippedStack(Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack cursecycle$disableHelmetProtection(LivingEntity entity, EquipmentSlot slot) {
        if (entity instanceof PlayerEntity player && ArmorDisabledChallenge.isActiveForPlayer(player)) {
            // Falling anvils/blocks apply helmet protection before the normal armor calculation.
            return ItemStack.EMPTY;
        }
        return entity.getEquippedStack(slot);
    }
}
