package com.cursecycle.client.gui;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.client.BlockedDirectionInput;
import com.cursecycle.config.CurseCycleConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class CurseCycleHudOverlay {
    private CurseCycleHudOverlay() {}

    public static void render(DrawContext context, CurseCycleConfig config, long day, long ticksUntilNext,
                              List<String> activeIds, String nextId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!config.enableHudOverlay || client.options.hudHidden) return;
        var renderer = client.textRenderer;
        int width = Math.min(218, context.getScaledWindowWidth() - 12);
        if (width < 80) return;
        int textWidth = width - 42;
        List<HudEntry> entries = new ArrayList<>();
        int height = 30;
        for (String id : activeIds) {
            Challenge challenge = ChallengeRegistry.get(id);
            String name = challenge == null ? id : challenge.getName();
            String desc = challenge == null ? "" : challenge.getShortDescription();
            String feedback = BlockedDirectionInput.feedback(id);
            int difficultyWidth = challenge == null ? 0 : renderer.getWidth(challenge.getDifficulty().getDisplayName()) + 6;
            int nameHeight = renderer.wrapLines(Text.literal(name), Math.max(24, textWidth - difficultyWidth)).size() * 11;
            int descHeight = Math.min(2, renderer.wrapLines(Text.literal(desc), textWidth - 6).size()) * 11;
            int rowHeight = Math.max(22, nameHeight + descHeight + (feedback.isEmpty() ? 0 : 11)) + 8;
            entries.add(new HudEntry(id, challenge, name, desc, feedback, rowHeight));
            height += rowHeight;
        }
        if (entries.isEmpty()) height += 19;
        boolean next = nextId != null && !nextId.isEmpty();
        if (next) height += 25;
        int x = Math.clamp(config.hudPositionX, 0, Math.max(0, context.getScaledWindowWidth() - width - 6));
        int y = Math.clamp(config.hudPositionY, 0, Math.max(0, context.getScaledWindowHeight() - height - 6));
        int visibleHeight = Math.min(height, context.getScaledWindowHeight() - y - 6);
        CurseStyle.panel(context, x, y, width, visibleHeight);
        context.enableScissor(x + 1, y + 1, x + width - 1, y + visibleHeight - 1);
        context.drawTextWithShadow(renderer, Text.translatable("text.cursecycle.day", day),
                x + 9, y + 9, CurseStyle.BRONZE);
        long seconds = Math.max(0, ticksUntilNext) / 20;
        String timer = String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        context.drawTextWithShadow(renderer, timer, x + width - renderer.getWidth(timer) - 9, y + 9,
                ticksUntilNext <= 1200 ? CurseStyle.PURPLE : CurseStyle.TEXT);
        context.fill(x + 9, y + 23, x + width - 9, y + 24, CurseStyle.EDGE);
        int currentY = y + 31;
        if (entries.isEmpty()) {
            context.drawTextWithShadow(renderer, Text.translatable("text.cursecycle.no_active"),
                    x + 9, currentY, CurseStyle.MUTED);
            currentY += 19;
        }
        for (HudEntry entry : entries) {
            int color = CurseStyle.color(entry.challenge);
            context.fill(x + 8, currentY, x + 10, currentY + 17, color);
            CurseStyle.icon(context, entry.id, x + 13, currentY);
            String difficulty = entry.challenge == null ? "" : entry.challenge.getDifficulty().getDisplayName();
            int difficultyWidth = difficulty.isEmpty() ? 0 : renderer.getWidth(difficulty) + 6;
            context.drawTextWithShadow(renderer, difficulty,
                    x + width - renderer.getWidth(difficulty) - 9, currentY, color);
            int lineY = CurseStyle.wrapped(context, renderer, Text.literal(entry.name),
                    x + 34, currentY, Math.max(24, textWidth - difficultyWidth), CurseStyle.TEXT);
            if (!entry.feedback.isEmpty()) {
                context.drawTextWithShadow(renderer, CurseStyle.trim(renderer, entry.feedback, textWidth),
                        x + 34, lineY, color);
                lineY += 11;
            }
            var lines = renderer.wrapLines(Text.literal(entry.desc), textWidth - 6);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                var line = lines.get(i);
                if (i == 1 && lines.size() > 2) {
                    line = net.minecraft.text.OrderedText.concat(line, Text.literal("…").asOrderedText());
                }
                context.drawTextWithShadow(renderer, line, x + 34, lineY, CurseStyle.MUTED);
                lineY += 11;
            }
            currentY += entry.height;
        }
        if (next) {
            context.fill(x + 9, currentY - 3, x + width - 9, currentY - 2, CurseStyle.EDGE);
            String names = ChallengeUtils.isDoubleChallenge(nextId)
                    ? String.join(" / ", ChallengeUtils.parseSubChallengeIds(nextId).stream().map(CurseStyle::name).toList())
                    : CurseStyle.name(nextId);
            if (names.isEmpty()) names = CurseStyle.name(nextId);
            context.drawTextWithShadow(renderer, Text.translatable("text.cursecycle.hud.next"),
                    x + 9, currentY + 2, CurseStyle.MUTED);
            context.drawTextWithShadow(renderer, CurseStyle.trim(renderer, names, width - 18),
                    x + 9, currentY + 13, CurseStyle.PURPLE);
        }
        context.disableScissor();
    }

    private record HudEntry(String id, Challenge challenge, String name, String desc, String feedback, int height) {}
}
