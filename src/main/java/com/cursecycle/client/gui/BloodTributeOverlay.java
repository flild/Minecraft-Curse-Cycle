package com.cursecycle.client.gui;

import com.cursecycle.challenge.impl.BloodTributeChallenge;
import com.cursecycle.client.BloodTributeVisuals;
import com.cursecycle.client.SimonSaysVisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Required countdown feedback uses the journal panel and sits below Simon when paired. */
public final class BloodTributeOverlay {
    private BloodTributeOverlay() {}

    public static void render(DrawContext context) {
        var client = MinecraftClient.getInstance();
        if (!BloodTributeVisuals.valid(client) || client.options.hudHidden || client.getOverlay() != null) return;
        float scale = HudLayout.scale(context.getScaledWindowWidth(), context.getScaledWindowHeight());
        float top = 6 + (SimonSaysVisuals.valid(client) ? (SimonSaysOverlay.height(SimonSaysVisuals.state()) + 4) * scale : 0);
        renderFeedback(context, BloodTributeVisuals.remaining(), BloodTributeVisuals.state().duration(), top);
    }

    public static void renderFeedback(DrawContext context, int remaining, int duration, float top) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        int width = 180;
        boolean overdue = remaining <= 0;
        int color = remaining <= BloodTributeChallenge.WARNING ? 0xFFDE857E : CurseStyle.PURPLE;
        var label = Text.translatable(overdue ? "text.cursecycle.blood_tribute.hud_overdue"
                : "text.cursecycle.blood_tribute.task");
        var lines = renderer.wrapLines(label, width - 14);
        int height = 32 + lines.size() * 11;
        float scale = HudLayout.scale(context.getScaledWindowWidth(), context.getScaledWindowHeight());
        context.getMatrices().push();
        try {
            context.getMatrices().translate((context.getScaledWindowWidth() - width * scale) / 2f, top, 0);
            context.getMatrices().scale(scale, scale, 1);
            CurseStyle.panel(context, 0, 0, width, height);
            CurseStyle.icon(context, BloodTributeChallenge.ID, 5, 3);
            context.drawTextWithShadow(renderer, Text.translatable("text.cursecycle.blood_tribute.title"),
                    25, 7, CurseStyle.BRONZE);
            var timer = Text.translatable("text.cursecycle.blood_tribute.seconds", (Math.max(0, remaining) + 19) / 20);
            context.drawTextWithShadow(renderer, timer, width - renderer.getWidth(timer) - 7, 7, color);
            int y = 22;
            for (var line : lines) {
                context.drawTextWithShadow(renderer, line, 7, y, overdue ? color : CurseStyle.TEXT);
                y += 11;
            }
            context.fill(7, height - 7, width - 7, height - 4, CurseStyle.EDGE);
            int fill = (int) ((width - 14) * Math.clamp(remaining / (double) Math.max(1, duration), 0, 1));
            if (fill > 0) context.fill(7, height - 7, 7 + fill, height - 4, color);
        } finally { context.getMatrices().pop(); }
    }
}
