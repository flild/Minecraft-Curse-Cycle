package com.cursecycle;

import com.cursecycle.challenge.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoHealingChallengeTest {
    @Test void registeredAsGlobalHard() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get("no_healing");
        assertNotNull(challenge);
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(ChallengeDifficulty.HARD, challenge.getDifficulty());
    }
}
