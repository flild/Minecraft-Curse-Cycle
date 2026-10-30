package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NetheriteMobSpawn;
import com.cursecycle.challenge.impl.NetheriteMobsChallenge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public abstract class NetheriteMobsWorldMixin {
    // Chunk deserialization and dimension transfer use different entry points.
    @Inject(method = "spawnEntity", at = @At("RETURN"))
    private void cursecycle$newMob(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && entity instanceof MobEntity)
            ((NetheriteMobSpawn) entity).cursecycle$recordBirth(NetheriteMobsChallenge.active((ServerWorld) (Object) this));
    }

    // Some conversions initialize equipment AFTER spawnEntity returns. Do not outfit at initialize's base RETURN.
    @Inject(method = "tickEntity", at = @At("HEAD"))
    private void cursecycle$finishInitialization(Entity entity, CallbackInfo ci) {
        if (entity instanceof MobEntity) ((NetheriteMobSpawn) entity).cursecycle$finishBirth();
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"))
    private void cursecycle$finishPassengerInitialization(Entity vehicle, Entity passenger, CallbackInfo ci) {
        if (passenger instanceof MobEntity) ((NetheriteMobSpawn) passenger).cursecycle$finishBirth();
    }
}
