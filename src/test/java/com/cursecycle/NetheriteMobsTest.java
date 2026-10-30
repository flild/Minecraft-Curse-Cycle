package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.NetheriteMobsChallenge;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NetheriteMobsTest {
    @Test void registrationAndCompositeResolution() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(NetheriteMobsChallenge.ID);
        assertInstanceOf(NetheriteMobsChallenge.class, challenge);
        assertEquals(ChallengeDifficulty.HARD, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals("Незеритовая вечеринка", challenge.getName());
        assertEquals("Кто-то выдал всем мобам незеритовую броню.", challenge.getShortDescription());
        assertEquals(List.of("netherite_mobs", "no_sprint"),
                ChallengeUtils.resolveToActiveIds("double_challenge:netherite_mobs,no_sprint"));
        assertEquals(1L, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals("netherite_mobs")).count());
    }
}
