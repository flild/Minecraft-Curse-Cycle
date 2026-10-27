package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.LockedContainersChallenge;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayerEntity.class)
public abstract class LockedContainersServerPlayerMixin {
    @ModifyExpressionValue(method = "openHandledScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/screen/NamedScreenHandlerFactory;createMenu(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/screen/ScreenHandler;"))
    private ScreenHandler cursecycle$denyStorageScreen(ScreenHandler handler) {
        var player = (ServerPlayerEntity) (Object) this;
        if (!LockedContainersChallenge.blocks(handler, player)) return handler;
        // Constructors call Inventory.onOpen. Balance it without ever sending an
        // OpenScreen packet or installing this handler as the player's screen.
        handler.onClosed(player);
        return null;
    }
}
