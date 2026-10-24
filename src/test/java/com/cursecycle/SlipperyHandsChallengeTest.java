package com.cursecycle;

import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.SlipperyHandsChallenge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlipperyHandsChallengeTest {
    @Test
    void registeredAsEasyGlobalChallengeWithRequestedText() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get("slippery_hands");
        assertInstanceOf(SlipperyHandsChallenge.class, challenge);
        assertEquals("Только фонка не хватает", challenge.getName());
        assertEquals("Тун тун тун тундундун дун дун", challenge.getShortDescription());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(ChallengeDifficulty.EASY, challenge.getDifficulty());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.EASY).contains(challenge));
        assertEquals(1, ChallengeRegistry.getAll().stream()
                .filter(c -> c.getId().equals(SlipperyHandsChallenge.ID)).count());
        challenge.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.EASY).contains(challenge));
    }
}
