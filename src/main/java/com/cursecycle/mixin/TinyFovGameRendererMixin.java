package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.TinyFovChallenge;
import com.cursecycle.client.TinyFovCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class TinyFovGameRendererMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow private boolean renderingPanorama;

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void cursecycle$sceneFov(Camera camera, float tickDelta, boolean changingFov,
                                     CallbackInfoReturnable<Double> cir) {
        // renderWorld passes true; renderHand passes false. Vanilla has already applied
        // interpolated sprint/speed/bow/spyglass and submersion modifiers at this point.
        if (changingFov && !renderingPanorama && TinyFovCamera.shouldApply(client)) {
            cir.setReturnValue(TinyFovChallenge.FOV_DEGREES);
        }
    }
}
