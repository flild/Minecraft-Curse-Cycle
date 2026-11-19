package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.HunterMark;
import com.cursecycle.challenge.impl.SkeletonHunterChallenge;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(MobEntity.class)
public abstract class SkeletonHunterMobMixin implements HunterMark {
    @Unique private UUID cursecycle$owner;
    @Unique private UUID cursecycle$token;
    @Unique private long cursecycle$activation;

    @Override public UUID cursecycle$hunterOwner() { return cursecycle$owner; }
    @Override public UUID cursecycle$hunterToken() { return cursecycle$token; }
    @Override public long cursecycle$hunterActivation() { return cursecycle$activation; }
    @Override public void cursecycle$markHunter(UUID owner, UUID token, long activation) {
        cursecycle$owner = owner; cursecycle$token = token; cursecycle$activation = activation;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void cursecycle$saveHunter(NbtCompound nbt, CallbackInfo ci) {
        if (cursecycle$owner == null) return;
        var mark = new NbtCompound();
        mark.putUuid("owner", cursecycle$owner); mark.putUuid("token", cursecycle$token);
        mark.putLong("activation", cursecycle$activation);
        nbt.put("cursecycle:skeleton_hunter", mark);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void cursecycle$loadHunter(NbtCompound nbt, CallbackInfo ci) {
        var mark = nbt.getCompound("cursecycle:skeleton_hunter");
        cursecycle$owner = mark.containsUuid("owner") ? mark.getUuid("owner") : null;
        cursecycle$token = mark.containsUuid("token") ? mark.getUuid("token") : null;
        cursecycle$activation = mark.getLong("activation");
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void cursecycle$ownerOnly(LivingEntity target, CallbackInfo ci) {
        if (cursecycle$owner == null) return;
        var owner = SkeletonHunterChallenge.target((MobEntity) (Object) this);
        // Keep the owner beyond vanilla acquisition range; never retaliate against bystanders.
        if (target != owner) ci.cancel();
    }

    @Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
    private void cursecycle$pursueOwner(CallbackInfo ci) {
        if (cursecycle$owner == null) return;
        var mob = (MobEntity) (Object) this;
        var owner = SkeletonHunterChallenge.target(mob);
        mob.setTarget(owner);
        if (owner == null) {
            mob.getNavigation().stop();
            ci.cancel();
        } else if (mob.age % 20 == 0 && mob.squaredDistanceTo(owner) > 144) {
            mob.getNavigation().startMovingTo(owner, 1.0);
        }
    }

    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void cursecycle$keepHunter(CallbackInfo ci) {
        // Peaceful pauses this owned entity, rather than silently deleting it as a hostile mob.
        if (cursecycle$owner != null) ci.cancel();
    }

    @Inject(method = "isAffectedByDaylight", at = @At("HEAD"), cancellable = true)
    private void cursecycle$hunterDaylight(CallbackInfoReturnable<Boolean> cir) {
        if (cursecycle$owner != null) cir.setReturnValue(false);
    }

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void cursecycle$noHunterEquipmentDrops(CallbackInfo ci) {
        // Zero drop chance alone can still be increased by Looting. Ordinary loot/XP remain vanilla.
        if (cursecycle$owner != null) ci.cancel();
    }
}
