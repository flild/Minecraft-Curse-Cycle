package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.GravityTenChallenge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GravityTenTest {
    @Test void registration() {
        ChallengeRegistry.clear(); ModChallenges.registerAll();
        var c = ChallengeRegistry.get("gravity_ten");
        assertInstanceOf(GravityTenChallenge.class, c);
        assertEquals("Земля тянет к себе", c.getName());
        assertEquals("Гравитационное ускорение игрока увеличено в 3 раз.", c.getShortDescription());
        assertEquals(ChallengeDifficulty.MEDIUM, c.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, c.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(ch -> ch.getId().equals("gravity_ten")).count());
    }
}
