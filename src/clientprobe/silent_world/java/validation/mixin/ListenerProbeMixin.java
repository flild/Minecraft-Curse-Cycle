package validation.mixin;

import net.minecraft.client.sound.SoundListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import validation.SilentWorldProbe;

/** Counts real setter calls without replacing the production filter or OpenAL call. */
@Mixin(SoundListener.class)
public abstract class ListenerProbeMixin {
    @Inject(method = "setVolume", at = @At("HEAD"))
    private void probe$countVolume(float volume, CallbackInfo ci) {
        SilentWorldProbe.volumeCalls++;
    }
}
