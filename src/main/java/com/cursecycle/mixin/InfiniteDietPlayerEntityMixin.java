package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.InfiniteDietHungerOwner;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class InfiniteDietPlayerEntityMixin {
    @Shadow protected HungerManager hungerManager;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cursecycle$bindHungerOwner(CallbackInfo ci) {
        ((InfiniteDietHungerOwner) hungerManager).cursecycle$setDietOwner((PlayerEntity) (Object) this);
    }
}
