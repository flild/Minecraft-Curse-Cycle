package com.cursecycle.mixin;

import com.cursecycle.client.HeartbeatInput;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class HeartbeatKeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void cursecycle$heartbeat(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        HeartbeatInput.keyboard(window, key, scancode, action);
    }
}
