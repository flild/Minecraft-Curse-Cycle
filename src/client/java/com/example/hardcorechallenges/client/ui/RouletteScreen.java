package com.example.hardcorechallenges.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

public class RouletteScreen extends Screen {
    private final List<String> winners;
    private final List<String> candidates;
    private int ticksOpen = 0;
    private final int MAX_TICKS = 100; // 5 секунд анимации

    public RouletteScreen(List<String> winners, List<String> candidates) {
        super(Text.literal("Challenge Roulette"));
        this.winners = winners;
        this.candidates = candidates;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void tick() {
        super.tick();
        ticksOpen++;
        if (ticksOpen >= MAX_TICKS) {
            this.close();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, "§6Rolling Challenges...", centerX, 20, 0xFFFFFF);

        if (ticksOpen < MAX_TICKS - 40) {
            // Анимация (показываем случайных кандидатов)
            int index = (ticksOpen / 5) % Math.max(1, candidates.size());
            String current = candidates.isEmpty() ? "Unknown" : candidates.get(index);
            context.drawCenteredTextWithShadow(this.textRenderer, "§e" + current, centerX, centerY, 0xFFFFFF);
        } else {
            // Показываем победителей
            context.drawCenteredTextWithShadow(this.textRenderer, "§aWINNERS:", centerX, centerY - 20, 0xFFFFFF);
            int yOffset = 0;
            for (String winner : winners) {
                context.drawCenteredTextWithShadow(this.textRenderer, "§c" + winner, centerX, centerY + yOffset, 0xFFFFFF);
                yOffset += 15;
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
