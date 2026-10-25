package com.cursecycle;

import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.ForcedThirdPersonFrontChallenge;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForcedThirdPersonFrontChallengeTest {
    @Test
    void registeredOnceAsSelectableMediumGlobalChallenge() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(ForcedThirdPersonFrontChallenge.ID);
        assertInstanceOf(ForcedThirdPersonFrontChallenge.class, challenge);
        assertEquals("Смотри мне в глаза.", challenge.getName());
        assertEquals("Впереди явно что-то интересное, но пока смотри назад", challenge.getShortDescription());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1L, ChallengeRegistry.getAll().stream()
                .filter(c -> c.getId().equals(ForcedThirdPersonFrontChallenge.ID)).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
        challenge.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
    }

    @Test
    void doubleChallengeResolvesAndOnlySurvivalAndAdventureAreAffected() {
        assertEquals(List.of(ForcedThirdPersonFrontChallenge.ID, "no_sprint"),
                ChallengeUtils.resolveToActiveIds("double_challenge:forced_third_person_front,no_sprint"));
        assertTrue(ForcedThirdPersonFrontChallenge.affectsGameMode(GameMode.SURVIVAL));
        assertTrue(ForcedThirdPersonFrontChallenge.affectsGameMode(GameMode.ADVENTURE));
        assertFalse(ForcedThirdPersonFrontChallenge.affectsGameMode(GameMode.CREATIVE));
        assertFalse(ForcedThirdPersonFrontChallenge.affectsGameMode(GameMode.SPECTATOR));
        assertFalse(ForcedThirdPersonFrontChallenge.affectsGameMode(null));
    }
}
