package com.cursecycle.mixin;

import net.minecraft.client.sound.SoundListener;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public abstract class SilentWorldSoundSystemMixin {
    @Shadow private boolean started;
    @Shadow @Final private SoundListener listener;

    // Runs even while paused and before new UI/audio playback. Refreshing at the frame boundary
    // also restores the current gain after death, mode/world changes and disconnect to the menu.
    @Inject(method = {"tick(Z)V", "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            "updateListenerPosition(Lnet/minecraft/client/render/Camera;)V"}, at = @At("HEAD"))
    private void cursecycle$refreshOutput(CallbackInfo ci) {
        if (started) listener.setVolume(listener.getVolume());
    }
}
