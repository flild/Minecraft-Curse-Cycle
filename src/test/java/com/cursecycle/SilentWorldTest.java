package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.SilentWorldChallenge;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.SilentWorld;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SilentWorldTest {
    private static final String ID = SilentWorldChallenge.ID;
    @AfterEach void reset() { ClientDataHolder.reset(); }

    @Test void registrationMatchesSpecification() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(ID);
        assertInstanceOf(SilentWorldChallenge.class, challenge);
        assertEquals("Тихий час", challenge.getName());
        assertEquals("Все звуки Minecraft отключены.", challenge.getShortDescription());
        assertEquals("Для игроков полностью отключается вывод звука Minecraft: окружение, существа, шаги, интерфейс, музыка и звуки мода. Значения громкости в настройках не переписываются. Изображение, субтитры, звук внешних приложений и голос вне звукового движка Minecraft не меняются.", challenge.getDetailedRules());
        assertEquals(ChallengeDifficulty.EASY, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count());
        assertEquals(List.of(ID, "no_sprint"), ChallengeUtils.resolveToActiveIds("double_challenge:silent_world,no_sprint"));
    }

    @Test void requiresCurrentSyncedLivingSurvivalOrAdventure() {
        assertFalse(SilentWorld.shouldMute(null));
        for (var mode : GameMode.values()) {
            assertEquals(mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE,
                    SilentWorld.affects(true, List.of(ID, ID, "no_sprint"), mode, true));
            assertFalse(SilentWorld.affects(false, List.of(ID), mode, true));
            assertFalse(SilentWorld.affects(true, List.of(ID), mode, false));
            assertFalse(SilentWorld.affects(true, List.of("no_sprint"), mode, true));
        }
        assertFalse(SilentWorld.affects(true, List.of(ID), null, true));
        ClientDataHolder.updateState(1, 20, List.of(ID, "no_sprint"), "", List.of());
        assertTrue(active());
        ClientDataHolder.reset();
        assertFalse(active());
        ClientDataHolder.updateState(2, 40, List.of("no_sprint"), "", List.of(ID));
        assertFalse(active());
    }

    private boolean active() {
        return SilentWorld.affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(), GameMode.SURVIVAL, true);
    }
}
