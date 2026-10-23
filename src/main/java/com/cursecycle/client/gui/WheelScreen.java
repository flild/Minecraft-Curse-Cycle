package com.cursecycle.client.gui;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.CurseCycleMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundEvent;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class WheelScreen extends Screen {

    private final List<Challenge> wheelItems = new ArrayList<>();
    private final String rawWinnerId;
    private final String actualWinnerBaseId;
    private final List<Challenge> subChallenges = new ArrayList<>();
    private Challenge winningChallenge;

    private float currentAngle = 0;
    private int ticksElapsed = 0;
    private static final int TOTAL_SPIN_TICKS = 100; // ~5 seconds (20 ticks/sec * 5)
    private boolean isFinished = false;
    private int lastSoundSegment = -1;
    private float targetAngle = 0;
    private float totalSpinAngle = 0;

    public WheelScreen(List<String> candidateIds, String winnerId) {
        super(Text.translatable("text.cursecycle.wheel.title"));
        this.rawWinnerId = winnerId;
        this.actualWinnerBaseId = ChallengeUtils.getBaseChallengeId(winnerId);

        List<String> subIds = ChallengeUtils.parseSubChallengeIds(winnerId);
        for (String sub : subIds) {
            Challenge c = ChallengeRegistry.get(sub);
            if (c != null) subChallenges.add(c);
        }

        for (String id : candidateIds) {
            Challenge c = ChallengeRegistry.get(id);
            if (c != null) {
                wheelItems.add(c);
            }
        }
        this.winningChallenge = ChallengeRegistry.get(actualWinnerBaseId);

        int winnerIndex = -1;
        for (int i = 0; i < wheelItems.size(); i++) {
            if (wheelItems.get(i).getId().equals(actualWinnerBaseId)) {
                winnerIndex = i;
                break;
            }
        }

        if (winnerIndex != -1 && !wheelItems.isEmpty()) {
            float segmentAngle = 360.0f / wheelItems.size();
            float rawTarget = 270.0f - (winnerIndex * segmentAngle);
            targetAngle = (rawTarget % 360.0f + 360.0f) % 360.0f;
            totalSpinAngle = 1800.0f + targetAngle;
        } else {
            totalSpinAngle = 1800.0f;
        }
    }

    @Override
    public boolean shouldPause() {
        return false; // Game does NOT pause!
    }

    @Override
    public void tick() {
        super.tick();
        if (!CurseCycleMod.getConfig().enableWheelAnimation) {
            close();
            return;
        }
        ticksElapsed++;

        if (ticksElapsed <= TOTAL_SPIN_TICKS) {
            float progress = (float) ticksElapsed / TOTAL_SPIN_TICKS;
            float easeOut = 1.0f - (float) Math.pow(1.0f - progress, 3);
            currentAngle = (totalSpinAngle * easeOut) % 360.0f;

            if (!wheelItems.isEmpty()) {
                int segmentCount = wheelItems.size();
                float segmentAngle = 360.0f / segmentCount;
                int currentSegment = (int) (currentAngle / segmentAngle);
                if (currentSegment != lastSoundSegment) {
                    lastSoundSegment = currentSegment;
                    playWheelSound(CurseCycleMod.getConfig().wheelSoundPath,
                            SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f);
                }
            }
        } else if (!isFinished) {
            isFinished = true;
            currentAngle = targetAngle;
            playWheelSound(CurseCycleMod.getConfig().wheelFinishSoundPath,
                    SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    private void playWheelSound(String path, SoundEvent fallback, float pitch, float volume) {
        Identifier id = path == null ? null : Identifier.tryParse(path);
        SoundEvent sound = id == null ? fallback : Registries.SOUND_EVENT.getOrEmpty(id).orElse(fallback);
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render semi-transparent background overlay without applying post-processing blur
        context.fill(0, 0, this.width, this.height, 0x88000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        TextRenderer textRenderer = this.textRenderer;
        int centerX = width / 2;
        int centerY = height / 2;

        // Title Header
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("🎰 КОЛЕСО ФОРТУНЫ CURSE-CYCLE 🎰").formatted(Formatting.GOLD, Formatting.BOLD), centerX, centerY - 110, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Определяется следующее испытание...").formatted(Formatting.GRAY), centerX, centerY - 95, 0xAAAAAA);

        int radius = 70;

        if (!wheelItems.isEmpty()) {
            int count = wheelItems.size();
            float segmentAngle = 360.0f / count;

            for (int i = 0; i < count; i++) {
                Challenge challenge = wheelItems.get(i);
                double angleDeg = currentAngle + (i * segmentAngle);
                double angleRad = Math.toRadians(angleDeg);

                int x = centerX + (int) (Math.cos(angleRad) * radius);
                int y = centerY + (int) (Math.sin(angleRad) * radius);

                int color = challenge.getDifficulty().getColor().getColorValue() != null
                        ? challenge.getDifficulty().getColor().getColorValue()
                        : 0xFFFFFF;

                // Segment card box
                context.fill(x - 28, y - 10, x + 28, y + 10, 0xDD222233);
                context.drawBorder(x - 28, y - 10, 56, 20, color | 0xFF000000);

                String label = challenge.getName();
                if (textRenderer.getWidth(label) > 50) {
                    label = textRenderer.trimToWidth(label, 44) + "…";
                }
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x, y - 4, color);
            }

            // Pointer at top (12 o'clock / 270 degrees)
            context.fill(centerX - 3, centerY - radius - 20, centerX + 3, centerY - radius - 5, 0xFFFF0000);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("▼").formatted(Formatting.RED, Formatting.BOLD), centerX, centerY - radius - 28, 0xFF0000);
        }

        // Winner Announcement Card when finished
        if (isFinished) {
            int cardW = 260;
            List<net.minecraft.text.OrderedText> titleLines = new ArrayList<>();
            List<net.minecraft.text.OrderedText> subLines = new ArrayList<>();

            if (winningChallenge != null) {
                titleLines = textRenderer.wrapLines(Text.literal(winningChallenge.getName()).formatted(Formatting.BOLD), cardW - 20);
                if (!subChallenges.isEmpty()) {
                    String subNames = subChallenges.stream().map(Challenge::getName).reduce((a, b) -> a + " & " + b).orElse("");
                    subLines = textRenderer.wrapLines(Text.literal("(" + subNames + ")").formatted(Formatting.AQUA), cardW - 20);
                }
            } else {
                titleLines = textRenderer.wrapLines(Text.literal(rawWinnerId), cardW - 20);
            }

            int cardH = 26 + (titleLines.size() * 12) + (subLines.size() * 12) + 16 + 20;
            int cardX = centerX - (cardW / 2);
            int cardY = centerY - (cardH / 2);

            context.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xEE11111D);
            context.drawBorder(cardX, cardY, cardW, cardH, 0xFFFFAA00);

            context.drawCenteredTextWithShadow(textRenderer, Text.literal("ВЫБРАНО ИСПЫТАНИЕ!").formatted(Formatting.YELLOW, Formatting.BOLD), centerX, cardY + 8, 0xFFFFAA00);

            int currentY = cardY + 22;

            if (winningChallenge != null) {
                int diffColor = winningChallenge.getDifficulty().getColor().getColorValue() != null ? winningChallenge.getDifficulty().getColor().getColorValue() : 0xFFFFFF;
                for (net.minecraft.text.OrderedText line : titleLines) {
                    context.drawCenteredTextWithShadow(textRenderer, line, centerX, currentY, diffColor);
                    currentY += 12;
                }

                if (!subLines.isEmpty()) {
                    for (net.minecraft.text.OrderedText line : subLines) {
                        context.drawCenteredTextWithShadow(textRenderer, line, centerX, currentY, 0x55FFFF);
                        currentY += 12;
                    }
                }

                context.drawCenteredTextWithShadow(textRenderer, Text.literal("[" + winningChallenge.getDifficulty().getDisplayName() + "]").formatted(winningChallenge.getDifficulty().getColor()), centerX, currentY + 2, diffColor);
            } else {
                for (net.minecraft.text.OrderedText line : titleLines) {
                    context.drawCenteredTextWithShadow(textRenderer, line, centerX, currentY, 0xFFFFFF);
                    currentY += 12;
                }
            }

            context.drawCenteredTextWithShadow(textRenderer, Text.literal("(Нажмите ЛКМ или ESC, чтобы закрыть)").formatted(Formatting.DARK_GRAY), centerX, cardY + cardH - 14, 0x888888);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished) {
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
