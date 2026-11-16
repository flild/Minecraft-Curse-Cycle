package com.cursecycle.client.gui;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.client.BlockedDirectionInput;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.ClientTimingSettings;
import com.cursecycle.config.TimingSettings;
import com.cursecycle.network.TimingSettingsPayload;
import com.cursecycle.network.TimingSettingsRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** The H-key journal also explains the mod and edits the server's timing settings. */
public class MainMenuScreen extends Screen {
    private static final int HOME = 0, ACTIVE = 1, NEXT = 2, HISTORY = 3, COMMANDS = 4, SETTINGS = 5;
    private static final String[] TABS = {"home", "active", "next", "history", "commands", "settings"};
    private int activeTab;
    private int historyPage;
    private int scroll;
    private int modalScroll;
    private Challenge modal;
    private int left, top, panelWidth, panelHeight, bodyTop, bodyBottom;
    private final List<Entry> entries = new ArrayList<>();
    private int contentHeight;
    private String stateSignature = "";
    private final List<Paragraph> paragraphs = new ArrayList<>();
    private final List<SettingControl> settingControls = new ArrayList<>();
    private String[] timingDraft;
    private TimingSettings baseline;
    private long settingsRevision = -1;
    private boolean saving;
    private int saveTicks;
    private String saveStatus = "";
    private CurseButton applyButton;

    public MainMenuScreen() {
        super(Text.translatable("text.cursecycle.menu.title"));
    }

