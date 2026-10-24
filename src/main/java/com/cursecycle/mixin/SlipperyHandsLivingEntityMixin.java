package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.SlipperyHandsChallenge;
import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class SlipperyHandsLivingEntityMixin {
    // This value drives BOTH ground acceleration and horizontal drag in travel.
    // Altering only drag would cause excessive acceleration on ordinary blocks.
    @Redirect(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/Block;getSlipperiness()F"))
    private float cursecycle$slipperyGround(Block block) {
        float original = block.getSlipperiness();
        return (Object) this instanceof PlayerEntity player
                ? SlipperyHandsChallenge.getSlipperiness(player, original) : original;
    }
}
