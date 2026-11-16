package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.EndlessRaidChallenge;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EndlessRaidChallengeTest {
    @Test void registrationMatchesSpecification() {
        ChallengeRegistry.clear(); ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get("endless_raid");
        assertInstanceOf(EndlessRaidChallenge.class, challenge);
        assertFalse(challenge.getName().isBlank());
        assertFalse(challenge.getShortDescription().isBlank());
        assertFalse(challenge.getDetailedRules().isBlank());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals("endless_raid")).count());
        assertEquals(List.of("endless_raid", "no_sprint"), ChallengeUtils.resolveToActiveIds("double_challenge:endless_raid,no_sprint"));
    }
}
