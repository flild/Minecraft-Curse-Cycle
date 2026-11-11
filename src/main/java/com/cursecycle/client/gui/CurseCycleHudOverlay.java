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

    public static void render(DrawContext context, CurseCycleConfig config, long ticksUntilNext,
                              List<String> activeIds, String nextId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!config.enableHudOverlay || client.options.hudHidden) return;
        var renderer = client.textRenderer;
        int width = Math.min(202, context.getScaledWindowWidth() - 12);
        if (width < 80) return;
        int textWidth = width - 38;
        List<HudEntry> entries = new ArrayList<>();
        int height = 25;
        for (String id : activeIds) {
            Challenge challenge = ChallengeRegistry.get(id);
            String name = challenge == null ? id : challenge.getName();
            String desc = challenge == null ? "" : challenge.getShortDescription();
            String feedback = BlockedDirectionInput.feedback(id);
            int difficultyWidth = challenge == null ? 0 : renderer.getWidth(challenge.getDifficulty().getDisplayName()) + 6;
            int nameHeight = renderer.wrapLines(Text.literal(name), Math.max(24, textWidth - difficultyWidth)).size() * 11;
            int descHeight = Math.min(2, renderer.wrapLines(Text.literal(desc), textWidth - 6).size()) * 11;
            int rowHeight = Math.max(20, nameHeight + descHeight + (feedback.isEmpty() ? 0 : 11)) + 5;
            entries.add(new HudEntry(id, challenge, name, desc, feedback, rowHeight));
            height += rowHeight;
        }
        if (entries.isEmpty()) height += 17;
        boolean next = nextId != null && !nextId.isEmpty();
        if (next) height += 23;
        int x = Math.clamp(config.hudPositionX, 0, Math.max(0, context.getScaledWindowWidth() - width - 6));
        int y = Math.clamp(config.hudPositionY, 0, Math.max(0, context.getScaledWindowHeight() - height - 6));
        int visibleHeight = Math.min(height, context.getScaledWindowHeight() - y - 6);
        CurseStyle.panel(context, x, y, width, visibleHeight);
        context.enableScissor(x + 1, y + 1, x + width - 1, y + visibleHeight - 1);
        context.drawTextWithShadow(renderer, Text.translatable("text.cursecycle.hud.countdown"),
                x + 7, y + 7, CurseStyle.BRONZE);
        long days = ChallengeCountdown.daysRemaining(ticksUntilNext);
        Text timer = Text.translatable(ChallengeCountdown.daysKey(days, client.getLanguageManager().getLanguage()), days);
        context.drawTextWithShadow(renderer, timer, x + width - renderer.getWidth(timer) - 7, y + 7,
                days <= 1 ? CurseStyle.PURPLE : CurseStyle.TEXT);
        context.fill(x + 7, y + 20, x + width - 7, y + 21, CurseStyle.EDGE);
        int currentY = y + 26;
        if (entries.isEmpty()) {
            context.drawTextWithShadow(renderer, Text.translatable("text.cursecycle.no_active"),
                    x + 7, currentY, CurseStyle.MUTED);
            currentY += 17;
        }
        for (HudEntry entry : entries) {
            int color = CurseStyle.color(entry.challenge);
            context.fill(x + 6, currentY, x + 8, currentY + 17, color);
            CurseStyle.icon(context, entry.id, x + 10, currentY);
            String difficulty = entry.challenge == null ? "" : entry.challenge.getDifficulty().getDisplayName();
            int difficultyWidth = difficulty.isEmpty() ? 0 : renderer.getWidth(difficulty) + 6;
            context.drawTextWithShadow(renderer, difficulty,
                    x + width - renderer.getWidth(difficulty) - 7, currentY, color);
            int lineY = CurseStyle.wrapped(context, renderer, Text.literal(entry.name),
                    x + 30, currentY, Math.max(24, textWidth - difficultyWidth), CurseStyle.TEXT);
            if (!entry.feedback.isEmpty()) {
                context.drawTextWithShadow(renderer, CurseStyle.trim(renderer, entry.feedback, textWidth),
                        x + 30, lineY, color);
                lineY += 11;
            }
            var lines = renderer.wrapLines(Text.literal(entry.desc), textWidth - 6);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                var line = lines.get(i);
                if (i == 1 && lines.size() > 2) {
                    line = net.minecraft.text.OrderedText.concat(line, Text.literal("…").asOrderedText());
                }
                context.drawTextWithShadow(renderer, line, x + 30, lineY, CurseStyle.MUTED);
                lineY += 11;
            }
            currentY += entry.height;
        }
        if (next) {
            context.fill(x + 7, currentY - 3, x + width - 7, currentY - 2, CurseStyle.EDGE);
            String names = ChallengeUtils.isDoubleChallenge(nextId)
                    ? String.join(" / ", ChallengeUtils.parseSubChallengeIds(nextId).stream().map(CurseStyle::name).toList())
                    : CurseStyle.name(nextId);
            if (names.isEmpty()) names = CurseStyle.name(nextId);
            context.drawTextWithShadow(renderer, Text.translatable("text.cursecycle.hud.next"),
                    x + 7, currentY, CurseStyle.MUTED);
            context.drawTextWithShadow(renderer, CurseStyle.trim(renderer, names, width - 14),
                    x + 7, currentY + 11, CurseStyle.PURPLE);
        }
        context.disableScissor();
    }

    private record HudEntry(String id, Challenge challenge, String name, String desc, String feedback, int height) {}
}
