package com.cursecycle.mixin;

import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.impl.WarrantyExpiredChallenge;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class WarrantyExpiredItemStackMixin {
    // Both equipment and direct server uses converge here. Scale once BEFORE Unbreaking;
    // leave vanilla damageability, Creative, repair and the single break callback intact.
    @ModifyVariable(method = "damage(ILnet/minecraft/server/world/ServerWorld;Lnet/minecraft/server/network/ServerPlayerEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int cursecycle$accelerateWear(int amount, int requested, ServerWorld world,
                                         ServerPlayerEntity player, Consumer<Item> breakCallback) {
        if (amount <= 0 || !ChallengeActivity.active(player, WarrantyExpiredChallenge.ID)) return amount;
        var stack = (ItemStack) (Object) this;
        // Also avoid overflowing vanilla's later currentDamage + amount addition.
        return (int) Math.min((long) amount * WarrantyExpiredChallenge.WEAR_MULTIPLIER,
                (long) Integer.MAX_VALUE - stack.getDamage());
    }
}
