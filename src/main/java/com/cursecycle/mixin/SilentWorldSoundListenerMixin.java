package com.cursecycle.mixin;

import com.cursecycle.client.SilentWorld;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SoundListener.class)
public abstract class SilentWorldSoundListenerMixin {
    // Keep SoundListener.volume intact: SoundSystem uses it to decide whether to start sounds.
    // Only the final OpenAL gain changes, so subtitles and source timelines remain vanilla.
    @ModifyArg(method = "setVolume", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/openal/AL10;alListenerf(IF)V", remap = false), index = 1)
    private float cursecycle$muteOutput(float volume) {
        return SilentWorld.shouldMute(MinecraftClient.getInstance()) ? 0.0F : volume;
    }
}
