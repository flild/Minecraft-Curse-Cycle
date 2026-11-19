package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.SimonSaysChallenge;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class SimonSaysServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    // TAIL is reached only after forceMainThread and vanilla processing of an actual swing packet.
    @Inject(method = "onHandSwing", at = @At("TAIL"))
    private void cursecycle$observeSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
        SimonSaysChallenge.swung(player);
    }
}
