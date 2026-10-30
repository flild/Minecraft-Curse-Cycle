package validation.mixin;

import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stop vanilla GLFW/screen dispatch only AFTER the production raw-edge injection. */
@Mixin(Keyboard.class)
public class KeyboardProbeMixin {
    @Inject(method = "onKey", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getWindow()Lnet/minecraft/client/util/Window;", ordinal = 0), cancellable = true)
    private void probe$stop(long window, int key, int scan, int action, int mods, CallbackInfo ci) { ci.cancel(); }
}
