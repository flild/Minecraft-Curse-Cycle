package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.TinyFovChallenge;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.TinyFovCamera;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TinyFovCameraTest {
    private static final String ID = TinyFovChallenge.ID;

    @AfterEach void resetClient() { ClientDataHolder.reset(); }

    @Test void registrationMatchesSpecificationAndResolvesInDoubleChallenge() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(ID);
        assertInstanceOf(TinyFovChallenge.class, challenge);
        assertEquals("Тесновато", challenge.getName());
        assertEquals("Поле зрения сужено до 15 градусов.", challenge.getShortDescription());
        assertEquals("Эффективное поле зрения игровой камеры фиксировано на 15 градусах во всех перспективах. "
                + "Спринт, скорость, натягивание лука, подзорная труба и другие ванильные FOV-модификаторы "
                + "не расширяют и не сужают его. Настройки пользователя и масштаб интерфейса сохраняются.",
                challenge.getDetailedRules());
        assertEquals(ChallengeDifficulty.EASY, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.EASY).contains(challenge));
        assertEquals(List.of(ID, "no_sprint"), ChallengeUtils.resolveToActiveIds("double_challenge:tiny_fov,no_sprint"));
    }

    @Test void renderGateRequiresSyncedLivingSurvivalOrAdventurePlayer() {
        for (var mode : GameMode.values()) {
            assertEquals(mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE,
                    TinyFovCamera.affects(true, List.of(ID, ID, "forced_third_person_front"), mode, true));
            assertFalse(TinyFovCamera.affects(false, List.of(ID), mode, true));
            assertFalse(TinyFovCamera.affects(true, List.of(ID), mode, false));
            assertFalse(TinyFovCamera.affects(true, List.of("no_sprint"), mode, true));
        }
        assertFalse(TinyFovCamera.affects(true, List.of(ID), null, true));
        assertFalse(TinyFovCamera.shouldApply(null));
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
        return TinyFovCamera.affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(),
                GameMode.SURVIVAL, true);
    }
}
