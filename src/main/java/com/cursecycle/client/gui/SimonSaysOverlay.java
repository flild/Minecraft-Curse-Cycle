package com.cursecycle.client.gui;

import com.cursecycle.challenge.impl.SimonSaysChallenge;
import com.cursecycle.client.SimonSaysVisuals;
import com.cursecycle.network.SimonSaysStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Uses the journal/HUD materials; stays visible while typing the chat task. */
public final class SimonSaysOverlay {
    private SimonSaysOverlay() {}

    public static void render(DrawContext context) {
        var client = MinecraftClient.getInstance();
        if (!SimonSaysVisuals.valid(client) || client.options.hudHidden || client.getOverlay() != null) return;
        renderFeedback(context, SimonSaysVisuals.state(), SimonSaysVisuals.remaining());
    }

    public static void renderFeedback(DrawContext context, SimonSaysStatePayload state, int remaining) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        int width = 180;
        boolean command = state.phase() == SimonSaysStatePayload.COMMAND;
        boolean waiting = state.phase() == SimonSaysStatePayload.WAITING;
        Text label = label(state);
        var lines = renderer.wrapLines(label, width - 14);
        int height = 32 + lines.size() * 11;
        float scale = HudLayout.scale(context.getScaledWindowWidth(), context.getScaledWindowHeight());
        int color = state.phase() == SimonSaysStatePayload.FAILURE || command && remaining <= 20
                ? 0xFFDE857E : command ? CurseStyle.PURPLE : CurseStyle.BRONZE;
        context.getMatrices().push();
        try {
            context.getMatrices().translate((context.getScaledWindowWidth() - width * scale) / 2f, 6, 0);
            context.getMatrices().scale(scale, scale, 1);
            CurseStyle.panel(context, 0, 0, width, height);
            CurseStyle.icon(context, SimonSaysChallenge.ID, 5, 3);
            context.drawTextWithShadow(renderer, Text.translatable("text.cursecycle.simon_says.title"),
                    25, 7, CurseStyle.BRONZE);
            if (command || waiting) {
                Text timer = Text.translatable("text.cursecycle.simon_says.seconds", (remaining + 19) / 20);
                context.drawTextWithShadow(renderer, timer, width - renderer.getWidth(timer) - 7, 7, color);
            }
            int y = 22;
            for (var line : lines) {
                context.drawTextWithShadow(renderer, line, 7, y, waiting ? CurseStyle.MUTED : CurseStyle.TEXT);
                y += 11;
            }
            context.fill(7, height - 7, width - 7, height - 4, CurseStyle.EDGE);
            int fill = command || waiting ? (int) ((width - 14) * Math.clamp(
                    remaining / (double) Math.max(1, state.duration()), 0, 1)) : width - 14;
            if (fill > 0) context.fill(7, height - 7, 7 + fill, height - 4, color);
        } finally { context.getMatrices().pop(); }
    }

    public static int height(SimonSaysStatePayload state) {
        return 32 + MinecraftClient.getInstance().textRenderer.wrapLines(label(state), 166).size() * 11;
    }
    private static Text label(SimonSaysStatePayload state) {
        return Text.translatable(state.phase() == SimonSaysStatePayload.COMMAND ? "text.cursecycle.simon_says." + state.action()
                : state.phase() == SimonSaysStatePayload.WAITING ? "text.cursecycle.simon_says.waiting"
                : state.phase() == SimonSaysStatePayload.SUCCESS ? "text.cursecycle.simon_says.success"
                : "text.cursecycle.simon_says.failure");
    }
}
