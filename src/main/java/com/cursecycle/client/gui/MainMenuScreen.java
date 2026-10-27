package com.cursecycle.client.gui;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.BlockedDirectionInput;
import com.cursecycle.config.CurseCycleConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class MainMenuScreen extends Screen {

    private int activeTab = 0; // 0 = Active, 1 = Next, 2 = History, 3 = Settings
    private Challenge selectedChallengeForModal = null;
    private int historyPage = 0;
    private static final int HISTORY_PER_PAGE = 6;

    public MainMenuScreen() {
        super(Text.translatable("text.cursecycle.menu.title"));
    }

    @Override
    protected void init() {
        int buttonWidth = 65;
        int startX = (width - (5 * buttonWidth + 20)) / 2;
        int topY = 35;

        addDrawableChild(ButtonWidget.builder(Text.literal("Активные"), button -> activeTab = 0)
                .dimensions(startX, topY, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Следующее"), button -> activeTab = 1)
                .dimensions(startX + buttonWidth + 5, topY, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("История"), button -> activeTab = 2)
                .dimensions(startX + (buttonWidth + 5) * 2, topY, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Настройки"), button -> activeTab = 3)
                .dimensions(startX + (buttonWidth + 5) * 3, topY, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> this.close())
                .dimensions(startX + (buttonWidth + 5) * 4, topY, buttonWidth, 20).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render semi-transparent background overlay without applying post-processing blur
        context.fill(0, 0, this.width, this.height, 0x88000000);
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
            case 3 -> renderSettingsTab(context, tr, panelX, panelY, panelW, mouseX, mouseY);
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
                boolean isHovered = mouseX >= px + 15 && mouseX <= px + pw - 15 && mouseY >= itemY && mouseY <= itemY + 22;

                context.fill(px + 15, itemY, px + pw - 15, itemY + 22, isHovered ? 0xFF2A2A3D : 0xFF1E1E2A);
                context.drawBorder(px + 15, itemY, pw - 30, 22, color | 0xFF000000);

                String name = "⚡ " + c.getName();
                if (tr.getWidth(name) > 150) {
                    name = tr.trimToWidth(name, 144) + "…";
                }
                context.drawTextWithShadow(tr, Text.literal(name), px + 22, itemY + 3, color);
                context.drawTextWithShadow(tr, Text.literal("[" + c.getDifficulty().getDisplayName() + "]"), px + 180, itemY + 3, color);
                context.drawTextWithShadow(tr, Text.literal("🔍 Правила").formatted(Formatting.UNDERLINE, Formatting.GRAY), px + pw - 80, itemY + 3, 0xAAAAAA);

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

        if (nextId.startsWith("double_challenge:")) {
            context.drawTextWithShadow(tr, Text.literal("🎯 Специальное: Два испытания одновременно!").formatted(Formatting.AQUA, Formatting.BOLD), px + 15, py + 35, 0x55FFFF);
            String[] subIds = nextId.substring("double_challenge:".length()).split(",");
            int itemY = py + 52;
            for (String subId : subIds) {
                Challenge c = ChallengeRegistry.get(subId);
                if (c != null) {
                    int color = c.getDifficulty().getColor().getColorValue() != null ? c.getDifficulty().getColor().getColorValue() : 0xFFFFFF;
                    context.fill(px + 15, itemY, px + pw - 15, itemY + 22, 0xFF1E1E2A);
                    context.drawBorder(px + 15, itemY, pw - 30, 22, color | 0xFF000000);

                    String name = " • " + c.getName();
                    if (tr.getWidth(name) > 150) {
                        name = tr.trimToWidth(name, 144) + "…";
                    }
                    context.drawTextWithShadow(tr, Text.literal(name), px + 22, itemY + 3, color);
                    context.drawTextWithShadow(tr, Text.literal("[" + c.getDifficulty().getDisplayName() + "]"), px + 180, itemY + 3, color);
                    itemY += 26;
                }
            }
            return;
        }

        Challenge c = ChallengeRegistry.get(nextId);
        if (c != null) {
            int color = c.getDifficulty().getColor().getColorValue() != null ? c.getDifficulty().getColor().getColorValue() : 0xFFFFFF;

            List<OrderedText> descLines = tr.wrapLines(Text.literal("Описание: " + c.getShortDescription()), pw - 50);

            int startY = py + 35;
            int descStartY = py + 75;
            int descEndY = descStartY + (descLines.size() * 12);
            int btnY = descEndY + 6;
            int cardH = (btnY + 20) - startY;

            context.fill(px + 15, startY, px + pw - 15, startY + cardH, 0xFF1E1E2A);
            context.drawBorder(px + 15, startY, pw - 30, cardH, color | 0xFF000000);

            String name = "🎯 " + c.getName();
            if (tr.getWidth(name) > pw - 50) {
                name = tr.trimToWidth(name, pw - 56) + "…";
            }
            context.drawTextWithShadow(tr, Text.literal(name).formatted(Formatting.BOLD), px + 25, py + 45, color);
            context.drawTextWithShadow(tr, Text.literal("Сложность: " + c.getDifficulty().getDisplayName()).formatted(c.getDifficulty().getColor()), px + 25, py + 60, color);

            int lineY = descStartY;
            for (OrderedText line : descLines) {
                context.drawTextWithShadow(tr, line, px + 25, lineY, 0xCCCCCC);
                lineY += 12;
            }

            boolean isHovered = mouseX >= px + 25 && mouseX <= px + 150 && mouseY >= btnY && mouseY <= btnY + 17;
            context.fill(px + 25, btnY, px + 150, btnY + 17, isHovered ? 0xFF333355 : 0xFF222233);
            context.drawTextWithShadow(tr, Text.literal("📖 Полные правила"), px + 35, btnY + 4, 0xFFFFAA00);
        }
    }

    private void renderHistoryTab(DrawContext context, TextRenderer tr, int px, int py, int pw, int mouseX, int mouseY) {
        context.drawTextWithShadow(tr, Text.literal("История выпавших испытаний:").formatted(Formatting.YELLOW, Formatting.BOLD), px + 15, py + 15, 0xFFFFFF);

        List<String> history = ClientDataHolder.getHistoryChallengeIds();
        if (history.isEmpty()) {
            context.drawTextWithShadow(tr, Text.literal("История пока пуста.").formatted(Formatting.GRAY), px + 15, py + 35, 0xAAAAAA);
            return;
        }

        int totalItems = history.size();
        int totalPages = Math.max(1, (totalItems + HISTORY_PER_PAGE - 1) / HISTORY_PER_PAGE);
        if (historyPage >= totalPages) historyPage = totalPages - 1;
        if (historyPage < 0) historyPage = 0;

        int startIndex = totalItems - 1 - (historyPage * HISTORY_PER_PAGE);
        int itemY = py + 35;

        for (int i = 0; i < HISTORY_PER_PAGE && (startIndex - i) >= 0; i++) {
            String rawId = history.get(startIndex - i);
            String title;
            int color = 0xFFFFFF;

            if (rawId.startsWith("double_challenge:")) {
                String[] subs = rawId.substring("double_challenge:".length()).split(",");
                StringBuilder sb = new StringBuilder("2х (");
                for (int s = 0; s < subs.length; s++) {
                    Challenge sc = ChallengeRegistry.get(subs[s]);
                    if (s > 0) sb.append(" & ");
                    sb.append(sc != null ? sc.getName() : subs[s]);
                }
                sb.append(")");
                title = sb.toString();
                color = 0x55FFFF;
            } else {
                Challenge c = ChallengeRegistry.get(rawId);
                title = c != null ? c.getName() + " [" + c.getDifficulty().getDisplayName() + "]" : rawId;
                if (c != null && c.getDifficulty().getColor().getColorValue() != null) {
                    color = c.getDifficulty().getColor().getColorValue();
                }
            }

            String fullItem = "• " + title;
            int maxItemW = pw - 40;
            if (tr.getWidth(fullItem) > maxItemW) {
                fullItem = tr.trimToWidth(fullItem, maxItemW - 6) + "…";
            }

            context.drawTextWithShadow(tr, Text.literal(fullItem), px + 20, itemY, color);
            itemY += 16;
        }

        // Pagination controls
        String pageStr = (historyPage + 1) + " / " + totalPages;
        context.drawTextWithShadow(tr, Text.literal("Стр. " + pageStr), px + pw - 90, py + 155, 0xAAAAAA);

        boolean prevHover = mouseX >= px + 15 && mouseX <= px + 65 && mouseY >= py + 152 && mouseY <= py + 168;
        context.fill(px + 15, py + 152, px + 65, py + 168, prevHover ? 0xFF333355 : 0xFF222233);
        context.drawTextWithShadow(tr, Text.literal("◄ Назад"), px + 20, py + 155, historyPage < totalPages - 1 ? 0xFFFFFF : 0x666666);

        boolean nextHover = mouseX >= px + 75 && mouseX <= px + 135 && mouseY >= py + 152 && mouseY <= py + 168;
        context.fill(px + 75, py + 152, px + 135, py + 168, nextHover ? 0xFF333355 : 0xFF222233);
        context.drawTextWithShadow(tr, Text.literal("Вперёд ►"), px + 80, py + 155, historyPage > 0 ? 0xFFFFFF : 0x666666);
    }

    private void renderSettingsTab(DrawContext context, TextRenderer tr, int px, int py, int pw, int mouseX, int mouseY) {
        context.drawTextWithShadow(tr, Text.literal("Настройки Curse-Cycle:").formatted(Formatting.YELLOW, Formatting.BOLD), px + 15, py + 15, 0xFFFFFF);

        CurseCycleConfig cfg = CurseCycleMod.getConfig();

        // Toggle HUD Overlay
        boolean hudHover = mouseX >= px + 15 && mouseX <= px + pw - 15 && mouseY >= py + 35 && mouseY <= py + 55;
        context.fill(px + 15, py + 35, px + pw - 15, py + 55, hudHover ? 0xFF2A2A3D : 0xFF1E1E2A);
        context.drawTextWithShadow(tr, Text.literal("Отображение HUD Оверлея:"), px + 25, py + 40, 0xFFFFFF);
        String hudStatus = cfg.enableHudOverlay ? "ВКЛ" : "ВЫКЛ";
        int hudColor = cfg.enableHudOverlay ? 0x55FF55 : 0xFF5555;
        context.drawTextWithShadow(tr, Text.literal(hudStatus), px + pw - 60, py + 40, hudColor);

        // Adjust HUD Position X
        context.drawTextWithShadow(tr, Text.literal("Позиция HUD X: " + cfg.hudPositionX), px + 25, py + 65, 0xCCCCCC);
        boolean xMinus = mouseX >= px + 180 && mouseX <= px + 210 && mouseY >= py + 62 && mouseY <= py + 77;
        context.fill(px + 180, py + 62, px + 210, py + 77, xMinus ? 0xFF444466 : 0xFF222233);
        context.drawTextWithShadow(tr, Text.literal(" -10 "), px + 183, py + 65, 0xFFFFFF);

        boolean xPlus = mouseX >= px + 215 && mouseX <= px + 245 && mouseY >= py + 62 && mouseY <= py + 77;
        context.fill(px + 215, py + 62, px + 245, py + 77, xPlus ? 0xFF444466 : 0xFF222233);
        context.drawTextWithShadow(tr, Text.literal(" +10 "), px + 218, py + 65, 0xFFFFFF);

        // Adjust HUD Position Y
        context.drawTextWithShadow(tr, Text.literal("Позиция HUD Y: " + cfg.hudPositionY), px + 25, py + 85, 0xCCCCCC);
        boolean yMinus = mouseX >= px + 180 && mouseX <= px + 210 && mouseY >= py + 82 && mouseY <= py + 97;
        context.fill(px + 180, py + 82, px + 210, py + 97, yMinus ? 0xFF444466 : 0xFF222233);
        context.drawTextWithShadow(tr, Text.literal(" -10 "), px + 183, py + 85, 0xFFFFFF);

        boolean yPlus = mouseX >= px + 215 && mouseX <= px + 245 && mouseY >= py + 82 && mouseY <= py + 97;
        context.fill(px + 215, py + 82, px + 245, py + 97, yPlus ? 0xFF444466 : 0xFF222233);
        context.drawTextWithShadow(tr, Text.literal(" +10 "), px + 218, py + 85, 0xFFFFFF);

        // Toggle Sound Effects
        boolean soundHover = mouseX >= px + 15 && mouseX <= px + pw - 15 && mouseY >= py + 105 && mouseY <= py + 125;
        context.fill(px + 15, py + 105, px + pw - 15, py + 125, soundHover ? 0xFF2A2A3D : 0xFF1E1E2A);
        context.drawTextWithShadow(tr, Text.literal("Анимация и звуки Колеса:"), px + 25, py + 110, 0xFFFFFF);
        String soundStatus = cfg.enableWheelAnimation ? "ВКЛ" : "ВЫКЛ";
        int soundColor = cfg.enableWheelAnimation ? 0x55FF55 : 0xFF5555;
        context.drawTextWithShadow(tr, Text.literal(soundStatus), px + pw - 60, py + 110, soundColor);
    }

    private void renderChallengeModal(DrawContext context, TextRenderer tr, Challenge c) {
        int modalW = 280;
        String feedback = BlockedDirectionInput.feedback(c.getId());
        String rules = c.getDetailedRules() + (feedback.isEmpty() ? "" : "\n" + feedback);
        List<OrderedText> wrappedLines = tr.wrapLines(Text.literal(rules), modalW - 30);
        int modalH = Math.max(130, 48 + (wrappedLines.size() * 12) + 28);
        int modalX = (width - modalW) / 2;
        int modalY = (height - modalH) / 2;

        context.fill(0, 0, width, height, 0xAA000000);
        context.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF181824);
        context.drawBorder(modalX, modalY, modalW, modalH, 0xFFFFAA00);

        int color = c.getDifficulty().getColor().getColorValue() != null ? c.getDifficulty().getColor().getColorValue() : 0xFFFFFF;
        context.drawTextWithShadow(tr, Text.literal(c.getName()).formatted(Formatting.BOLD), modalX + 15, modalY + 12, color);
        context.drawTextWithShadow(tr, Text.literal("Сложность: " + c.getDifficulty().getDisplayName()).formatted(c.getDifficulty().getColor()), modalX + 15, modalY + 26, color);

        context.fill(modalX + 15, modalY + 40, modalX + modalW - 15, modalY + 41, 0xFF555577);

        int lineY = modalY + 48;
        for (OrderedText line : wrappedLines) {
            context.drawTextWithShadow(tr, line, modalX + 15, lineY, 0xDDDDDD);
            lineY += 12;
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
                if (!nextId.startsWith("double_challenge:")) {
                    Challenge c = ChallengeRegistry.get(nextId);
                    if (c != null) {
                        TextRenderer tr = this.textRenderer;
                        List<OrderedText> descLines = tr.wrapLines(Text.literal("Описание: " + c.getShortDescription()), panelW - 50);
                        int btnY = panelY + 75 + (descLines.size() * 12) + 6;
                        if (mouseX >= panelX + 25 && mouseX <= panelX + 150 && mouseY >= btnY && mouseY <= btnY + 17) {
                            selectedChallengeForModal = c;
                            return true;
                        }
                    }
                }
            }
        } else if (activeTab == 2) {
            List<String> history = ClientDataHolder.getHistoryChallengeIds();
            int totalItems = history.size();
            int totalPages = Math.max(1, (totalItems + HISTORY_PER_PAGE - 1) / HISTORY_PER_PAGE);

            // Prev button
            if (mouseX >= panelX + 15 && mouseX <= panelX + 65 && mouseY >= panelY + 152 && mouseY <= panelY + 168) {
                if (historyPage < totalPages - 1) historyPage++;
                return true;
            }
            // Next button
            if (mouseX >= panelX + 75 && mouseX <= panelX + 135 && mouseY >= panelY + 152 && mouseY <= panelY + 168) {
                if (historyPage > 0) historyPage--;
                return true;
            }
        } else if (activeTab == 3) {
            CurseCycleConfig cfg = CurseCycleMod.getConfig();
            // Toggle HUD
            if (mouseX >= panelX + 15 && mouseX <= panelX + panelW - 15 && mouseY >= panelY + 35 && mouseY <= panelY + 55) {
                cfg.enableHudOverlay = !cfg.enableHudOverlay;
                cfg.save();
                return true;
            }
            // HUD X -10
            if (mouseX >= panelX + 180 && mouseX <= panelX + 210 && mouseY >= panelY + 62 && mouseY <= panelY + 77) {
                cfg.hudPositionX = Math.max(0, cfg.hudPositionX - 10);
                cfg.save();
                return true;
            }
            // HUD X +10
            if (mouseX >= panelX + 215 && mouseX <= panelX + 245 && mouseY >= panelY + 62 && mouseY <= panelY + 77) {
                cfg.hudPositionX = Math.min(width - 50, cfg.hudPositionX + 10);
                cfg.save();
                return true;
            }
            // HUD Y -10
            if (mouseX >= panelX + 180 && mouseX <= panelX + 210 && mouseY >= panelY + 82 && mouseY <= panelY + 97) {
                cfg.hudPositionY = Math.max(0, cfg.hudPositionY - 10);
                cfg.save();
                return true;
            }
            // HUD Y +10
            if (mouseX >= panelX + 215 && mouseX <= panelX + 245 && mouseY >= panelY + 82 && mouseY <= panelY + 97) {
                cfg.hudPositionY = Math.min(height - 30, cfg.hudPositionY + 10);
                cfg.save();
                return true;
            }
            // Toggle Sound
            if (mouseX >= panelX + 15 && mouseX <= panelX + panelW - 15 && mouseY >= panelY + 105 && mouseY <= panelY + 125) {
                cfg.enableWheelAnimation = !cfg.enableWheelAnimation;
                cfg.save();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
