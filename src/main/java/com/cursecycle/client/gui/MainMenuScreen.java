package com.cursecycle.client.gui;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.client.ClientDataHolder;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class MainMenuScreen extends Screen {

    private int activeTab = 0; // 0 = Active, 1 = Next, 2 = History, 3 = Settings
    private Challenge selectedChallengeForModal = null;

    public MainMenuScreen() {
        super(Text.translatable("text.cursecycle.menu.title"));
    }

    @Override
    protected void init() {
        int buttonWidth = 80;
        int startX = (width - (4 * buttonWidth + 15)) / 2;
        int topY = 35;

        addDrawableChild(ButtonWidget.builder(Text.literal("Активные"), button -> activeTab = 0)
                .dimensions(startX, topY, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Следующее"), button -> activeTab = 1)
                .dimensions(startX + buttonWidth + 5, topY, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("История"), button -> activeTab = 2)
                .dimensions(startX + (buttonWidth + 5) * 2, topY, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> this.close())
                .dimensions(startX + (buttonWidth + 5) * 3, topY, buttonWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int panelW = 340;
        int panelH = 180;
        int panelX = (width - panelW) / 2;
        int panelY = 65;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xDD11111A);
        context.drawBorder(panelX, panelY, panelW, panelH, 0xFF444466);

        TextRenderer tr = this.textRenderer;
        context.drawCenteredTextWithShadow(tr, Text.literal("🔥 CURSE-CYCLE — ГЛАВНОЕ МЕНЮ 🔥").formatted(Formatting.GOLD, Formatting.BOLD), width / 2, 15, 0xFFFFAA00);

        switch (activeTab) {
            case 0 -> renderActiveTab(context, tr, panelX, panelY, panelW, mouseX, mouseY);
            case 1 -> renderNextTab(context, tr, panelX, panelY, panelW, mouseX, mouseY);
            case 2 -> renderHistoryTab(context, tr, panelX, panelY, panelW, mouseX, mouseY);
        }

        // Modal for detailed challenge rules
        if (selectedChallengeForModal != null) {
            renderChallengeModal(context, tr, selectedChallengeForModal);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderActiveTab(DrawContext context, TextRenderer tr, int px, int py, int pw, int mouseX, int mouseY) {
        context.drawTextWithShadow(tr, Text.literal("Текущие активные испытания:").formatted(Formatting.YELLOW, Formatting.BOLD), px + 15, py + 15, 0xFFFFFF);

        List<String> activeIds = ClientDataHolder.getActiveChallengeIds();
        if (activeIds.isEmpty()) {
            context.drawTextWithShadow(tr, Text.literal("В данный момент нет активных испытаний.").formatted(Formatting.GRAY), px + 15, py + 35, 0xAAAAAA);
            return;
        }

        int itemY = py + 35;
        for (String id : activeIds) {
            Challenge c = ChallengeRegistry.get(id);
            if (c != null) {
                int color = c.getDifficulty().getColor().getColorValue() != null ? c.getDifficulty().getColor().getColorValue() : 0xFFFFFF;
                boolean isHovered = mouseX >= px + 15 && mouseX <= px + pw - 30 && mouseY >= itemY && mouseY <= itemY + 22;

                context.fill(px + 15, itemY, px + pw - 15, itemY + 22, isHovered ? 0xFF2A2A3D : 0xFF1E1E2A);
                context.drawBorder(px + 15, itemY, pw - 30, 22, color | 0xFF000000);

                context.drawTextWithShadow(tr, Text.literal("⚡ " + c.getName()), px + 22, itemY + 3, color);
                context.drawTextWithShadow(tr, Text.literal("[" + c.getDifficulty().getDisplayName() + "]"), px + 180, itemY + 3, color);
                context.drawTextWithShadow(tr, Text.literal("🔍 Подробнее...").formatted(Formatting.UNDERLINE, Formatting.GRAY), px + pw - 100, itemY + 3, 0xAAAAAA);

                itemY += 26;
            }
        }
    }

    private void renderNextTab(DrawContext context, TextRenderer tr, int px, int py, int pw, int mouseX, int mouseY) {
        context.drawTextWithShadow(tr, Text.literal("Запланированное следующее испытание:").formatted(Formatting.YELLOW, Formatting.BOLD), px + 15, py + 15, 0xFFFFFF);

        String nextId = ClientDataHolder.getNextChallengeId();
        if (nextId == null || nextId.isEmpty()) {
            context.drawTextWithShadow(tr, Text.literal("Следующее испытание ещё не выбрано.").formatted(Formatting.GRAY), px + 15, py + 35, 0xAAAAAA);
            context.drawTextWithShadow(tr, Text.literal("Оно будет случайно выбрано колесом за 1 день до активации.").formatted(Formatting.DARK_GRAY), px + 15, py + 50, 0x888888);
            return;
        }

        Challenge c = ChallengeRegistry.get(nextId);
        if (c != null) {
            int color = c.getDifficulty().getColor().getColorValue() != null ? c.getDifficulty().getColor().getColorValue() : 0xFFFFFF;

            context.fill(px + 15, py + 35, px + pw - 15, py + 120, 0xFF1E1E2A);
            context.drawBorder(px + 15, py + 35, pw - 30, 85, color | 0xFF000000);

            context.drawTextWithShadow(tr, Text.literal("🎯 " + c.getName()).formatted(Formatting.BOLD), px + 25, py + 45, color);
            context.drawTextWithShadow(tr, Text.literal("Сложность: " + c.getDifficulty().getDisplayName()).formatted(c.getDifficulty().getColor()), px + 25, py + 60, color);
            context.drawTextWithShadow(tr, Text.literal("Описание: " + c.getShortDescription()), px + 25, py + 75, 0xCCCCCC);

            boolean isHovered = mouseX >= px + 25 && mouseX <= px + 150 && mouseY >= py + 95 && mouseY <= py + 112;
            context.fill(px + 25, py + 95, px + 150, py + 112, isHovered ? 0xFF333355 : 0xFF222233);
            context.drawTextWithShadow(tr, Text.literal("📖 Полные правила"), px + 35, py + 99, 0xFFFFAA00);
        }
    }

    private void renderHistoryTab(DrawContext context, TextRenderer tr, int px, int py, int pw, int mouseX, int mouseY) {
        context.drawTextWithShadow(tr, Text.literal("История выпавших испытаний:").formatted(Formatting.YELLOW, Formatting.BOLD), px + 15, py + 15, 0xFFFFFF);

        List<String> history = ClientDataHolder.getHistoryChallengeIds();
        if (history.isEmpty()) {
            context.drawTextWithShadow(tr, Text.literal("История пока пуста.").formatted(Formatting.GRAY), px + 15, py + 35, 0xAAAAAA);
            return;
        }

        int itemY = py + 35;
        // Display recent history items
        int count = 0;
        for (int i = history.size() - 1; i >= 0 && count < 5; i--, count++) {
            String id = history.get(i);
            Challenge c = ChallengeRegistry.get(id);
            String title = c != null ? c.getName() : id;
            String diff = c != null ? "[" + c.getDifficulty().getDisplayName() + "]" : "";
            int color = (c != null && c.getDifficulty().getColor().getColorValue() != null) ? c.getDifficulty().getColor().getColorValue() : 0xFFFFFF;

            context.drawTextWithShadow(tr, Text.literal("• " + title + " " + diff), px + 20, itemY, color);
            itemY += 16;
        }
    }

    private void renderChallengeModal(DrawContext context, TextRenderer tr, Challenge c) {
        int modalW = 280;
        int modalH = 150;
        int modalX = (width - modalW) / 2;
        int modalY = (height - modalH) / 2;

        context.fill(0, 0, width, height, 0xAA000000); // Back darkness
        context.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF181824);
        context.drawBorder(modalX, modalY, modalW, modalH, 0xFFFFAA00);

        int color = c.getDifficulty().getColor().getColorValue() != null ? c.getDifficulty().getColor().getColorValue() : 0xFFFFFF;
        context.drawTextWithShadow(tr, Text.literal(c.getName()).formatted(Formatting.BOLD), modalX + 15, modalY + 12, color);
        context.drawTextWithShadow(tr, Text.literal("Сложность: " + c.getDifficulty().getDisplayName()).formatted(c.getDifficulty().getColor()), modalX + 15, modalY + 26, color);

        context.fill(modalX + 15, modalY + 40, modalX + modalW - 15, modalY + 41, 0xFF555577);

        // Word wrap detailed rules text
        List<Text> wrappedRules = tr.wrapLines(Text.literal(c.getDetailedRules()), modalW - 30).stream()
                .map(Text::literal).toList();

        int lineY = modalY + 48;
        for (Text line : wrappedRules) {
            context.drawTextWithShadow(tr, line, modalX + 15, lineY, 0xDDDDDD);
            lineY += 11;
        }

        context.drawCenteredTextWithShadow(tr, Text.literal("(Нажмите в любом месте, чтобы закрыть)").formatted(Formatting.DARK_GRAY), width / 2, modalY + modalH - 16, 0x888888);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectedChallengeForModal != null) {
            selectedChallengeForModal = null;
            return true;
        }

        int panelW = 340;
        int panelX = (width - panelW) / 2;
        int panelY = 65;

        if (activeTab == 0) {
            List<String> activeIds = ClientDataHolder.getActiveChallengeIds();
            int itemY = panelY + 35;
            for (String id : activeIds) {
                Challenge c = ChallengeRegistry.get(id);
                if (c != null) {
                    if (mouseX >= panelX + 15 && mouseX <= panelX + panelW - 15 && mouseY >= itemY && mouseY <= itemY + 22) {
                        selectedChallengeForModal = c;
                        return true;
                    }
                    itemY += 26;
                }
            }
        } else if (activeTab == 1) {
            String nextId = ClientDataHolder.getNextChallengeId();
            if (nextId != null && !nextId.isEmpty()) {
                Challenge c = ChallengeRegistry.get(nextId);
                if (c != null && mouseX >= panelX + 25 && mouseX <= panelX + 150 && mouseY >= panelY + 95 && mouseY <= panelY + 112) {
                    selectedChallengeForModal = c;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
