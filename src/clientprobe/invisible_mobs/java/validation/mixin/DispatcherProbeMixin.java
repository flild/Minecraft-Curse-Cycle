package validation.mixin;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import validation.InvisibleMobsProbe;

/** Replaces only terminal GL/world draw work; dispatcher branches and production gates execute. */
@Mixin(EntityRenderDispatcher.class)
public abstract class DispatcherProbeMixin {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void shadow(CallbackInfo ci) { InvisibleMobsProbe.shadows++; ci.cancel(); }

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private void fire(CallbackInfo ci) { InvisibleMobsProbe.fires++; ci.cancel(); }

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void hitbox(CallbackInfo ci) { InvisibleMobsProbe.hitboxes++; ci.cancel(); }
}
