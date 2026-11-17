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
        int barWidth = Math.min(120, width - 24);
        if (barWidth < 20 || height < 90) return;
        int x = (width - barWidth) / 2, y = height - 70;
        int color = state.exhausted() || state.value() <= 20 ? 0xFFDE857E : CurseStyle.BRONZE;
        // Stepped bronze frame and recessed track share the journal and wheel palette.
        context.fill(x - 3, y - 2, x + barWidth + 3, y + 8, CurseStyle.PANEL);
        context.fill(x - 2, y - 1, x + barWidth + 2, y + 7, CurseStyle.EDGE);
        context.fill(x - 1, y, x + barWidth + 1, y + 6, 0xFF0D0B12);
        context.fill(x - 3, y + 1, x - 2, y + 5, CurseStyle.BRONZE);
        context.fill(x + barWidth + 2, y + 1, x + barWidth + 3, y + 5, CurseStyle.BRONZE);
        int filled = barWidth * state.value() / HeavyBodyChallenge.MAX_STAMINA;
        if (filled > 0) {
            context.fill(x, y + 1, x + filled, y + 5, color);
            context.fill(x, y + 1, x + filled, y + 2,
                    state.exhausted() || state.value() <= 20 ? 0xFFF0B1A4 : 0xFFD8B675);
        }
        for (int i = 1; i < 10; i++) {
            int divider = x + barWidth * i / 10;
            context.fill(divider, y + 1, divider + 1, y + 5, 0xFF0D0B12);
        }
        var label = Text.translatable(state.exhausted() ? "text.cursecycle.heavy_body.recovering"
                : "text.cursecycle.heavy_body.stamina");
        context.drawCenteredTextWithShadow(client.textRenderer, label, width / 2, y - 11,
                state.exhausted() ? color : CurseStyle.TEXT);
    }
}
