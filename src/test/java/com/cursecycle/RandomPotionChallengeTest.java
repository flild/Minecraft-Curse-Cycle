package com.cursecycle;

import com.cursecycle.challenge.impl.RandomPotionChallenge;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.List;
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
        assertTrue(clock.due(5000));
        assertFalse(clock.due(5000));
        assertFalse(clock.due(5599));
        assertTrue(clock.due(5600));
    }
    @Test void exactPool() {
        assertEquals(19, RandomPotionChallenge.POOL.size());
        assertEquals(19, Set.copyOf(RandomPotionChallenge.POOL).size());
        assertTrue(RandomPotionChallenge.POOL.containsAll(Set.of("instant_damage", "instant_health", "oozing", "weaving", "infested")));
        assertFalse(RandomPotionChallenge.POOL.contains("wither"));
        assertEquals(List.of("speed", "slowness", "strength", "instant_health", "instant_damage",
                "jump_boost", "regeneration", "resistance", "fire_resistance", "water_breathing",
                "invisibility", "night_vision", "weakness", "poison", "slow_falling", "wind_charged",
                "weaving", "oozing", "infested"), RandomPotionChallenge.POOL);
    }
}
