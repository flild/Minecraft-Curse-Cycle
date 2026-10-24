package com.cursecycle;

import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.ArmorDisabledChallenge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArmorDisabledChallengeTest {
    @BeforeEach
    void register() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
    }

    @Test
    void registeredAsMediumGlobalChallenge() {
        var challenge = ChallengeRegistry.get("armor_disabled");
        assertInstanceOf(ArmorDisabledChallenge.class, challenge);
        assertEquals("Голый король", challenge.getName());
        assertEquals("Броня и элитры полностью отключены", challenge.getShortDescription());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
    }

    @Test
    void resolvesAlongsideAnotherChallenge() {
        assertEquals(List.of("armor_disabled", "no_sprint"),
                ChallengeUtils.resolveToActiveIds("double_challenge:armor_disabled,no_sprint"));
    }

    @Test
    void disabledChallengeIsExcludedFromSelection() {
        var challenge = ChallengeRegistry.get(ArmorDisabledChallenge.ID);
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
        challenge.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
    }
}
