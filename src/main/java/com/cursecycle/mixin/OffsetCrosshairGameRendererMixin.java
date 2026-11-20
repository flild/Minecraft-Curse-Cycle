package com.cursecycle.mixin;

import com.cursecycle.client.OffsetCrosshair;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class OffsetCrosshairGameRendererMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow private boolean renderingPanorama;

    @ModifyExpressionValue(method = {"renderWorld", "renderHand"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;getBasicProjectionMatrix(D)Lorg/joml/Matrix4f;"))
    private Matrix4f cursecycle$offsetAimProjection(Matrix4f projection) {
        if (!renderingPanorama && OffsetCrosshair.shouldApply(client)) {
            var window = client.getWindow();
            OffsetCrosshair.offsetProjection(projection, window.getScaledWidth(), window.getScaledHeight());
        }
        return projection;
    }
}
