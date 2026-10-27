package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.HeavyBodyChallenge;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class HeavyBodyLivingEntityMixin {
    // Only the ground branch of applyMovementInput: fluid travel and air control keep their own speeds.
    @Inject(method = "getMovementSpeed(F)F", at = @At("RETURN"), cancellable = true)
    private void cursecycle$heavyGround(float slipperiness, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof PlayerEntity player)
            cir.setReturnValue(HeavyBodyChallenge.scale(player, cir.getReturnValueF()));
    }

    // This local already contains jump strength, block multiplier and Jump Boost.
    @ModifyVariable(method = "jump", at = @At("STORE"), ordinal = 0)
    private float cursecycle$heavyJump(float impulse) {
        return (Object) this instanceof PlayerEntity player ? HeavyBodyChallenge.scale(player, impulse) : impulse;
    }
}
