package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.BlindHudChallenge;
import com.cursecycle.client.BlindHud;
import com.cursecycle.client.ClientDataHolder;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlindHudTest {
    private static final String ID = BlindHudChallenge.ID;
    @AfterEach void reset() { ClientDataHolder.reset(); }

    @Test void registrationMatchesSpecification() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(ID);
        assertInstanceOf(BlindHudChallenge.class, challenge);
        assertFalse(challenge.getName().isBlank());
        assertFalse(challenge.getShortDescription().isBlank());
        assertFalse(challenge.getDetailedRules().isBlank());
        assertEquals(ChallengeDifficulty.EASY, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count());
        assertEquals(List.of(ID, "no_sprint"), ChallengeUtils.resolveToActiveIds("double_challenge:blind_hud,no_sprint"));
    }

    @Test void onlyCurrentSyncedLivingSurvivalAndAdventureAreAffected() {
        assertFalse(BlindHud.shouldApply(null));
        for (var mode : GameMode.values()) {
            assertEquals(mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE,
                    BlindHud.affects(true, List.of(ID, ID, "no_sprint"), mode, true));
            assertFalse(BlindHud.affects(false, List.of(ID), mode, true));
            assertFalse(BlindHud.affects(true, List.of(ID), mode, false));
            assertFalse(BlindHud.affects(true, List.of("no_sprint"), mode, true));
        }
        assertFalse(BlindHud.affects(true, List.of(ID), null, true));
        ClientDataHolder.updateState(1, 20, List.of(ID, "no_sprint"), "", List.of());
        assertTrue(active());
        ClientDataHolder.reset();
        assertFalse(active());
        ClientDataHolder.updateState(2, 40, List.of("no_sprint"), "", List.of(ID));
        assertFalse(active());
    }

    private boolean active() {
        return BlindHud.affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(), GameMode.SURVIVAL, true);
    }
}
