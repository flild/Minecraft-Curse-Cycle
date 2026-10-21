package com.cursecycle;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.NoJumpingChallenge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NoJumpingChallengeTest {

    @BeforeEach
    void setUp() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
    }

    @Test
    void testNoJumpingRegistration() {
        Challenge challenge = ChallengeRegistry.get(NoJumpingChallenge.ID);
        assertNotNull(challenge, "NoJumpingChallenge should be registered in ChallengeRegistry");
        assertEquals("no_jumping", challenge.getId());
        assertEquals("Прикованные к земле", challenge.getName());
        assertEquals("Прыжки полностью заблокированы.", challenge.getShortDescription());
        assertEquals("Игроки теряют способность совершать обычные прыжки.", challenge.getDetailedRules());
        assertEquals(ChallengeDifficulty.EASY, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
    }

    @Test
    void testNoJumpingInEasyList() {
        var easyChallenges = ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.EASY);
        assertTrue(easyChallenges.stream().anyMatch(c -> c.getId().equals(NoJumpingChallenge.ID)),
                "NoJumpingChallenge should be in the list of enabled easy challenges");
    }

    @Test
    void testIsNoJumpingActiveForNullPlayer() {
        assertFalse(NoJumpingChallenge.isNoJumpingActiveForPlayer(null),
                "isNoJumpingActiveForPlayer should return false when player is null");
    }
}
