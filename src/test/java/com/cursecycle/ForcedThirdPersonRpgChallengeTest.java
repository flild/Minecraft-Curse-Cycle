package com.cursecycle;

import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.ForcedThirdPersonRpgChallenge;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForcedThirdPersonRpgChallengeTest {
    @Test
    void registeredOnceAsSelectableEasyGlobalChallenge() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(ForcedThirdPersonRpgChallenge.ID);
        assertInstanceOf(ForcedThirdPersonRpgChallenge.class, challenge);
        assertEquals("Как будто это рпг", challenge.getName());
        assertEquals("Вид от первого лица заблокирован.", challenge.getShortDescription());
        assertEquals(ChallengeDifficulty.EASY, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1L, ChallengeRegistry.getAll().stream()
                .filter(c -> c.getId().equals(ForcedThirdPersonRpgChallenge.ID)).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.EASY).contains(challenge));
        challenge.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.EASY).contains(challenge));
    }

    @Test
    void resolvesInDoubleChallengeAndOnlyAffectsSurvivalAndAdventure() {
        assertEquals(List.of(ForcedThirdPersonRpgChallenge.ID, "no_sprint"),
                ChallengeUtils.resolveToActiveIds("double_challenge:forced_third_person_rpg,no_sprint"));
        assertTrue(ForcedThirdPersonRpgChallenge.affectsGameMode(GameMode.SURVIVAL));
        assertTrue(ForcedThirdPersonRpgChallenge.affectsGameMode(GameMode.ADVENTURE));
        assertFalse(ForcedThirdPersonRpgChallenge.affectsGameMode(GameMode.CREATIVE));
        assertFalse(ForcedThirdPersonRpgChallenge.affectsGameMode(GameMode.SPECTATOR));
        assertFalse(ForcedThirdPersonRpgChallenge.affectsGameMode(null));
    }
}
