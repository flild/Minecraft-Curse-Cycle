package com.cursecycle.mixin;

import com.cursecycle.challenge.HealthInitialization;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class HealthInitializationServerPlayerMixin implements HealthInitialization {
    @Unique private boolean cursecycle$healthReady;
    @Unique private boolean cursecycle$copyingHealth;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void cursecycle$ready(CallbackInfo ci) { cursecycle$healthReady = true; }
    @Override public boolean cursecycle$healthInitialized() { return cursecycle$healthReady; }
    @Override public boolean cursecycle$copyingHealth() { return cursecycle$copyingHealth; }

    @WrapOperation(method = "copyFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;setHealth(F)V"))
    private void cursecycle$copyInitialHealth(ServerPlayerEntity player, float health, Operation<Void> original) {
        // These setters initialize a replacement object, including external base max-health values.
        boolean previous = cursecycle$copyingHealth;
        cursecycle$copyingHealth = true;
        try { original.call(player, health); }
        finally { cursecycle$copyingHealth = previous; }
    }
}
