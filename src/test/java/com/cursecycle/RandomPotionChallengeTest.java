package com.cursecycle;

import com.cursecycle.challenge.impl.RandomPotionChallenge;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RandomPotionChallengeTest {
    @Test void ticksAndDuplicateDispatch() {
        var clock = new RandomPotionChallenge.Clock(100);
        assertFalse(clock.due(100));
        assertFalse(clock.due(699));
        assertTrue(clock.due(700));
        assertFalse(clock.due(700));
        assertFalse(clock.due(1299));
        assertTrue(clock.due(1300));
    }
    @Test void exactPool() {
        assertEquals(19, RandomPotionChallenge.POOL.size());
        assertEquals(19, Set.copyOf(RandomPotionChallenge.POOL).size());
        assertTrue(RandomPotionChallenge.POOL.containsAll(Set.of("instant_damage", "instant_health", "oozing", "weaving", "infested")));
        assertFalse(RandomPotionChallenge.POOL.contains("wither"));
    }
}
