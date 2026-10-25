package com.cursecycle.mixin;

import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.HealthInitialization;
import com.cursecycle.challenge.impl.NoHealingChallenge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class NoHealingLivingEntityMixin {
    @Unique private boolean cursecycle$loadingHealth;

    @WrapMethod(method = "readCustomDataFromNbt")
    private void cursecycle$loadSavedHealth(NbtCompound nbt, Operation<Void> original) {
        cursecycle$loadingHealth = true;
        try { original.call(nbt); }
        finally { cursecycle$loadingHealth = false; }
    }

    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float cursecycle$preventHealing(float health) {
        // The server is assigned before the interaction manager is ready: exclude the entire constructor.
        if (!cursecycle$loadingHealth && (Object) this instanceof ServerPlayerEntity player
                && ((HealthInitialization) player).cursecycle$healthInitialized()
                && player.getServer() != null && ChallengeActivity.active(player, NoHealingChallenge.ID)
                && health > player.getHealth()) return player.getHealth();
        return health;
    }
}
