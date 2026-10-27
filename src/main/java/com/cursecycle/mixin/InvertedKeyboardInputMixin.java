package com.cursecycle.mixin;

import com.cursecycle.client.InvertedKeyboardInput;
import com.cursecycle.client.BlockedDirectionInput;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class InvertedKeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("RETURN"))
    private void cursecycle$invertKeyboard(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        // Vanilla rebuilds all fields on every tick, so repeated ticks never invert old input twice.
        InvertedKeyboardInput.apply(this);
        // Explicit order: remove the chosen resulting component after inversion, before travel normalizes it.
        BlockedDirectionInput.apply(this);
    }
}
