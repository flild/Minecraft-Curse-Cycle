package com.cursecycle.mixin;

import com.cursecycle.client.ForcedThirdPersonCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GameOptions.class)
public abstract class ForcedThirdPersonGameOptionsMixin {
    @ModifyVariable(method = "setPerspective", at = @At("HEAD"), argsOnly = true)
    private Perspective cursecycle$forceThirdPersonBack(Perspective requested) {
        return ForcedThirdPersonCamera.constrain(MinecraftClient.getInstance(), requested);
    }
}
