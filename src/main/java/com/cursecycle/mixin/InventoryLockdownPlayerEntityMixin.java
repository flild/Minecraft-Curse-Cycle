package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.InventoryLockdownChallenge;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class InventoryLockdownPlayerEntityMixin {
    @Inject(method = "canEquip", at = @At("HEAD"), cancellable = true)
    private void cursecycle$blockAutomaticEquipping(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        // Dispensers use canEquip instead of canUseSlot to select a recipient.
        if (player.getPreferredEquipmentSlot(stack) != EquipmentSlot.MAINHAND
                && InventoryLockdownChallenge.isActiveForPlayer(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canUseSlot", at = @At("HEAD"), cancellable = true)
    private void cursecycle$blockEquipping(EquipmentSlot slot, CallbackInfoReturnable<Boolean> cir) {
        // Equipment.equipAndSwap checks this before moving either stack.
        if (slot != EquipmentSlot.MAINHAND && (Object) this instanceof PlayerEntity player
                && InventoryLockdownChallenge.isActiveForPlayer(player)) {
            cir.setReturnValue(false);
        }
    }
}
