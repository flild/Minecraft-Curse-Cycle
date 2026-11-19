package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.BuddingChallenge;
import com.cursecycle.challenge.impl.BuddingMark;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class BuddingMobMixin implements BuddingMark {
    @Unique private boolean cursecycle$buddingChild;
    @Unique private boolean cursecycle$buddingDeathHandled;

    @Override public boolean cursecycle$isBuddingChild() { return cursecycle$buddingChild; }
    @Override public void cursecycle$markBuddingChild() { cursecycle$buddingChild = true; }
    @Override public boolean cursecycle$claimBuddingDeath() {
        if (cursecycle$buddingDeathHandled) return false;
        cursecycle$buddingDeathHandled = true;
        return true;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void cursecycle$saveBudding(NbtCompound nbt, CallbackInfo ci) {
        if (cursecycle$buddingChild) nbt.putBoolean(BuddingChallenge.CHILD_NBT, true);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void cursecycle$loadBudding(NbtCompound nbt, CallbackInfo ci) {
        cursecycle$buddingChild = nbt.getBoolean(BuddingChallenge.CHILD_NBT);
        cursecycle$buddingDeathHandled = false;
    }

    @Inject(method = "convertTo", at = @At("RETURN"))
    private void cursecycle$keepBuddingOrigin(EntityType<?> type, boolean keepEquipment,
                                             CallbackInfoReturnable<MobEntity> cir) {
        // Drowning must not turn an exempt child into a fresh source of children.
        if (cursecycle$buddingChild && cir.getReturnValue() != null)
            ((BuddingMark) cir.getReturnValue()).cursecycle$markBuddingChild();
    }
}
