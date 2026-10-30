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
        int width = Math.max(20, Math.min(120, context.getScaledWindowWidth() - 16));
        int x = (context.getScaledWindowWidth() - width) / 2;
        int y = Math.max(15, context.getScaledWindowHeight() - 62);
        int phase = (int) Math.floorMod(age - state.target() + 10, 20);
        context.getMatrices().push();
        try {
            context.getMatrices().translate(0, 0, 400);
            context.fill(x, y, x + width, y + 5, 0xCC101018);
            context.fill(x + 5 * (width - 1) / 20, y, x + 15 * (width - 1) / 20 + 2, y + 5, 0xFF555577);
            int target = x + 10 * (width - 1) / 20;
            context.fill(target, y, target + 1, y + 5, 0xFFCCCCDD);
            int cursor = x + phase * (width - 1) / 20;
            context.fill(cursor, y - 1, cursor + 2, y + 6, state.accepted() ? 0xFF55FF55 : 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(client.textRenderer, label, x + width / 2, y - 12, 0xFFFFFF);
        } finally { context.getMatrices().pop(); }
    }
}
