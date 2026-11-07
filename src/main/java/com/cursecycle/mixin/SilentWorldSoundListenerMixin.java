package com.cursecycle.mixin;

import com.cursecycle.client.SilentWorld;
import com.cursecycle.client.SilentWorldOutput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SoundListener.class)
public abstract class SilentWorldSoundListenerMixin implements SilentWorldOutput {
    @Unique private boolean cursecycle$muted;

    @Override
    public void cursecycle$refreshOutput() {
        if (cursecycle$muted != SilentWorld.shouldMute(MinecraftClient.getInstance())) {
            var listener = (SoundListener) (Object) this;
            listener.setVolume(listener.getVolume());
        }
    }

    // Keep SoundListener.volume intact: SoundSystem uses it to decide whether to start sounds.
    // Only the final OpenAL gain changes, so subtitles and source timelines remain vanilla.
    @ModifyArg(method = "setVolume", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/openal/AL10;alListenerf(IF)V", remap = false), index = 1)
    private float cursecycle$muteOutput(float volume) {
        // Every real setter, including sliders and device start, records the submitted state.
        cursecycle$muted = SilentWorld.shouldMute(MinecraftClient.getInstance());
        return cursecycle$muted ? 0.0F : volume;
    }
}
