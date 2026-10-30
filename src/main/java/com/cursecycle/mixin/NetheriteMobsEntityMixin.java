package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NetheriteMobsChallenge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class NetheriteMobsEntityMixin {
    // The final vanilla dropStack overload covers death, replacement and conversion drops.
    @Inject(method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void cursecycle$noIssuedDrops(ItemStack stack, float yOffset, CallbackInfoReturnable<ItemEntity> cir) {
        if ((Object) this instanceof MobEntity mob && !mob.getWorld().isClient()
                && NetheriteMobsChallenge.isIssued(stack)) cir.setReturnValue(null);
    }
}
