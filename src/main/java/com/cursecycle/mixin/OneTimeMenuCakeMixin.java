package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.OneTimeMenuChallenge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.block.CakeBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CakeBlock.class)
public abstract class OneTimeMenuCakeMixin {
    @WrapMethod(method = "tryEat")
    private static ActionResult cursecycle$cake(WorldAccess world, BlockPos pos, BlockState state,
                                                PlayerEntity player, Operation<ActionResult> original) {
        if (OneTimeMenuChallenge.blocked(player, "minecraft:cake")) return ActionResult.FAIL;
        ActionResult result = original.call(world, pos, state, player);
        if (result.isAccepted()) OneTimeMenuChallenge.consumed(player, "minecraft:cake");
        return result;
    }
}
