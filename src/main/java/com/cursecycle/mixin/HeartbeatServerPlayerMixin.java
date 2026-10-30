package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.HeartbeatChallenge;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class HeartbeatServerPlayerMixin {
    @Inject(method = "changeGameMode", at = @At("RETURN"))
    private void cursecycle$heartbeatMode(GameMode mode, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) HeartbeatChallenge.sync((ServerPlayerEntity) (Object) this);
    }
}
