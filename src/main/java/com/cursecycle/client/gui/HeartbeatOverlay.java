package com.cursecycle.client.gui;

import com.cursecycle.challenge.impl.HeartbeatChallenge;
import com.cursecycle.client.HeartbeatInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Required rhythm feedback, independent of the optional general mod HUD and actionbar. */
public final class HeartbeatOverlay {
    private HeartbeatOverlay() {}
    public static void render(DrawContext context) {
        var client = MinecraftClient.getInstance();
        if (!HeartbeatInput.valid(client) || client.options.hudHidden || client.getOverlay() != null
                || HeartbeatInput.textInput(client.currentScreen)) return;
        var state = HeartbeatInput.state();
        long age = HeartbeatInput.age();
        var key = HeartbeatInput.key.getBoundKeyLocalizedText();
        Text label = age < HeartbeatChallenge.GRACE
                ? Text.translatable("text.cursecycle.heartbeat.grace", key, (HeartbeatChallenge.GRACE - age + 19) / 20)
                : Text.translatable(state.accepted() ? "text.cursecycle.heartbeat.accepted" : "text.cursecycle.heartbeat.beat", key);
        int width = Math.min(180, context.getScaledWindowWidth() - 16);
        if (width < 32 || context.getScaledWindowHeight() < 42) return;
        int x = (context.getScaledWindowWidth() - width) / 2;
        int y = Math.max(6, context.getScaledWindowHeight() - 108);
        int barX = x + 7, barY = y + 20, barWidth = width - 14;
        boolean preparing = age < HeartbeatChallenge.GRACE;
        // Before the first approach, keep the marker at the left instead of showing a false beat at GRACE.
        int phase = age < HeartbeatChallenge.GRACE + HeartbeatChallenge.INTERVAL / 2 ? 0
                : (int) Math.floorMod(age - state.target() + HeartbeatChallenge.INTERVAL / 2,
                        HeartbeatChallenge.INTERVAL);
        context.getMatrices().push();
        try {
            context.getMatrices().translate(0, 0, 400);
            CurseStyle.panel(context, x, y, width, 30);
            context.fill(barX, barY, barX + barWidth, barY + 4, CurseStyle.EDGE);
            if (preparing) {
                int progress = (int) (age * barWidth / HeartbeatChallenge.GRACE);
                context.fill(barX, barY, barX + progress, barY + 4, CurseStyle.BRONZE);
            } else {
                int windowStart = (HeartbeatChallenge.INTERVAL / 2 - HeartbeatChallenge.WINDOW)
                        * (barWidth - 2) / HeartbeatChallenge.INTERVAL;
                int windowEnd = (HeartbeatChallenge.INTERVAL / 2 + HeartbeatChallenge.WINDOW)
                        * (barWidth - 2) / HeartbeatChallenge.INTERVAL;
                context.fill(barX + windowStart, barY, barX + windowEnd + 2, barY + 4, CurseStyle.PURPLE);
                int target = barX + (barWidth - 2) / 2;
                context.fill(target, barY - 2, target + 2, barY + 6, CurseStyle.BRONZE);
                int cursor = barX + phase * (barWidth - 2) / HeartbeatChallenge.INTERVAL;
                context.fill(cursor, barY - 1, cursor + 2, barY + 5,
                        state.accepted() ? CurseStyle.BRONZE : CurseStyle.TEXT);
            }
            context.drawCenteredTextWithShadow(client.textRenderer, label, x + width / 2, y + 6,
                    state.accepted() ? CurseStyle.BRONZE : CurseStyle.TEXT);
        } finally { context.getMatrices().pop(); }
    }
}
