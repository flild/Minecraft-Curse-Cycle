package com.cursecycle.client.gui;

import com.cursecycle.challenge.impl.HeavyBodyChallenge;
import com.cursecycle.client.BlindHud;
import com.cursecycle.client.HeavyBodyInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Exhaustion obscures vision even with F1; its resource bar follows HUD visibility. */
public final class HeavyBodyOverlay {
    private HeavyBodyOverlay() {}

    public static void render(DrawContext context) {
        var client = MinecraftClient.getInstance();
        if (!HeavyBodyInput.valid(client)) return;
        var state = HeavyBodyInput.stamina();
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        int alpha = (int) (255 * state.darkness());
        if (alpha > 0) context.fill(0, 0, width, height, alpha << 24);
        if (client.options.hudHidden || client.currentScreen != null || BlindHud.shouldApply(client)
                || !HeavyBodyChallenge.onFoot(client.player)
                || !state.visible() && !client.player.isSprinting()) return;
        int barWidth = Math.min(100, width - 16);
        if (barWidth < 20 || height < 90) return;
        int x = (width - barWidth) / 2, y = height - 70;
        context.fill(x - 1, y - 1, x + barWidth + 1, y + 5, 0xD016151D);
        context.fill(x, y, x + barWidth, y + 4, CurseStyle.EDGE);
        context.fill(x, y, x + barWidth * state.value() / HeavyBodyChallenge.MAX_STAMINA, y + 4,
                state.exhausted() ? 0xFFDE857E : CurseStyle.BRONZE);
        var label = Text.translatable(state.exhausted() ? "text.cursecycle.heavy_body.recovering"
                : "text.cursecycle.heavy_body.stamina");
        context.drawCenteredTextWithShadow(client.textRenderer, label, width / 2, y - 11, CurseStyle.TEXT);
    }
}
