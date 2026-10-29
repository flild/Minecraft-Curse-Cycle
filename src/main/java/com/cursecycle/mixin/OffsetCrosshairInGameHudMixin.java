package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.OffsetCrosshairChallenge;
import com.cursecycle.client.OffsetCrosshair;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(InGameHud.class)
public abstract class OffsetCrosshairInGameHudMixin {
    @Shadow @Final private MinecraftClient client;

    @WrapMethod(method = "renderCrosshair")
    private void cursecycle$offsetCrosshair(DrawContext context, RenderTickCounter tickCounter,
                                           Operation<Void> original) {
        if (!OffsetCrosshair.shouldApply(client)) {
            original.call(context, tickCounter);
            return;
        }
        var matrices = context.getMatrices();
        matrices.push();
        try {
            matrices.translate(OffsetCrosshair.translation(context.getScaledWindowWidth(), OffsetCrosshairChallenge.OFFSET_X),
                    OffsetCrosshair.translation(context.getScaledWindowHeight(), OffsetCrosshairChallenge.OFFSET_Y), 0);
            // Vanilla also multiplies this matrix into its separate debug 3D crosshair model-view.
            // Keep all visibility/attack-indicator decisions and draw exactly one vanilla crosshair.
            original.call(context, tickCounter);
        } finally {
            matrices.pop();
        }
    }
}
