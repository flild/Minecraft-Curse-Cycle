package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.InventoryLockdownChallenge;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class InventoryLockdownServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/packet/c2s/play/PlayerActionC2SPacket;getAction()Lnet/minecraft/network/packet/c2s/play/PlayerActionC2SPacket$Action;"),
            cancellable = true)
    private void cursecycle$blockHandSwap(PlayerActionC2SPacket packet, CallbackInfo ci) {
        // After forceMainThread, before either hand is changed. Vanilla does not predict this swap.
        if (packet.getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && InventoryLockdownChallenge.isActiveForPlayer(player)) {
            ci.cancel();
        }
    }
}
