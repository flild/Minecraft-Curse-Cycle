package com.example.hardcorechallenges.client.ui;

import com.example.hardcorechallenges.client.network.ClientStateCache;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ChallengeMenuScreen extends Screen {

    public ChallengeMenuScreen() {
        super(Text.literal("Hardcore Challenges Menu"));
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> {
            this.close();
        }).dimensions(centerX - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int x = 20;
        int y = 20;

        context.drawTextWithShadow(this.textRenderer, "§lHardcore Challenges", x, y, 0xFFFFFF);
        y += 20;
        context.drawTextWithShadow(this.textRenderer, "Game Mode: §e" + ClientStateCache.gameMode, x, y, 0xFFFFFF);
        y += 20;

        context.drawTextWithShadow(this.textRenderer, "§lActive Challenges:", x, y, 0xFFFFFF);
        y += 15;
        if (ClientStateCache.activeChallenges.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, "None", x, y, 0xAAAAAA);
        } else {
            for (String c : ClientStateCache.activeChallenges) {
                context.drawTextWithShadow(this.textRenderer, "- " + c, x, y, 0xFF5555);
                y += 15;
            }
        }
    }
}
