package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.LowGravityChallenge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

@Mixin(Explosion.class)
public abstract class LowGravityExplosionMixin {
    @Redirect(method = "collectBlocksAndDamageEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"))
    private void cursecycle$explosion(Entity target, Vec3d velocity) {
        if (target instanceof LivingEntity living && LowGravityChallenge.active(living)) {
            var previous = target.getVelocity();
            velocity = previous.add(velocity.subtract(previous).multiply(LowGravityChallenge.KNOCKBACK));
        }
        target.setVelocity(velocity);
    }

    // The explosion packet carries a separate impulse for client prediction; scale it identically, once.
    @Redirect(method = "collectBlocksAndDamageEntities", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object cursecycle$explosionPacket(Map<PlayerEntity, Vec3d> players, Object player, Object impulse) {
        return players.put((PlayerEntity) player,
                LowGravityChallenge.knockback((PlayerEntity) player, (Vec3d) impulse));
    }
}
