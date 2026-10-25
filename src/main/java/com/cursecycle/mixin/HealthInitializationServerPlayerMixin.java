package com.cursecycle.mixin;

import com.cursecycle.challenge.HealthInitialization;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class HealthInitializationServerPlayerMixin implements HealthInitialization {
    @Unique private boolean cursecycle$healthReady;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void cursecycle$ready(CallbackInfo ci) { cursecycle$healthReady = true; }
    @Override public boolean cursecycle$healthInitialized() { return cursecycle$healthReady; }
}
