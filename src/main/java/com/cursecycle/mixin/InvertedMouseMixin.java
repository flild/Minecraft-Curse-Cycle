package com.cursecycle.mixin;

import com.cursecycle.client.InvertedMouseInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Mouse.class)
public abstract class InvertedMouseMixin {
    @Shadow @Final private MinecraftClient client;

    @ModifyArgs(method = "updateMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void cursecycle$invertMouseLook(Args args) {
        // Vanilla has already applied sensitivity, smoothing/scoping and the current invertY setting.
        // Only this mouse call is changed: GUI input and absolute/server rotations keep their own paths.
        if (!InvertedMouseInput.shouldInvert(client)) return;
        args.set(0, -(double) args.get(0));
        args.set(1, -(double) args.get(1));
    }
}
