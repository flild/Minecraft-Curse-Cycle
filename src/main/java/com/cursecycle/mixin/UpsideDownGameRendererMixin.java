package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.UpsideDownCameraChallenge;
import com.cursecycle.client.UpsideDownCamera;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class UpsideDownGameRendererMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow private boolean renderingPanorama;

    @ModifyExpressionValue(method = {"renderWorld", "renderHand"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;getBasicProjectionMatrix(D)Lorg/joml/Matrix4f;"))
    private Matrix4f cursecycle$rollProjection(Matrix4f projection) {
        // Each call creates a fresh matrix: world, its culling frustum, and the independent hand pass.
        // P * roll * hurt * bob * view preserves vanilla effects without touching Camera/raycast yaw/pitch.
        // GUI uses its own projection outside these two methods. Never roll the shared model-view stack.
        if (!renderingPanorama && UpsideDownCamera.shouldApply(client)) {
            projection.rotateZ((float) Math.toRadians(UpsideDownCameraChallenge.ROLL_DEGREES));
        }
        return projection;
    }
}
