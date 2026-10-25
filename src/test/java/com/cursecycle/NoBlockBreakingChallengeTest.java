package com.cursecycle;

import com.cursecycle.challenge.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoBlockBreakingChallengeTest {
    @Test void metadataAndActivation() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get("no_block_breaking");
        assertNotNull(challenge);
        assertEquals(ChallengeDifficulty.EASY, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertTrue(challenge.getDetailedRules().contains("amplifier 9"));
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(challenge.getId())).count());
    }
}
