package com.cursecycle.client.gui;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.config.CurseCycleConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.List;

public class CurseCycleHudOverlay {

    public static void render(DrawContext context, CurseCycleConfig config, long day, long ticksUntilNext, List<String> activeIds, String nextId) {
        if (!config.enableHudOverlay) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        TextRenderer textRenderer = client.textRenderer;
        int x = config.hudPositionX;
        int y = config.hudPositionY;
        int padding = 6;
        int width = 180;

        // Calculate card height dynamically
        int activeCount = activeIds.isEmpty() ? 1 : activeIds.size();
        int height = 28 + (activeCount * 14) + (nextId.isEmpty() ? 0 : 16);

        // Draw translucent dark background card with accent border
        context.fill(x, y, x + width, y + height, 0xCC101018);
        context.drawBorder(x, y, width, height, 0xFF444466);

        // Header: Day count & Countdown
        String dayText = "День " + day;
        long totalSeconds = ticksUntilNext / 20;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        String timerText = String.format("%02d:%02d", minutes, seconds);

        context.drawTextWithShadow(textRenderer, Text.literal("⏱ " + dayText), x + padding, y + padding, 0xFFFFAA00);
        int timerWidth = textRenderer.getWidth(timerText);
        context.drawTextWithShadow(textRenderer, Text.literal(timerText), x + width - padding - timerWidth, y + padding, 0xFFAAAA00);

        // Divider
        int currentY = y + padding + 12;
        context.fill(x + padding, currentY, x + width - padding, currentY + 1, 0xFF555577);
        currentY += 5;

        // Active Challenges Section
        context.drawTextWithShadow(textRenderer, Text.literal("Активные испытания:"), x + padding, currentY, 0xFF8888AA);
        currentY += 12;

        if (activeIds.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.literal("  - Нет активных"), x + padding, currentY, 0xFF888888);
            currentY += 12;
        } else {
            for (String id : activeIds) {
                Challenge challenge = ChallengeRegistry.get(id);
                if (challenge != null) {
                    String name = "⚡ " + challenge.getName();
                    int color = challenge.getDifficulty().getColor().getColorValue() != null ? challenge.getDifficulty().getColor().getColorValue() : 0xFFFFFF;
                    context.drawTextWithShadow(textRenderer, Text.literal(name), x + padding, currentY, color);

                    String diffText = "[" + challenge.getDifficulty().getDisplayName() + "]";
                    int diffW = textRenderer.getWidth(diffText);
                    context.drawTextWithShadow(textRenderer, Text.literal(diffText), x + width - padding - diffW, currentY, color);
                    currentY += 11;

                    String desc = challenge.getShortDescription();
                    if (desc.length() > 28) desc = desc.substring(0, 26) + "..";
                    context.drawTextWithShadow(textRenderer, Text.literal("   " + desc), x + padding, currentY, 0xAAAAAA);
                    currentY += 13;
                } else {
                    context.drawTextWithShadow(textRenderer, Text.literal("⚡ " + id), x + padding, currentY, 0xFFFFFFFF);
                    currentY += 12;
                }
            }
        }

        // Next challenge preview if selected
        if (nextId != null && !nextId.isEmpty()) {
            String nextText = "След: ";
            if (nextId.startsWith("double_challenge:")) {
                String subPart = nextId.substring("double_challenge:".length());
                String[] subs = subPart.split(",");
                StringBuilder names = new StringBuilder("2х (");
                for (int i = 0; i < subs.length; i++) {
                    Challenge sc = ChallengeRegistry.get(subs[i]);
                    if (i > 0) names.append(" & ");
                    names.append(sc != null ? sc.getName() : subs[i]);
                }
                names.append(")");
                nextText += names.toString();
            } else {
                Challenge nextChallenge = ChallengeRegistry.get(nextId);
                nextText += (nextChallenge != null ? nextChallenge.getName() : nextId);
            }
            context.drawTextWithShadow(textRenderer, Text.literal(nextText), x + padding, currentY, 0xFF55FF55);
        }
    }
}