    @Override protected void init() {
        panelWidth = Math.min(480, width - 20);
        panelHeight = Math.min(370, height - 20);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int columns = panelWidth >= 400 ? 6 : 3;
        bodyTop = top + (columns == 6 ? 65 : 92);
        bodyBottom = top + panelHeight - 37;
        if (modal != null) {
            addDrawableChild(button(left + panelWidth - 80, top + panelHeight - 27, 68,
                    Text.translatable("gui.back"), b -> { modal = null; modalScroll = 0; clearAndInit(); }));
            return;
        }
        int tabWidth = (panelWidth - 24) / columns;
        for (int i = 0; i < TABS.length; i++) {
            final int tab = i;
            addDrawableChild(new CurseButton(left + 12 + (i % columns) * tabWidth, top + 35 + (i / columns) * 25, tabWidth - 3, 22,
                    Text.translatable("text.cursecycle.menu.tab." + TABS[i]), i == activeTab, b -> {
                activeTab = tab; scroll = 0; clearAndInit();
            }));
        }
        addDrawableChild(button(left + panelWidth - 80, top + panelHeight - 27, 68,
                Text.translatable("gui.done"), b -> close()));
        entries.clear();
        paragraphs.clear();
        settingControls.clear();
        applyButton = null;
        contentHeight = 0;
        if (activeTab == HOME) {
            buildHome();
        } else if (activeTab == COMMANDS) {
            buildCommands();
        } else if (activeTab == ACTIVE) {
            for (String id : ClientDataHolder.getActiveChallengeIds()) addEntry(id, true);
        } else if (activeTab == NEXT) {
            String next = ClientDataHolder.getNextChallengeId();
            if (next != null && !next.isEmpty()) {
                List<String> subs = ChallengeUtils.parseSubChallengeIds(next);
                if (subs.isEmpty()) addEntry(next, true);
                else for (String id : subs) addEntry(id, true);
            }
        } else if (activeTab == HISTORY) {
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
        positionSettingControls();
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

    private void paragraph(String key, int color, Object... args) {
        paragraph(Text.translatable("text.cursecycle.menu." + key, args), color);
    }

    private void paragraph(Text text, int color) {
        List<OrderedText> lines = textRenderer.wrapLines(text, panelWidth - 36);
        paragraphs.add(new Paragraph(lines, contentHeight, color));
        contentHeight += lines.size() * 11 + 9;
    }

    private void buildHome() {
        paragraph("home.about_title", CurseStyle.BRONZE);
        paragraph("home.about", CurseStyle.TEXT);
        paragraph("home.rules_title", CurseStyle.BRONZE);
        paragraph("home.rules", CurseStyle.TEXT);
        var snapshot = ClientTimingSettings.get();
        if (snapshot != null)
            paragraph("home.timing", CurseStyle.MUTED, snapshot.preparationSeconds(), snapshot.intervalDays(), snapshot.noticeDays());
        paragraph("home.use_title", CurseStyle.BRONZE);
        paragraph("home.use", CurseStyle.TEXT,
                com.cursecycle.CurseCycleClient.openMenuKeyBinding.getBoundKeyLocalizedText());
        paragraph("home.settings", CurseStyle.TEXT);
    }

    private void buildCommands() {
        paragraph("commands.read_title", CurseStyle.BRONZE);
        for (String command : List.of("active", "next", "history", "list"))
            paragraph("commands." + command, CurseStyle.TEXT);
        paragraph("commands.admin_title", CurseStyle.BRONZE);
        paragraph("commands.permissions", CurseStyle.MUTED);
        for (String command : List.of("trigger_spin", "activate_next", "activate", "setday", "reset"))
            paragraph("commands." + command, CurseStyle.TEXT);
        paragraph("commands.alias", CurseStyle.MUTED);
    }

    private void loadTimingDraft() {
        var snapshot = ClientTimingSettings.get();
        if (snapshot == null) return;
        baseline = snapshot.settings();
        timingDraft = new String[]{Integer.toString(baseline.intervalDays()), Integer.toString(baseline.noticeDays()),
                Integer.toString(baseline.preparationSeconds())};
    }

    private TimingSettings parsedDraft() {
        if (timingDraft == null) return null;
        try {
            var settings = new TimingSettings(Integer.parseInt(timingDraft[0]), Integer.parseInt(timingDraft[1]),
                    Integer.parseInt(timingDraft[2]));
            return settings.isValid() ? settings : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    private void buildSettings() {
        if (timingDraft == null) loadTimingDraft();
        paragraph("setting.timing", CurseStyle.BRONZE);
        var snapshot = ClientTimingSettings.get();
        String access = snapshot == null
                ? supportsSettings() ? "loading" : "unavailable"
                : snapshot.editable() ? "scope" : "readonly";
        paragraph("setting." + access, CurseStyle.MUTED);
        String[] labels = {"interval", "notice", "preparation"};
        int controlsX = left + panelWidth - 112;
        for (int i = 0; i < labels.length; i++) {
            int row = contentHeight;
            var lines = textRenderer.wrapLines(Text.translatable("text.cursecycle.menu.setting." + labels[i]), panelWidth - 146);
            paragraphs.add(new Paragraph(lines, row + 5, CurseStyle.TEXT));
            int index = i;
            var field = new TextFieldWidget(textRenderer, controlsX, bodyTop + row, 96, 20,
                    Text.translatable("text.cursecycle.menu.setting." + labels[i]));
            field.setMaxLength(10);
            field.setText(timingDraft == null ? "" : timingDraft[i]);
            field.setTextPredicate(value -> value.chars().allMatch(Character::isDigit));
            field.setChangedListener(value -> { timingDraft[index] = value; saveStatus = ""; updateApplyButton(); });
            field.setEditable(snapshot != null && snapshot.editable() && !saving);
            addSettingControl(field, row);
            contentHeight += Math.max(25, lines.size() * 11 + 10);
        }
        paragraph("setting.validation", CurseStyle.MUTED);
        paragraph("setting.applies", CurseStyle.MUTED);
        paragraph("setting.display", CurseStyle.BRONZE);
        var config = CurseCycleMod.getConfig();
        int rowY = contentHeight;
        settingLabel("hud", rowY);
        addSettingControl(button(controlsX, bodyTop + rowY, 96, switchText(config.enableHudOverlay), b -> {
            config.enableHudOverlay = !config.enableHudOverlay; config.save(); clearAndInit();
        }), rowY);
        rowY += 29;
        final int xY = rowY;
        settingLabel("hud_x", rowY);
        addSettingControl(button(controlsX, bodyTop + xY, 26, Text.literal("-"), b -> {
            config.hudPositionX = Math.max(0, config.hudPositionX - 10); config.save(); clearAndInit();
        }), rowY);
        addSettingControl(button(controlsX + 70, bodyTop + xY, 26, Text.literal("+"), b -> {
            config.hudPositionX = Math.min(width - 30, config.hudPositionX + 10); config.save(); clearAndInit();
        }), rowY);
        settingValue(config.hudPositionX, rowY);
        rowY += 29;
        final int yY = rowY;
        settingLabel("hud_y", rowY);
        addSettingControl(button(controlsX, bodyTop + yY, 26, Text.literal("-"), b -> {
            config.hudPositionY = Math.max(0, config.hudPositionY - 10); config.save(); clearAndInit();
        }), rowY);
        addSettingControl(button(controlsX + 70, bodyTop + yY, 26, Text.literal("+"), b -> {
            config.hudPositionY = Math.min(height - 30, config.hudPositionY + 10); config.save(); clearAndInit();
        }), rowY);
        settingValue(config.hudPositionY, rowY);
        rowY += 29;
        settingLabel("wheel", rowY);
        addSettingControl(button(controlsX, bodyTop + rowY, 96, switchText(config.enableWheelAnimation), b -> {
            config.enableWheelAnimation = !config.enableWheelAnimation; config.save(); clearAndInit();
        }), rowY);
        contentHeight = rowY + 27;
        applyButton = addDrawableChild(button(left + 12, top + panelHeight - 27, 120,
                Text.translatable("text.cursecycle.menu.setting." + (saving ? "saving" : "apply")), b -> {
                    var draft = parsedDraft();
                    if (draft == null || !supportsSettings()) return;
                    saving = true; saveTicks = 0; saveStatus = "saving";
                    ClientPlayNetworking.send(new TimingSettingsRequestPayload(true, draft.intervalDays(), draft.noticeDays(), draft.preparationSeconds()));
                    clearAndInit();
                }));
        updateApplyButton();
    }

    private void settingLabel(String key, int row) {
        paragraphs.add(new Paragraph(textRenderer.wrapLines(Text.translatable("text.cursecycle.menu.setting." + key),
                panelWidth - 146), row + 6, CurseStyle.TEXT));
    }

    private void settingValue(int value, int row) {
        // A separate narrow text column between the +/- buttons.
        settingControls.add(new SettingControl(null, row, value));
    }

    private void addSettingControl(ClickableWidget widget, int row) {
        addDrawableChild(widget);
        settingControls.add(new SettingControl(widget, row, null));
    }

    private void positionSettingControls() {
        for (var control : settingControls) {
            if (control.widget == null) continue;
            int y = bodyTop + control.offset - scroll;
            control.widget.setY(y);
            control.widget.visible = y >= bodyTop && y + control.widget.getHeight() <= bodyBottom;
        }
    }

    private void updateApplyButton() {
        if (applyButton == null) return;
        var snapshot = ClientTimingSettings.get();
        var draft = parsedDraft();
        applyButton.active = !saving && snapshot != null && snapshot.editable() && draft != null
                && !draft.equals(snapshot.settings()) && supportsSettings();
    }

    private boolean supportsSettings() {
        return client != null && client.getNetworkHandler() != null && ClientPlayNetworking.canSend(TimingSettingsRequestPayload.ID);
    }

    private Text switchText(boolean value) {
        return Text.translatable(value ? "options.on" : "options.off");
    }

    @Override public void tick() {
        if (settingsRevision != ClientTimingSettings.revision()) {
            settingsRevision = ClientTimingSettings.revision();
            var snapshot = ClientTimingSettings.get();
            if (snapshot != null) {
                if (timingDraft == null || saving || java.util.Objects.equals(parsedDraft(), baseline)) loadTimingDraft();
                saveStatus = switch (snapshot.result()) {
                    case TimingSettingsPayload.SAVED -> "saved";
                    case TimingSettingsPayload.DENIED -> "readonly";
                    case TimingSettingsPayload.INVALID -> "invalid";
                    case TimingSettingsPayload.SAVE_FAILED -> "failed";
                    default -> "";
                };
                saving = false;
            }
            clearAndInit();
        }
        if (saving && ++saveTicks > 200) {
            saving = false; saveStatus = "timeout"; clearAndInit();
        }
        String signature = ClientDataHolder.getActiveChallengeIds().toString()
                + ClientDataHolder.getNextChallengeId() + ClientDataHolder.getHistoryChallengeIds()
                + ClientDataHolder.getBlockedDirection();
        if (!stateSignature.equals(signature)) {
            stateSignature = signature;
            if (activeTab != SETTINGS) clearAndInit();
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
        } else if (activeTab == HOME || activeTab == COMMANDS || activeTab == SETTINGS) {
            renderParagraphs(context);
        } else {
            context.enableScissor(left + 10, bodyTop, left + panelWidth - 10, bodyBottom);
            if (entries.isEmpty()) {
                String key = activeTab == ACTIVE ? "text.cursecycle.no_active"
                        : activeTab == NEXT ? "text.cursecycle.menu.no_next" : "text.cursecycle.menu.no_history";
                CurseStyle.wrapped(context, textRenderer, Text.translatable(key),
                        left + 14, bodyTop + 8, panelWidth - 28, CurseStyle.MUTED);
            }
            for (Entry entry : entries) renderEntry(context, entry, mouseX, mouseY);
            context.disableScissor();
            drawScroll(context, scroll, contentHeight, bodyTop, bodyBottom);
            if (activeTab == HISTORY) {
                int pages = Math.max(1, (ClientDataHolder.getHistoryChallengeIds().size() + historyPerPage() - 1) / historyPerPage());
                context.drawTextWithShadow(textRenderer, (historyPage + 1) + " / " + pages,
                        left + 156, top + panelHeight - 21, CurseStyle.MUTED);
            }
        }
        super.render(context, mouseX, mouseY, delta);
        if (modal == null && (activeTab == HOME || activeTab == COMMANDS) && contentHeight > bodyBottom - bodyTop)
            context.drawTextWithShadow(textRenderer, Text.translatable("text.cursecycle.menu.scroll_hint"),
                    left + 14, top + panelHeight - 21, CurseStyle.MUTED);
        if (modal == null && activeTab == SETTINGS && !saveStatus.isEmpty())
            context.drawTextWithShadow(textRenderer, CurseStyle.trim(textRenderer,
                    Text.translatable("text.cursecycle.menu.setting." + saveStatus).getString(), panelWidth - 234),
                    left + 141, top + panelHeight - 21, CurseStyle.MUTED);
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

    private void renderParagraphs(DrawContext context) {
        context.enableScissor(left + 10, bodyTop, left + panelWidth - 10, bodyBottom);
        for (var paragraph : paragraphs) {
            int y = bodyTop + paragraph.offset - scroll;
            for (var line : paragraph.lines) {
                context.drawTextWithShadow(textRenderer, line, left + 14, y, paragraph.color);
                y += 11;
            }
        }
        for (var control : settingControls) if (control.value != null)
            context.drawCenteredTextWithShadow(textRenderer, Integer.toString(control.value), left + panelWidth - 64,
                    bodyTop + control.offset - scroll + 6, CurseStyle.MUTED);
        context.disableScissor();
        drawScroll(context, scroll, contentHeight, bodyTop, bodyBottom);
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
        else scroll = Math.clamp(scroll - (int) (vertical * 22), 0,
                Math.max(0, contentHeight - (bodyBottom - bodyTop)));
        positionSettingControls();
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
    private record Paragraph(List<OrderedText> lines, int offset, int color) {}
    private record SettingControl(ClickableWidget widget, int offset, Integer value) {}
}
