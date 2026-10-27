package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.LockedContainersChallenge;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class LockedContainersScreenHandlerMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void cursecycle$denyStaleStorageClick(int slot, int button, SlotActionType action,
                                                PlayerEntity player, CallbackInfo ci) {
        var handler = (ScreenHandler) (Object) this;
        if (!LockedContainersChallenge.blocks(handler, player)) return;
        if (player instanceof ServerPlayerEntity serverPlayer && player.currentScreenHandler == handler)
            serverPlayer.closeHandledScreen();
        ci.cancel();
    }
}
