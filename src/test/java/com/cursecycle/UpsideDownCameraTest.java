package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.UpsideDownCameraChallenge;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.UpsideDownCamera;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UpsideDownCameraTest {
    private static final String ID = UpsideDownCameraChallenge.ID;

    @AfterEach void resetClient() { ClientDataHolder.reset(); }

    @Test void registrationMatchesSpecificationAndResolvesInDoubleChallenge() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(ID);
        assertInstanceOf(UpsideDownCameraChallenge.class, challenge);
        assertEquals("С ног на голову", challenge.getName());
        assertEquals("Камера перевёрнута на 180°, интерфейс остаётся обычным.", challenge.getShortDescription());
        assertEquals("В Survival и Adventure изображение мира повёрнуто на 180 градусов вокруг оси взгляда. "
                + "HUD, меню, текст, прицел и курсор остаются в обычной ориентации. "
                + "Направление взгляда, луч взаимодействия и управление сами по себе не инвертируются; "
                + "смена перспективы не убирает переворот.", challenge.getDetailedRules());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
        assertEquals(List.of(ID, "no_sprint"), ChallengeUtils.resolveToActiveIds("double_challenge:upside_down_camera,no_sprint"));
    }

    @Test void renderGateRequiresSyncedLivingSurvivalOrAdventurePlayer() {
        for (var mode : GameMode.values()) {
            assertEquals(mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE,
                    UpsideDownCamera.affects(true, List.of(ID, ID, "forced_third_person_front"), mode, true));
            assertFalse(UpsideDownCamera.affects(false, List.of(ID), mode, true));
            assertFalse(UpsideDownCamera.affects(true, List.of(ID), mode, false));
            assertFalse(UpsideDownCamera.affects(true, List.of("no_sprint"), mode, true));
        }
        assertFalse(UpsideDownCamera.affects(true, List.of(ID), null, true));
        assertFalse(UpsideDownCamera.shouldApply(null));
    }

    @Test void resetAndReplacementSyncCannotRetainAnEndedCameraOverride() {
        ClientDataHolder.updateState(1, 20, List.of(ID), "", List.of());
        assertTrue(active());
        ClientDataHolder.reset();
        assertFalse(active());
        ClientDataHolder.updateState(2, 40, List.of("no_sprint"), "", List.of(ID));
        assertFalse(active());
        ClientDataHolder.updateState(3, 60, List.of(ID, "no_sprint"), "", List.of());
        assertTrue(active());
        ClientDataHolder.updateState(4, 80, List.of(), "", List.of(ID));
        assertFalse(active());
    }

    private boolean active() {
        return UpsideDownCamera.affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(),
                GameMode.SURVIVAL, true);
    }
}
