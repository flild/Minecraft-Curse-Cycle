package validation.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test JAR only. The HUD methods and production gates run unmodified. */
@Mixin(value = RenderSystem.class, remap = false)
public abstract class RenderSystemProbeMixin {
    @Inject(method = {"enableBlend", "disableBlend", "defaultBlendFunc"},
            at = @At("HEAD"), cancellable = true)
    private static void skipGl(CallbackInfo ci) { ci.cancel(); }
}
