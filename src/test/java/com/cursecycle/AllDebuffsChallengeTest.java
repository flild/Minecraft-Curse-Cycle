package com.cursecycle;

import com.cursecycle.challenge.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AllDebuffsChallengeTest {
    @Test void registeredAndSelectableInDoubleChallenge() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get("all_debuffs");
        assertNotNull(challenge);
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals("all_debuffs")).count());
        assertTrue(ChallengeUtils.resolveToActiveIds("double_challenge:all_debuffs,no_block_breaking").contains("all_debuffs"));
        challenge.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(challenge.getDifficulty()).contains(challenge));
        challenge.setEnabled(true);
    }
}
