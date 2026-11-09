package com.cursecycle.client.gui;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.client.BlockedDirectionInput;
import com.cursecycle.client.ClientDataHolder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Existing four journal tabs, with a shared responsive frame and scrollable rules. */
public class MainMenuScreen extends Screen {
    private static final String[] TABS = {"active", "next", "history", "settings"};
    private int activeTab;
    private int historyPage;
    private int scroll;
    private int modalScroll;
    private Challenge modal;
    private int left, top, panelWidth, panelHeight, bodyTop, bodyBottom;
    private final List<Entry> entries = new ArrayList<>();
    private int contentHeight;
    private String stateSignature = "";

    public MainMenuScreen() {
        super(Text.translatable("text.cursecycle.menu.title"));
    }

    @Override protected void init() {
        panelWidth = Math.min(480, width - 20);
        panelHeight = Math.min(310, height - 20);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        bodyTop = top + 70;
        bodyBottom = top + panelHeight - 37;
        if (modal != null) {
            addDrawableChild(button(left + panelWidth - 80, top + panelHeight - 27, 68,
                    Text.translatable("gui.back"), b -> { modal = null; modalScroll = 0; clearAndInit(); }));
            return;
        }
        int tabWidth = (panelWidth - 24) / 4;
        for (int i = 0; i < TABS.length; i++) {
            final int tab = i;
            addDrawableChild(new CurseButton(left + 12 + i * tabWidth, top + 35, tabWidth - 3, 22,
                    Text.translatable("text.cursecycle.menu.tab." + TABS[i]), i == activeTab, b -> {
                activeTab = tab; scroll = 0; clearAndInit();
            }));
        }
        addDrawableChild(button(left + panelWidth - 80, top + panelHeight - 27, 68,
                Text.translatable("gui.done"), b -> close()));
        entries.clear();
        contentHeight = 0;
        if (activeTab == 0) {
            for (String id : ClientDataHolder.getActiveChallengeIds()) addEntry(id, true);
        } else if (activeTab == 1) {
            String next = ClientDataHolder.getNextChallengeId();
            if (next != null && !next.isEmpty()) {
                List<String> subs = ChallengeUtils.parseSubChallengeIds(next);
                if (subs.isEmpty()) addEntry(next, true);
                else for (String id : subs) addEntry(id, true);
            }
        } else if (activeTab == 2) {
            List<String> history = ClientDataHolder.getHistoryChallengeIds();
            int perPage = historyPerPage();
            int pages = Math.max(1, (history.size() + perPage - 1) / perPage);
            historyPage = Math.clamp(historyPage, 0, pages - 1);
            for (int i = history.size() - 1 - historyPage * perPage, n = 0; i >= 0 && n < perPage; i--, n++) {
                addEntry(history.get(i), false);
            }
            var older = addDrawableChild(button(left + 12, top + panelHeight - 27, 64,
                    Text.translatable("text.cursecycle.menu.older"), b -> { historyPage++; scroll = 0; clearAndInit(); }));
            older.active = historyPage + 1 < pages;
            var newer = addDrawableChild(button(left + 81, top + panelHeight - 27, 64,
                    Text.translatable("text.cursecycle.menu.newer"), b -> { historyPage--; scroll = 0; clearAndInit(); }));
            newer.active = historyPage > 0;
        } else {
            buildSettings();
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - (bodyBottom - bodyTop)));
    }

    private CurseButton button(int x, int y, int w, Text text, ButtonWidget.PressAction action) {
        return new CurseButton(x, y, w, 20, text, false, action);
    }

    private void addEntry(String rawId, boolean rules) {
        Challenge challenge = ChallengeRegistry.get(ChallengeUtils.getBaseChallengeId(rawId));
        String name = CurseStyle.name(rawId);
        List<String> subs = ChallengeUtils.parseSubChallengeIds(rawId);
        String desc = !subs.isEmpty() ? String.join(" / ", subs.stream().map(CurseStyle::name).toList())
                : challenge == null ? "" : challenge.getShortDescription();
        String feedback = BlockedDirectionInput.feedback(rawId);
        int available = panelWidth - 68;
        int nameLines = textRenderer.wrapLines(Text.literal(name), available - 62).size();
        int descLines = textRenderer.wrapLines(Text.literal(desc), available).size();
        int h = Math.max(36, nameLines * 11 + descLines * 11 + (feedback.isEmpty() ? 0 : 11) + 15);
        entries.add(new Entry(rawId, challenge, name, desc, feedback, rules, contentHeight, h));
        contentHeight += h + 6;
    }

    private int historyPerPage() {
        return Math.max(1, (bodyBottom - bodyTop) / 48);
    }

    private void buildSettings() {
        var config = CurseCycleMod.getConfig();
        int rowY = bodyTop;
        int controlsX = left + panelWidth - 101;
        addDrawableChild(button(controlsX, rowY, 88, switchText(config.enableHudOverlay), b -> {
            config.enableHudOverlay = !config.enableHudOverlay; config.save(); clearAndInit();
        }));
        rowY += 29;
        final int xY = rowY;
        addDrawableChild(button(controlsX, xY, 26, Text.literal("-"), b -> {
            config.hudPositionX = Math.max(0, config.hudPositionX - 10); config.save(); clearAndInit();
        }));
        addDrawableChild(button(controlsX + 62, xY, 26, Text.literal("+"), b -> {
            config.hudPositionX = Math.min(width - 30, config.hudPositionX + 10); config.save(); clearAndInit();
        }));
        rowY += 29;
        final int yY = rowY;
        addDrawableChild(button(controlsX, yY, 26, Text.literal("-"), b -> {
            config.hudPositionY = Math.max(0, config.hudPositionY - 10); config.save(); clearAndInit();
        }));
        addDrawableChild(button(controlsX + 62, yY, 26, Text.literal("+"), b -> {
            config.hudPositionY = Math.min(height - 30, config.hudPositionY + 10); config.save(); clearAndInit();
        }));
        rowY += 29;
        addDrawableChild(button(controlsX, rowY, 88, switchText(config.enableWheelAnimation), b -> {
            config.enableWheelAnimation = !config.enableWheelAnimation; config.save(); clearAndInit();
        }));
    }

    private Text switchText(boolean value) {
        return Text.translatable(value ? "options.on" : "options.off");
    }

    @Override public void tick() {
        String signature = ClientDataHolder.getActiveChallengeIds().toString()
                + ClientDataHolder.getNextChallengeId() + ClientDataHolder.getHistoryChallengeIds()
                + ClientDataHolder.getBlockedDirection();
        if (!stateSignature.equals(signature)) {
            stateSignature = signature;
            clearAndInit();
        }
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Painted explicitly before the panel; Screen.render calls this again before widgets.
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CurseStyle.backdrop(context, width, height);
        CurseStyle.panel(context, left, top, panelWidth, panelHeight);
        context.drawTextWithShadow(textRenderer, "CURSE-CYCLE", left + 13, top + 13, CurseStyle.BRONZE);
        Text label = Text.translatable(modal == null ? "text.cursecycle.menu.journal" : "text.cursecycle.menu.rules");
        context.drawTextWithShadow(textRenderer, label,
                left + panelWidth - 13 - textRenderer.getWidth(label), top + 13, CurseStyle.MUTED);
        if (modal != null) {
            renderModal(context);
        } else if (activeTab == 3) {
            renderSettings(context);
        } else {
            context.enableScissor(left + 10, bodyTop, left + panelWidth - 10, bodyBottom);
            if (entries.isEmpty()) {
                String key = activeTab == 0 ? "text.cursecycle.no_active"
                        : activeTab == 1 ? "text.cursecycle.menu.no_next" : "text.cursecycle.menu.no_history";
                CurseStyle.wrapped(context, textRenderer, Text.translatable(key),
                        left + 14, bodyTop + 8, panelWidth - 28, CurseStyle.MUTED);
            }
            for (Entry entry : entries) renderEntry(context, entry, mouseX, mouseY);
            context.disableScissor();
            drawScroll(context, scroll, contentHeight, bodyTop, bodyBottom);
            if (activeTab == 2) {
                int pages = Math.max(1, (ClientDataHolder.getHistoryChallengeIds().size() + historyPerPage() - 1) / historyPerPage());
                context.drawTextWithShadow(textRenderer, (historyPage + 1) + " / " + pages,
                        left + 156, top + panelHeight - 21, CurseStyle.MUTED);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderEntry(DrawContext context, Entry entry, int mouseX, int mouseY) {
        int x = left + 12, y = bodyTop + entry.offset - scroll, w = panelWidth - 24;
        if (y + entry.height < bodyTop || y > bodyBottom) return;
        boolean hovered = entry.rules && entry.challenge != null && mouseX >= x && mouseX < x + w
                && mouseY >= Math.max(y, bodyTop) && mouseY < Math.min(y + entry.height, bodyBottom);
        context.fill(x, y, x + w, y + entry.height, hovered ? 0xFF302936 : 0xFF201D26);
        context.fill(x, y, x + 2, y + entry.height, CurseStyle.color(entry.challenge));
        CurseStyle.icon(context, entry.rawId, x + 8, y + 8);
        int lineY = CurseStyle.wrapped(context, textRenderer, Text.literal(entry.name),
                x + 31, y + 7, w - 100, CurseStyle.TEXT);
        if (entry.challenge != null) {
            String difficulty = entry.challenge.getDifficulty().getDisplayName();
            context.drawTextWithShadow(textRenderer, difficulty, x + w - textRenderer.getWidth(difficulty) - 8,
                    y + 7, CurseStyle.color(entry.challenge));
        }
        if (!entry.feedback.isEmpty()) {
            lineY = CurseStyle.wrapped(context, textRenderer, Text.literal(entry.feedback),
                    x + 31, lineY + 1, w - 40, CurseStyle.PURPLE);
        }
        CurseStyle.wrapped(context, textRenderer, Text.literal(entry.desc),
                x + 31, lineY + 3, w - 40, CurseStyle.MUTED);
    }

    private void renderSettings(DrawContext context) {
        var config = CurseCycleMod.getConfig();
        String[] keys = {"hud", "hud_x", "hud_y", "wheel"};
        for (int i = 0; i < keys.length; i++) {
            context.drawTextWithShadow(textRenderer, Text.translatable("text.cursecycle.menu.setting." + keys[i]),
                    left + 14, bodyTop + 6 + i * 29, CurseStyle.TEXT);
        }
        int cx = left + panelWidth - 57;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(Integer.toString(config.hudPositionX)),
                cx, bodyTop + 35, CurseStyle.MUTED);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(Integer.toString(config.hudPositionY)),
                cx, bodyTop + 64, CurseStyle.MUTED);
    }

    private void renderModal(DrawContext context) {
        CurseStyle.icon(context, modal.getId(), left + 13, top + 35);
        int nameEnd = CurseStyle.wrapped(context, textRenderer, Text.literal(modal.getName()),
                left + 37, top + 35, panelWidth - 52, CurseStyle.TEXT);
        context.drawTextWithShadow(textRenderer, modal.getDifficulty().getDisplayName(),
                left + 37, nameEnd + 3, CurseStyle.color(modal));
        int start = nameEnd + 24, end = top + panelHeight - 38;
        String feedback = BlockedDirectionInput.feedback(modal.getId());
        Text rules = Text.literal(modal.getDetailedRules() + (feedback.isEmpty() ? "" : "\n" + feedback));
        List<OrderedText> lines = textRenderer.wrapLines(rules, panelWidth - 32);
        int available = Math.max(11, end - start);
        modalScroll = Math.clamp(modalScroll, 0, Math.max(0, lines.size() * 11 - available));
        context.enableScissor(left + 12, start, left + panelWidth - 12, end);
        int y = start - modalScroll;
        for (OrderedText line : lines) {
            context.drawTextWithShadow(textRenderer, line, left + 14, y, CurseStyle.MUTED);
            y += 11;
        }
        context.disableScissor();
        drawScroll(context, modalScroll, lines.size() * 11, start, end);
    }

    private void drawScroll(DrawContext context, int offset, int total, int start, int end) {
        int visible = end - start;
        if (total <= visible || visible <= 0) return;
        int h = Math.max(12, visible * visible / total);
        int y = start + (visible - h) * offset / Math.max(1, total - visible);
        context.fill(left + panelWidth - 7, start, left + panelWidth - 6, end, CurseStyle.EDGE);
        context.fill(left + panelWidth - 7, y, left + panelWidth - 6, y + h, CurseStyle.BRONZE);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modal == null && button == 0 && mouseY >= bodyTop && mouseY < bodyBottom) {
            for (Entry entry : entries) {
                int y = bodyTop + entry.offset - scroll;
                if (entry.rules && entry.challenge != null && mouseX >= left + 12 && mouseX < left + panelWidth - 12
                        && mouseY >= y && mouseY < y + entry.height) {
                    modal = entry.challenge; modalScroll = 0; clearAndInit(); return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (modal != null) modalScroll = Math.max(0, modalScroll - (int) (vertical * 22));
        else if (activeTab != 3) scroll = Math.clamp(scroll - (int) (vertical * 22), 0,
                Math.max(0, contentHeight - (bodyBottom - bodyTop)));
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && modal != null) {
            modal = null; modalScroll = 0; clearAndInit(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private record Entry(String rawId, Challenge challenge, String name, String desc, String feedback,
                         boolean rules, int offset, int height) {}
}
