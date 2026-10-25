package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.InventoryLockdownChallenge;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;

@Mixin(PlayerInventory.class)
public abstract class InventoryLockdownPlayerInventoryMixin {
    @Shadow @Final public PlayerEntity player;

    @Redirect(method = {"getEmptySlot", "getSlotWithStack", "indexOf", "getOccupiedSlotWithRoomForStack"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/DefaultedList;size()I"))
    private int cursecycle$searchOnlyHotbar(DefaultedList<?> stacks) {
        return InventoryLockdownChallenge.isActiveForPlayer(player) ? PlayerInventory.getHotbarSize() : stacks.size();
    }

    @Redirect(method = "getOccupiedSlotWithRoomForStack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerInventory;getStack(I)Lnet/minecraft/item/ItemStack;"))
    private ItemStack cursecycle$skipOffhandPickup(PlayerInventory inventory, int slot) {
        return InventoryLockdownChallenge.isLockedIndex(slot) && InventoryLockdownChallenge.isActiveForPlayer(player)
                ? ItemStack.EMPTY : inventory.getStack(slot);
    }

    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void cursecycle$blockExplicitInsertion(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (InventoryLockdownChallenge.isLockedIndex(slot) && InventoryLockdownChallenge.isActiveForPlayer(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "swapSlotWithHotbar", at = @At("HEAD"), cancellable = true)
    private void cursecycle$blockPickFromInventory(int slot, CallbackInfo ci) {
        if (InventoryLockdownChallenge.isLockedIndex(slot) && InventoryLockdownChallenge.isActiveForPlayer(player)) {
            ci.cancel();
        }
    }

    @Redirect(method = "populateRecipeFinder", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/collection/DefaultedList;iterator()Ljava/util/Iterator;"))
    private Iterator<ItemStack> cursecycle$matchOnlyHotbar(DefaultedList<ItemStack> stacks) {
        return InventoryLockdownChallenge.isActiveForPlayer(player)
                ? stacks.subList(0, PlayerInventory.getHotbarSize()).iterator() : stacks.iterator();
    }
}
