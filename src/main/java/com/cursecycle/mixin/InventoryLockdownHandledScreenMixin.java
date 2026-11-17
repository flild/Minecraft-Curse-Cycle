package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.InventoryLockdownChallenge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class InventoryLockdownHandledScreenMixin {
    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void cursecycle$markLockedSlot(DrawContext context, Slot slot, CallbackInfo ci) {
        if (!InventoryLockdownChallenge.isLockedSlot(slot)) return;

        var matrices = context.getMatrices();
        matrices.push();
        // Above slot items/counts, below the dragged stack and tooltips.
        matrices.translate(0, 0, 300);
        context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xA0000000);
        for (int step = 0; step < 6; step++) {
            int x = slot.x + 2 + step * 2;
            int y = slot.y + 2 + step * 2;
            int oppositeY = slot.y + 12 - step * 2;
            context.fill(x, y, x + 2, y + 2, 0xFFDE857E);
            context.fill(x, oppositeY, x + 2, oppositeY + 2, 0xFFDE857E);
        }
        matrices.pop();
    }
}
