package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.AutoJumpChallenge;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class AutoJumpLivingEntityMixin {
    // This field write is absent from the early return for zero jump strength and from cancellation.
    @Inject(method = "jump", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/LivingEntity;velocityDirty:Z", opcode = 181, shift = At.Shift.AFTER))
    private void cursecycle$successfulJump(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) AutoJumpChallenge.jumped(player);
    }
}
