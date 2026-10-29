package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.InvertedMouseChallenge;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.InvertedMouseInput;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvertedMouseInputTest {
    private static final String ID = InvertedMouseChallenge.ID;

    @AfterEach void resetClient() { ClientDataHolder.reset(); }

    @Test void registrationMatchesSpecificationAndResolvesInDoubleChallenge() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(ID);
        assertInstanceOf(InvertedMouseChallenge.class, challenge);
        assertEquals("Лучше бы вечная тошнота", challenge.getName());
        assertEquals("Мышь поворачивает камеру в противоположную сторону.", challenge.getShortDescription());
        assertEquals("Горизонтальное и вертикальное вращение камеры от мыши инвертируются относительно текущих "
                + "пользовательских настроек. Чувствительность сохраняется. Курсор и клики в меню, "
                + "кнопки атаки/использования и колесо прокрутки работают обычно.", challenge.getDetailedRules());
        assertEquals(ChallengeDifficulty.HARD, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.HARD).contains(challenge));
        assertEquals(List.of(ID, "no_sprint"),
                ChallengeUtils.resolveToActiveIds("double_challenge:inverted_mouse,no_sprint"));
    }

    @Test void gateRequiresSynchronizedCurrentIdsAndLivingEligibleMode() {
        for (var mode : GameMode.values()) {
            assertEquals(mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE,
                    InvertedMouseInput.affects(true, List.of(ID, ID, "inverted_keyboard"), mode, true));
            assertFalse(InvertedMouseInput.affects(false, List.of(ID), mode, true));
            assertFalse(InvertedMouseInput.affects(true, List.of(ID), mode, false));
            assertFalse(InvertedMouseInput.affects(true, List.of("no_sprint"), mode, true));
        }
        assertFalse(InvertedMouseInput.affects(true, List.of(ID), null, true));
        assertFalse(InvertedMouseInput.shouldInvert(null));
    }

    @Test void disconnectAndReplacementSynchronizationCannotReuseEndedActivation() {
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
        return InvertedMouseInput.affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(),
                GameMode.SURVIVAL, true);
    }
}
