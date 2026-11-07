package validation.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import validation.HeartbeatProbe;

/** Capture transport only; production key matching, focus, edge and lifecycle gates still execute. */
@Mixin(value = ClientPlayNetworking.class, remap = false)
public class NetworkProbeMixin {
    @Inject(method = "canSend(Lnet/minecraft/network/packet/CustomPayload$Id;)Z", at = @At("HEAD"), cancellable = true)
    private static void probe$canSend(CustomPayload.Id<?> id, CallbackInfoReturnable<Boolean> ci) { ci.setReturnValue(HeartbeatProbe.canSend); }
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private static void probe$send(CustomPayload payload, CallbackInfo ci) { HeartbeatProbe.sent.add(payload); ci.cancel(); }
}
