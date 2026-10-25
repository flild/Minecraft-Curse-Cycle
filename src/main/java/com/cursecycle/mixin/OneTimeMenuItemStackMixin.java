package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.OneTimeMenuChallenge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class OneTimeMenuItemStackMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void cursecycle$start(World world, PlayerEntity player, Hand hand,
                                  CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.get(DataComponentTypes.FOOD) != null
                && OneTimeMenuChallenge.blocked(player, Registries.ITEM.getId(stack.getItem()).toString()))
            cir.setReturnValue(TypedActionResult.fail(stack));
    }

    @WrapMethod(method = "finishUsing")
    private ItemStack cursecycle$finish(World world, LivingEntity user, Operation<ItemStack> original) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!(user instanceof PlayerEntity player) || stack.get(DataComponentTypes.FOOD) == null)
            return original.call(world, user);
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        if (OneTimeMenuChallenge.blocked(player, id)) return stack;
        int count = stack.getCount();
        ItemStack result = original.call(world, user);
        if (stack.getCount() < count || result.getItem() != stack.getItem())
            OneTimeMenuChallenge.consumed(player, id);
        return result;
    }
}
