package com.cursecycle.client.gui;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class WheelScreen extends Screen {
    private static final Identifier RIM = Identifier.of("cursecycle", "textures/gui/wheel_rim.png");
    private final List<Challenge> items = new ArrayList<>();
    private final List<Challenge> subChallenges = new ArrayList<>();
    private final String winnerId;
    private final Challenge winner;
    private final WheelAnimation animation;
    private int ticks;
    private int lastClickTick = -20;
    private boolean finished;

    public WheelScreen(List<String> candidates, String winnerId) {
        super(Text.translatable("text.cursecycle.wheel.title"));
        this.winnerId = winnerId;
        winner = ChallengeRegistry.get(ChallengeUtils.getBaseChallengeId(winnerId));
        for (String id : candidates) {
            Challenge challenge = ChallengeRegistry.get(ChallengeUtils.getBaseChallengeId(id));
            if (challenge != null) items.add(challenge);
        }
        // A stale candidate list must still be able to show the server's authoritative result.
        if (winner != null && !items.contains(winner)) items.add(winner);
        for (String id : ChallengeUtils.parseSubChallengeIds(winnerId)) {
            Challenge challenge = ChallengeRegistry.get(id);
            if (challenge != null) subChallenges.add(challenge);
        }
        animation = new WheelAnimation(items.size(), items.indexOf(winner));
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void tick() {
        if (!CurseCycleMod.getConfig().enableWheelAnimation) {
            close();
            return;
        }
        double previous = animation.angle(ticks);
        ticks++;
        if (!finished && ticks >= WheelAnimation.DURATION) {
            finished = true;
            sound(CurseCycleMod.getConfig().wheelFinishSoundPath,
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, .75f, .7f);
            narrateScreenIfNarrationEnabled(true);
        } else if (!finished && items.size() > 1
                && animation.boundary(previous) != animation.boundary(animation.angle(ticks))) {
            lastClickTick = ticks;
            sound(CurseCycleMod.getConfig().wheelSoundPath,
                    SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, .8f + .3f * ticks / WheelAnimation.DURATION, .35f);
        }
    }

    private void sound(String path, SoundEvent fallback, float pitch, float volume) {
        Identifier id = path == null ? null : Identifier.tryParse(path);
        SoundEvent event = id == null ? fallback
                : Registries.SOUND_EVENT.getOrEmpty(id).orElse(fallback);
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(event, pitch, volume));
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        CurseStyle.backdrop(context, width, height);
    }

    @Override public Text getNarratedTitle() {
        return finished ? Text.translatable("text.cursecycle.wheel.result", CurseStyle.name(winnerId)) : title;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        float scale = Math.min(1.25f, Math.min(width / 440f, height / 368f));
        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, height / 2f, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.getMatrices().translate(-220, -184, 0);

        centered(context, Text.literal("CURSE-CYCLE"), 220, 9, CurseStyle.BRONZE);
        centered(context, Text.translatable("text.cursecycle.wheel.heading"), 220, 23, CurseStyle.MUTED);
        double angle = animation.angle(finished ? WheelAnimation.DURATION : Math.max(0, ticks - 1 + delta));
        int selected = animation.selected(angle);
        drawWheel(context, angle, selected, delta);
        drawResult(context, selected);

        centered(context, Text.translatable(finished ? "text.cursecycle.wheel.close" : "text.cursecycle.wheel.skip"),
                220, 350, CurseStyle.MUTED);
        context.getMatrices().pop();
        // No widgets: Screen.render would paint the dimming background a second time.
    }

    private void drawWheel(DrawContext context, double angle, int selected, float delta) {
        int cx = 220, cy = 149;
        CurseStyle.disk(context, cx + 2, cy + 5, 105, 0x66000000);
        CurseStyle.disk(context, cx, cy, 92, 0xFF100F16);
        if (!items.isEmpty()) {
            double segment = 360.0 / items.size();
            for (int i = 0; i < items.size(); i++) {
                double middle = angle + i * segment;
                boolean lit = i == selected;
                int color = CurseStyle.color(items.get(i));
                int outer = lit ? tint(color, .38f) : tint(color, finished ? .10f : .17f);
                double gap = Math.min(.65, segment * .08);
                CurseStyle.arc(context, cx, cy, 18, 77, middle - segment / 2 + gap,
                        middle + segment / 2 - gap, 0xFF191620, outer);
                CurseStyle.arc(context, cx, cy, 72, 77, middle - segment / 2 + gap,
                        middle + segment / 2 - gap, lit ? color : tint(color, .38f), lit ? color : tint(color, .38f));
            }
            // Upright vanilla symbols remain readable while their positions follow the disk.
            if (items.size() <= 24) {
                for (int i = 0; i < items.size(); i++) {
                    double radians = Math.toRadians(angle + i * segment);
                    int x = cx + (int) (Math.cos(radians) * 51);
                    int y = cy + (int) (Math.sin(radians) * 51);
                    CurseStyle.icon(context, items.get(i).getId(), x - 8, y - 8);
                }
            }
        }
        context.draw();
        // The generated annulus has a transparent center; sectors are rendered underneath it.
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        context.drawTexture(RIM, cx - 114, cy - 114, 228, 228, 0f, 0f, 1254, 1254, 1254, 1254);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();

        CurseStyle.disk(context, cx, cy, 22, 0xFF392C22);
        CurseStyle.arc(context, cx, cy, 20, 22, 0, 360, CurseStyle.BRONZE, CurseStyle.EDGE);
        CurseStyle.disk(context, cx, cy, 18, 0xFF18141F);
        float reveal = finished ? (float) Math.clamp((ticks - WheelAnimation.DURATION + delta) / 10.0, 0, 1) : 0;
        if (finished && subChallenges.size() >= 2) {
            float split = 5 + 4 * reveal;
            CurseStyle.diamond(context, cx - split, cy - 2 * reveal, 7, CurseStyle.PURPLE);
            CurseStyle.diamond(context, cx + split, cy + 2 * reveal, 7, 0xFF75518F);
        } else {
            CurseStyle.diamond(context, cx, cy, 10, CurseStyle.PURPLE);
            CurseStyle.diamond(context, cx - 1, cy - 2, 5, 0xFFE9C9FF);
        }
        // A small mechanical kick is tied to the last sector crossing, not random shaking.
        float kick = finished ? 0 : Math.max(0, 1 - (ticks - lastClickTick + delta) / 3f);
        context.getMatrices().push();
        context.getMatrices().translate(cx, cy - 97, 0);
        context.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-kick * 14));
        for (int row = 0; row < 16; row++) {
            int half = Math.max(1, (16 - row) / 2);
            context.fill(-half, row - 13, half + 1, row - 12, row < 3 ? 0xFFE1BC7E : CurseStyle.BRONZE);
        }
        context.getMatrices().pop();
        CurseStyle.diamond(context, cx, cy - 111, 4, CurseStyle.PURPLE);
    }

    private void drawResult(DrawContext context, int selected) {
        Challenge shown = finished ? winner : selected >= 0 ? items.get(selected) : null;
        int color = CurseStyle.color(shown);
        context.fill(152, 264, 288, 265, CurseStyle.EDGE);
        CurseStyle.diamond(context, 220, 264, 3, finished ? color : CurseStyle.BRONZE);
        centered(context, Text.translatable(finished ? "text.cursecycle.wheel.selected" : "text.cursecycle.wheel.selecting"),
                220, 274, CurseStyle.MUTED);
        String name = shown != null ? shown.getName() : winnerId;
        int y = 290;
        if (finished && !subChallenges.isEmpty()) {
            for (Challenge sub : subChallenges) {
                CurseStyle.icon(context, sub.getId(), 47, y - 4);
                context.drawTextWithShadow(textRenderer, CurseStyle.trim(textRenderer, sub.getName(), 323),
                        69, y, CurseStyle.color(sub));
                y += 19;
            }
        } else {
            for (OrderedText line : textRenderer.wrapLines(Text.literal(name), 390)) {
                context.drawCenteredTextWithShadow(textRenderer, line, 220, y, finished ? color : CurseStyle.TEXT);
                y += 11;
            }
            if (finished && shown != null) {
                centered(context, Text.literal(shown.getDifficulty().getDisplayName()), 220, y + 3, color);
                String desc = CurseStyle.trim(textRenderer, shown.getShortDescription(), 398);
                centered(context, Text.literal(desc), 220, y + 18, CurseStyle.MUTED);
            }
        }
    }

    private void centered(DrawContext context, Text text, int x, int y, int color) {
        context.drawCenteredTextWithShadow(textRenderer, text, x, y, color);
    }

    private static int tint(int color, float multiplier) {
        return 0xFF000000 | ((int) (((color >> 16) & 255) * multiplier) << 16)
                | ((int) (((color >> 8) & 255) * multiplier) << 8) | (int) ((color & 255) * multiplier);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (finished && button == 0) {
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
