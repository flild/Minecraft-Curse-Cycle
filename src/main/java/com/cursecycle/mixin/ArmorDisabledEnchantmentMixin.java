package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.ArmorDisabledChallenge;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Enchantment.class)
public abstract class ArmorDisabledEnchantmentMixin {
    @Inject(method = "modifyDamageProtection", at = @At("HEAD"), cancellable = true)
    private void cursecycle$disableArmorProtectionEnchantments(ServerWorld world, int level, ItemStack stack,
            Entity user, DamageSource source, MutableFloat protection, CallbackInfo ci) {
        if (ArmorDisabledChallenge.isArmorStackDisabled(user, stack)) ci.cancel();
    }
}
