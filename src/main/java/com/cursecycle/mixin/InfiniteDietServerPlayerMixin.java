package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.InfiniteDietHungerOwner;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class InfiniteDietServerPlayerMixin {
    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void cursecycle$rebindCopiedHunger(CallbackInfo ci) {
        // Alive respawns (e.g. returning from the End) reuse the old HungerManager.
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        ((InfiniteDietHungerOwner) self.getHungerManager()).cursecycle$setDietOwner(self);
    }
}
