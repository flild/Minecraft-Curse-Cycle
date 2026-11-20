package com.cursecycle.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudLayoutTest {
    @Test void panelsStayBelowOneThirdOfTheViewportAtLargeGuiScales() {
        // Includes GUI 6 at 1920x1440, 2560x1440 and 3840x2160, plus a small window.
        for (int[] viewport : new int[][]{{320, 240}, {427, 240}, {640, 360}, {360, 240}, {344, 720}}) {
            float scale = HudLayout.scale(viewport[0], viewport[1]);
            assertTrue(202 * scale <= viewport[0] / 3f, "challenge HUD width");
            assertTrue(180 * scale <= viewport[0] / 3f, "heartbeat width");
            assertTrue(126 * scale <= viewport[0] / 3f, "stamina width");
        }
    }

    @Test void enoughSpaceKeepsTheOriginalSizeAndUserScaleStillReducesIt() {
        assertEquals(1f, HudLayout.scale(640, 360));
        assertEquals(1f, HudLayout.scale(1280, 720));
        float automatic = HudLayout.scale(320, 240);
        assertEquals(101f, 202 * automatic);
        assertEquals(50.5f, 202 * automatic * .5f);
    }

    @Test void rhythmAndStaminaStayCenteredAndSeparateFromVanillaHud() {
        for (int[] viewport : new int[][]{{320, 240}, {427, 240}, {640, 360}, {960, 540}}) {
            int width = viewport[0], height = viewport[1];
            // Two wrapped label lines must also fit above stamina.
            var rhythm = HudLayout.bottomCentered(width, height, 180, 41, 88);
            var stamina = HudLayout.bottomCentered(width, height, 126, 20, 62);
            assertEquals(width / 2f, rhythm.x() + rhythm.width() * rhythm.scale() / 2f, .001);
            assertEquals(width / 2f, stamina.x() + stamina.width() * stamina.scale() / 2f, .001);
            assertTrue(rhythm.y() >= 6);
            assertTrue(rhythm.y() + 6 * rhythm.scale() >= height / 2f + 8, "label clears crosshair");
            assertTrue(rhythm.y() + (rhythm.height() + 3) * rhythm.scale() < stamina.y());
            assertTrue(stamina.y() + stamina.height() * stamina.scale() <= height - 62);
        }
    }
}
