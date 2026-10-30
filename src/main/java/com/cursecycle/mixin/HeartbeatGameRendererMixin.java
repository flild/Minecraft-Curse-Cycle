package com.cursecycle.mixin;

import com.cursecycle.client.gui.HeartbeatOverlay;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class HeartbeatGameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;draw()V"))
    private void cursecycle$heartbeat(RenderTickCounter ticks, boolean tick, CallbackInfo ci, @Local DrawContext context) {
        HeartbeatOverlay.render(context);
    }
}
