package com.cursecycle.client.gui;

/** HUD sizes use a 640x360 GUI canvas, shrinking together when GUI Scale leaves less room. */
public final class HudLayout {
    private HudLayout() {}

    public static float scale(int width, int height) {
        return Math.min(1f, Math.min(Math.max(1, width) / 640f, Math.max(1, height) / 360f));
    }

    /** The bottom gap stays in vanilla GUI units so bars remain above the hotbar/status bars. */
    public static Bounds bottomCentered(int windowWidth, int windowHeight, int width, int height, int bottomGap) {
        float scale = scale(windowWidth, windowHeight);
        return new Bounds((windowWidth - width * scale) / 2f,
                Math.max(6, windowHeight - bottomGap - height * scale), width, height, scale);
    }

    public record Bounds(float x, float y, int width, int height, float scale) {}
}
