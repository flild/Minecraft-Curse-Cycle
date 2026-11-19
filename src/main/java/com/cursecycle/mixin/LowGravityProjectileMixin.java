package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.LowGravityChallenge;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PersistentProjectileEntity.class)
public abstract class LowGravityProjectileMixin {
    // Punch uses addVelocity instead of takeKnockback. Preserve its direction and resistance calculation.
    @Redirect(method = "knockback", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;addVelocity(DDD)V"))
    private void cursecycle$projectile(LivingEntity target, double x, double y, double z) {
        var impulse = LowGravityChallenge.knockback(target, new Vec3d(x, y, z));
        target.addVelocity(impulse.x, impulse.y, impulse.z);
    }
}
