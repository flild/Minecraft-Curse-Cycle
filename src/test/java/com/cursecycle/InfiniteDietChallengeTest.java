package com.cursecycle;

import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.InfiniteDietChallenge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InfiniteDietChallengeTest {
    @Test
    void registeredOnceInMediumPoolWithRequestedMetadata() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get("infinite_diet");
        assertInstanceOf(InfiniteDietChallenge.class, challenge);
        assertEquals("Жестокая диета", challenge.getName());
        assertEquals("Ешь не ешь — всё мимо кассы.", challenge.getShortDescription());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals("infinite_diet")).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
        challenge.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
    }

    @Test
    void resolvesWithExistingChallenge() {
        assertEquals(List.of("infinite_diet", "no_sprint"),
                ChallengeUtils.resolveToActiveIds("double_challenge:infinite_diet,no_sprint"));
    }
}
