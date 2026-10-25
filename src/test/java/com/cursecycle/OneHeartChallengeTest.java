package com.cursecycle;

import com.cursecycle.challenge.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OneHeartChallengeTest {
    @Test void globalHardIsInDoubleResolution() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get("one_heart");
        assertNotNull(challenge);
        assertEquals(ChallengeDifficulty.HARD, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(2, ChallengeUtils.resolveToActiveIds("double_challenge:one_heart,no_healing").size());
    }
}
