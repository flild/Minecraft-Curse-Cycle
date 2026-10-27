package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SunlightBurnChallengeTest {
    @Test void dayBoundariesAndModulo() {
        for (long day : List.of(-24000L, 0L, 24000L, 24000000000L)) {
            assertTrue(SunlightBurnChallenge.daytime(day));
            assertTrue(SunlightBurnChallenge.daytime(day + 11999));
            assertFalse(SunlightBurnChallenge.daytime(day + 12000));
            assertFalse(SunlightBurnChallenge.daytime(day + 23999));
        }
    }

    @Test void firstTickIntervalDuplicatesAndServerCounterWrap() {
        var clock = new SunlightBurnChallenge.Clock();
        int start = Integer.MAX_VALUE - 10;
        assertTrue(clock.due(start));
        for (int i = 0; i < 20; i++) assertFalse(clock.due(start + i));
        assertTrue(clock.due(start + 20));
        assertFalse(clock.due(start + 20));
        assertTrue(clock.due(start + 100), "Missed checks do not create catch-up pulses");
        assertFalse(clock.due(start + 101));
    }

    @Test void registrationAndDoubleSelection() {
        ChallengeRegistry.clear(); ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(SunlightBurnChallenge.ID);
        assertInstanceOf(SunlightBurnChallenge.class, challenge);
        assertEquals("Стоило купить крем от солнца", challenge.getName());
        assertEquals("Под открытым дневным небом игрок загорается.", challenge.getShortDescription());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
        assertEquals(List.of("sunlight_burn", "no_sprint"),
                ChallengeUtils.resolveToActiveIds("double_challenge:sunlight_burn,no_sprint"));
    }
}
