package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.InfiniteDietChallenge;
import com.cursecycle.challenge.impl.InfiniteDietHungerOwner;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HungerManager.class)
public abstract class InfiniteDietHungerManagerMixin implements InfiniteDietHungerOwner {
    @Shadow private int foodLevel;
    @Shadow private float saturationLevel;
    @Unique private PlayerEntity cursecycle$dietOwner;

    @Override
    public void cursecycle$setDietOwner(PlayerEntity player) {
        cursecycle$dietOwner = player;
    }

    // FoodComponent, cake and /effect saturation all reach addInternal in 1.21.1.
    // Setters also cover direct changes and Peaceful's automatic hunger recovery.
    // NBT loading and construction deliberately retain vanilla saved/respawn values.
    @Redirect(method = {"addInternal", "setFoodLevel"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/player/HungerManager;foodLevel:I", opcode = Opcodes.PUTFIELD))
    private void cursecycle$limitFood(HungerManager manager, int requested) {
        foodLevel = InfiniteDietChallenge.isActiveForPlayer(cursecycle$dietOwner)
                ? Math.min(foodLevel, requested) : requested;
    }

    @Redirect(method = {"addInternal", "setSaturationLevel"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/player/HungerManager;saturationLevel:F", opcode = Opcodes.PUTFIELD))
    private void cursecycle$limitSaturation(HungerManager manager, float requested) {
        saturationLevel = InfiniteDietChallenge.isActiveForPlayer(cursecycle$dietOwner)
                ? Math.min(saturationLevel, requested) : requested;
    }
}
