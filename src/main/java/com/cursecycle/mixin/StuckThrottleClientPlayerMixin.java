package com.cursecycle.mixin;

import com.cursecycle.client.StuckThrottleInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class StuckThrottleClientPlayerMixin {
    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void cursecycle$requireExplicitSprint(CallbackInfoReturnable<Boolean> cir) {
        var client = MinecraftClient.getInstance();
        var player = (ClientPlayerEntity) (Object) this;
        // Synthetic forward edges (activation, landing, leaving a GUI) must not trigger double-tap sprint.
        if (client != null && client.player == player && StuckThrottleInput.shouldForce(client, player.input)
                && !client.options.sprintKey.isPressed()) cir.setReturnValue(false);
    }
}
