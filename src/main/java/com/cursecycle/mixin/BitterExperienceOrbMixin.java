package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.BitterExperienceChallenge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ExperienceOrbEntity.class)
public abstract class BitterExperienceOrbMixin {
    @Shadow private int pickingCount;

    @WrapMethod(method = "onPlayerCollision")
    private void cursecycle$afterConsumedPortion(PlayerEntity player, Operation<Void> original) {
        boolean available = !((ExperienceOrbEntity) (Object) this).isRemoved();
        int before = pickingCount;
        original.call(player);
        // A cooldown/early return consumes nothing. Mending may consume all the XP,
        // so neither addExperience nor the orb's nominal XP value is a pickup signal.
        // Stack locals also keep nested calls independent without persistent state.
        if (available && pickingCount == before - 1 && player instanceof ServerPlayerEntity serverPlayer)
            BitterExperienceChallenge.afterOrbPickup(serverPlayer);
    }
}
