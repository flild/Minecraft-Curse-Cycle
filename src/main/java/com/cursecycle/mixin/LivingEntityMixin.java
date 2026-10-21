package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NoJumpingChallenge;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow public abstract boolean isClimbing();

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void onLivingJump(CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player) {
            if (NoJumpingChallenge.isNoJumpingActiveForPlayer(player)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "swimUpward", at = @At("HEAD"), cancellable = true)
    private void onSwimUpward(TagKey<Fluid> fluid, CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player) {
            if (NoJumpingChallenge.isNoJumpingActiveForPlayer(player)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void onTravelTail(Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player) {
            if (NoJumpingChallenge.isNoJumpingActiveForPlayer(player)) {
                LivingEntity self = (LivingEntity) (Object) this;
                if (self.isClimbing() && self.getVelocity().y > 0) {
                    Vec3d currentVel = self.getVelocity();
                    self.setVelocity(currentVel.x, 0.0, currentVel.z);
                }
            }
        }
    }
}
