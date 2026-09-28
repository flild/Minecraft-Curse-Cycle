package com.example.hardcorechallenges.mixin;

import com.example.hardcorechallenges.api.event.ChallengeEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "setSprinting", at = @At("HEAD"))
    private void onSetSprinting(boolean sprinting, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            boolean wasSprinting = player.isSprinting();
            if (sprinting && !wasSprinting) {
                ChallengeEvents.ON_SPRINT.invoker().onSprintStart(player);
            } else if (!sprinting && wasSprinting) {
                ChallengeEvents.ON_SPRINT.invoker().onSprintStop(player);
            }
        }
    }
}
