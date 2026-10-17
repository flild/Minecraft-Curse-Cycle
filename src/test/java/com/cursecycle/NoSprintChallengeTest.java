package com.cursecycle;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.NoSprintChallenge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NoSprintChallengeTest {

    @BeforeEach
    void setUp() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
    }

    @Test
    void testNoSprintRegistration() {
        Challenge challenge = ChallengeRegistry.get(NoSprintChallenge.ID);
        assertNotNull(challenge, "NoSprintChallenge should be registered in ChallengeRegistry");
        assertEquals("no_sprint", challenge.getId());
        assertEquals("Без бега", challenge.getName());
        assertEquals(ChallengeDifficulty.EASY, challenge.getDifficulty());
    }

    @Test
    void testNoSprintInEasyList() {
        var easyChallenges = ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.EASY);
        assertTrue(easyChallenges.stream().anyMatch(c -> c.getId().equals(NoSprintChallenge.ID)),
                "NoSprintChallenge should be in the list of enabled easy challenges");
    }
}
