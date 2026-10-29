package validation.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import validation.OffsetCrosshairProbe;

/** Probe JAR only: replace GL side effects, keeping the real HUD method and its matrix calculations. */
@Mixin(value = RenderSystem.class, remap = false)
public abstract class RenderSystemProbeMixin {
    @Inject(method = {"enableBlend", "disableBlend", "blendFuncSeparate(Lcom/mojang/blaze3d/platform/GlStateManager$SrcFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DstFactor;Lcom/mojang/blaze3d/platform/GlStateManager$SrcFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DstFactor;)V", "defaultBlendFunc", "applyModelViewMatrix"},
            at = @At("HEAD"), cancellable = true)
    private static void skipGl(CallbackInfo ci) { ci.cancel(); }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private static void captureDebug(int size, CallbackInfo ci) {
        OffsetCrosshairProbe.debugMatrix = new org.joml.Matrix4f(RenderSystem.getModelViewStack());
        OffsetCrosshairProbe.debugCalls++;
        ci.cancel();
    }
}
