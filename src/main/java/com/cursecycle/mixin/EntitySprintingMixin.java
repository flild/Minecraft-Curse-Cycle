package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NoSprintChallenge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntitySprintingMixin {

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void onSetSprinting(boolean sprinting, CallbackInfo ci) {
        if (!sprinting) return;

        Entity self = (Entity) (Object) this;
        if (self instanceof PlayerEntity player) {
            if (NoSprintChallenge.isNoSprintActiveForPlayer(player)) {
                ci.cancel();
                if (player.isSprinting()) {
                    player.setSprinting(false);
                }
            }
        }
    }
}
