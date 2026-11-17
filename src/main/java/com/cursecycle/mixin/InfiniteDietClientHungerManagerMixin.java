package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.InfiniteDietChallenge;
import com.cursecycle.client.ClientDataHolder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.HungerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HungerManager.class)
public abstract class InfiniteDietClientHungerManagerMixin {
    // Suppress local food/cake prediction, but let server nutrition packets use the setters.
    @Inject(method = "addInternal", at = @At("HEAD"), cancellable = true)
    private void cursecycle$preventDietPrediction(int nutrition, float saturation, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (client != null && client.player != null
                && client.player.getHungerManager() == (Object) this
                && !client.player.isCreative() && !client.player.isSpectator()
                && ClientDataHolder.isSynced()
                && ClientDataHolder.getActiveChallengeIds().contains(InfiniteDietChallenge.ID)) {
            ci.cancel();
        }
    }
}
