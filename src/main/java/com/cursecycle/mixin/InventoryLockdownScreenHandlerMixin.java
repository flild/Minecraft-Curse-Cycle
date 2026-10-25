package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.InventoryLockdownChallenge;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class InventoryLockdownScreenHandlerMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void cursecycle$blockLockedClick(int slotId, int button, SlotActionType action,
                                            PlayerEntity player, CallbackInfo ci) {
        if (InventoryLockdownChallenge.blocksClick((ScreenHandler) (Object) this, slotId, button, action, player)) {
            ci.cancel();
        }
    }

    @Redirect(method = "internalOnSlotClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/screen/slot/Slot;canTakeItems(Lnet/minecraft/entity/player/PlayerEntity;)Z"))
    private boolean cursecycle$skipLockedCollection(Slot slot, PlayerEntity player) {
        return !InventoryLockdownChallenge.isLockedSlot(slot) && slot.canTakeItems(player);
    }

    @Redirect(method = {"internalOnSlotClick", "insertItem"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/screen/slot/Slot;canInsert(Lnet/minecraft/item/ItemStack;)Z"))
    private boolean cursecycle$skipLockedInsertion(Slot slot, ItemStack stack) {
        return !InventoryLockdownChallenge.isLockedSlot(slot) && slot.canInsert(stack);
    }

    @Redirect(method = "insertItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/screen/slot/Slot;getStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack cursecycle$skipLockedMerge(Slot slot) {
        // Vanilla merges into occupied stacks without calling canInsert. Hide only
        // this local read; the empty-slot branch is guarded above. No rollback.
        return InventoryLockdownChallenge.isLockedSlot(slot) ? ItemStack.EMPTY : slot.getStack();
    }
}
