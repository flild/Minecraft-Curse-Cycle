package com.cursecycle.mixin;

import com.cursecycle.client.HeartbeatInput;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class HeartbeatMouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void cursecycle$heartbeat(long window, int button, int action, int modifiers, CallbackInfo ci) {
        HeartbeatInput.mouse(window, button, action);
    }
}
