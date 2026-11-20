package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.OffsetCrosshairChallenge;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.OffsetCrosshair;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OffsetCrosshairTest {
    private static final String ID = OffsetCrosshairChallenge.ID;

    @AfterEach void reset() { ClientDataHolder.reset(); }

    @Test void registrationAndDescriptionsMatchSpecification() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(ID);
        assertInstanceOf(OffsetCrosshairChallenge.class, challenge);
        assertFalse(challenge.getName().isBlank());
        assertFalse(challenge.getShortDescription().isBlank());
        assertFalse(challenge.getDetailedRules().isBlank());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
        assertEquals(List.of(ID, "no_sprint"), ChallengeUtils.resolveToActiveIds("double_challenge:offset_crosshair,no_sprint"));
    }

    @Test void coordinatesUseCurrentGuiSizeAndClampEachAxisIndependently() {
        assertEquals(80, OffsetCrosshair.translation(640, 80));
        assertEquals(40, OffsetCrosshair.translation(360, 40));
        assertEquals(152, 160 / 2 + OffsetCrosshair.translation(160, 80));
        assertEquals(90, 100 / 2 + OffsetCrosshair.translation(100, 40));
        assertEquals(72, 80 / 2 + OffsetCrosshair.translation(80, 40));
        assertEquals(8, 16 / 2 + OffsetCrosshair.translation(16, 80));
        for (int size = 0; size < 16; size++) {
            assertEquals(0, OffsetCrosshair.translation(size, 80));
            assertEquals(0, OffsetCrosshair.translation(size, 40));
        }
        for (int size = 16; size <= 4000; size++) {
            for (int offset : new int[]{80, 40}) {
                int point = size / 2 + OffsetCrosshair.translation(size, offset);
                assertTrue(point >= 8 && point <= size - 8);
                if (size / 2 + offset <= size - 8) assertEquals(size / 2 + offset, point);
                else assertEquals(size - 8, point);
            }
        }
    }

    @Test void syncModeLifeAndReplacementGateTheEffectWithoutStoredOverrides() {
        assertFalse(OffsetCrosshair.shouldApply(null));
        for (var mode : GameMode.values()) {
            assertEquals(mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE,
                    OffsetCrosshair.affects(true, List.of(ID, ID, "no_sprint"), mode, true));
            assertFalse(OffsetCrosshair.affects(false, List.of(ID), mode, true));
            assertFalse(OffsetCrosshair.affects(true, List.of(ID), mode, false));
            assertFalse(OffsetCrosshair.affects(true, List.of("no_sprint"), mode, true));
        }
        assertFalse(OffsetCrosshair.affects(true, List.of(ID), null, true));
        ClientDataHolder.updateState(1, 20, List.of(ID, "no_sprint"), "", List.of());
        assertTrue(active());
        ClientDataHolder.reset();
        assertFalse(active());
        ClientDataHolder.updateState(2, 40, List.of("no_sprint"), "", List.of(ID));
        assertFalse(active());
    }

    @Test void actionRayProjectsOntoShiftedMarkAtEveryDistanceFovAndGuiSize() {
        // Expected HUD coordinates include odd sizes, edge clamping and the tiny-window fallback.
        for (int[] screen : new int[][]{{640,360,400,220}, {321,181,240,130},
                {160,100,152,90}, {17,16,9,8}, {15,9,7,4}}) {
            int width = screen[0], height = screen[1];
            for (float fov : new float[]{15, 70, 110}) for (boolean rolled : new boolean[]{false, true}) {
                var vanilla = new Matrix4f().perspective((float) Math.toRadians(fov),
                        (float) width / height, .05f, 2048);
                if (rolled) vanilla.rotateZ((float) Math.PI);
                var projection = new Matrix4f(vanilla);
                assertSame(projection, OffsetCrosshair.offsetProjection(projection, width, height));
                for (float distance : new float[]{1, 4.5f, 32, 1000}) {
                    var hit = projection.transformProject(new Vector3f(0, 0, -distance));
                    assertEquals(screen[2], (hit.x + 1) * width / 2, 1e-4);
                    assertEquals(screen[3], (1 - hit.y) * height / 2, 1e-4);
                    assertEquals(vanilla.transformProject(new Vector3f(0, 0, -distance)).z, hit.z, 1e-6);
                }
                // Looking through the off-centre mark follows the real action/arrow ray; the screen
                // centre now points elsewhere. This is what makes aiming to the side functional.
                var inverse = new Matrix4f(projection).invert();
                var markRay = inverse.transformProject(new Vector3f(2f * screen[2] / width - 1,
                        1 - 2f * screen[3] / height, 0)).normalize();
                assertTrue(markRay.equals(new Vector3f(0, 0, -1), 1e-5f));
                var centreRay = inverse.transformProject(new Vector3f(0, 0, 0)).normalize();
                assertFalse(centreRay.equals(markRay, 1e-5f));
            }
        }
    }

    @Test void minimizedWindowDoesNotCreateInvalidProjection() {
        var projection = new Matrix4f().perspective(1, 1, .05f, 128);
        var before = new Matrix4f(projection);
        for (int[] size : new int[][]{{0,360}, {640,0}, {0,0}, {-1,360}}) {
            assertSame(projection, OffsetCrosshair.offsetProjection(projection, size[0], size[1]));
            assertEquals(before, projection);
        }
    }

    private boolean active() {
        return OffsetCrosshair.affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(), GameMode.SURVIVAL, true);
    }
}
