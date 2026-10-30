package validation.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseProbeMixin {
    @Inject(method = "onMouseButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getWindow()Lnet/minecraft/client/util/Window;", ordinal = 0), cancellable = true)
    private void probe$stop(long window, int button, int action, int mods, CallbackInfo ci) { ci.cancel(); }
}
