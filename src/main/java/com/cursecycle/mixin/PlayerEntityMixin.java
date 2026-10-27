package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NoJumpingChallenge;
import com.cursecycle.event.ChallengeEventManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void onPlayerJump(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (NoJumpingChallenge.isNoJumpingActiveForPlayer(self)) {
            ci.cancel();
            return;
        }

        if (self instanceof ServerPlayerEntity serverPlayer) {
            if (com.cursecycle.challenge.impl.AutoJumpChallenge.duplicateJump(serverPlayer)) {
                ci.cancel();
                return;
            }
            if (serverPlayer.getServer() != null) {
                ChallengeEventManager.dispatchToActive(
                    serverPlayer.getServer(),
                    serverPlayer,
                    c -> c.onPlayerJump(serverPlayer.getServer(), serverPlayer)
                );
            }
        }
    }
}
