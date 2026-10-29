package com.cursecycle.mixin;

import com.cursecycle.client.BlindHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class BlindHudInGameHudMixin {
    @Shadow @Final private MinecraftClient client;

    // In 1.21.1 status bars contain only the player's hearts (all variants and
    // absorption), armor, food and air. Mount health/jump and hotbar are separate.
    @Inject(method = {"renderStatusBars", "renderExperienceBar", "renderExperienceLevel"},
            at = @At("HEAD"), cancellable = true)
    private void cursecycle$hidePersonalResources(CallbackInfo ci) {
        if (BlindHud.shouldApply(client)) ci.cancel();
    }
}
