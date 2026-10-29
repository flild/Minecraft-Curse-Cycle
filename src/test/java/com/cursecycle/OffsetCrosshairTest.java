package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.OffsetCrosshairChallenge;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.OffsetCrosshair;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
        assertEquals("Конфига симпла", challenge.getName());
        assertEquals("Прицел визуально смещён вправо и вниз, настоящий выстрел идёт из центра.", challenge.getShortDescription());
        assertEquals("Видимый прицел смещается на 80 единиц масштабированного GUI вправо и 40 вниз от центра экрана. "
                + "Настоящее направление взгляда, атаки, добычи и использования остаётся центральным. "
                + "Мир, камера, курсор меню и остальной HUD не сдвигаются.", challenge.getDetailedRules());
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

    private boolean active() {
        return OffsetCrosshair.affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(), GameMode.SURVIVAL, true);
    }
}
