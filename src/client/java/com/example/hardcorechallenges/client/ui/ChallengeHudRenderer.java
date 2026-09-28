package com.example.hardcorechallenges.client.ui;

import com.example.hardcorechallenges.client.network.ClientStateCache;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;

public class ChallengeHudRenderer implements HudRenderCallback {

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        List<String> active = ClientStateCache.activeChallenges;
        if (active.isEmpty()) return;

        TextRenderer textRenderer = client.textRenderer;
        int x = 10;
        int y = 10;

        drawContext.drawTextWithShadow(textRenderer, "§6[Hardcore Challenges]", x, y, 0xFFFFFF);
        y += 12;
        drawContext.drawTextWithShadow(textRenderer, "Game Mode: " + ClientStateCache.gameMode, x, y, 0xFFFFFF);
        y += 12;

        int passedDays = (int) (ClientStateCache.totalTicks / 24000L);
        drawContext.drawTextWithShadow(textRenderer, "Day: " + passedDays, x, y, 0xFFFFFF);
        y += 15;

        for (String challengeName : active) {
            drawContext.drawTextWithShadow(textRenderer, "§c- " + challengeName, x, y, 0xFFFFFF);
            y += 12;
        }
    }
}
