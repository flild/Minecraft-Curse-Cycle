package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.OneHeartChallenge;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InGameHud.class)
public abstract class OneHeartInGameHudMixin {
    @Shadow private PlayerEntity getCameraPlayer() { throw new AssertionError(); }

    @ModifyExpressionValue(method = "renderStatusBars", at = @At(value = "INVOKE",
            target = "Ljava/lang/Math;max(FF)F"))
    private float cursecycle$visibleMaximum(float maximum) {
        var player = getCameraPlayer();
        // Vanilla reads the raw attribute and the old damage-animation HP, bypassing getMaxHealth.
        return player != null && OneHeartChallenge.active(player) ? Math.min(maximum, player.getMaxHealth()) : maximum;
    }

    @ModifyExpressionValue(method = "renderStatusBars", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;getAbsorptionAmount()F"))
    private float cursecycle$visibleAbsorption(float absorption) {
        var player = getCameraPlayer();
        return player != null && OneHeartChallenge.active(player) ? 0 : absorption;
    }
}
