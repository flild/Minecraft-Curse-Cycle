package com.cursecycle.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChallengeCountdownTest {
    @Test void wholeGameDaysRoundUpUntilTheNextBoundaryWithoutOverflow() {
        assertEquals(5, ChallengeCountdown.daysRemaining(120_000));
        assertEquals(5, ChallengeCountdown.daysRemaining(119_999));
        assertEquals(4, ChallengeCountdown.daysRemaining(96_000));
        assertEquals(2, ChallengeCountdown.daysRemaining(24_001));
        assertEquals(1, ChallengeCountdown.daysRemaining(24_000));
        assertEquals(1, ChallengeCountdown.daysRemaining(1));
        assertEquals(0, ChallengeCountdown.daysRemaining(0));
        assertEquals(0, ChallengeCountdown.daysRemaining(-1));
        assertEquals(384_307_168_202_283L, ChallengeCountdown.daysRemaining(Long.MAX_VALUE));
    }

    @Test void russianDayFormsAndEnglishSingularAreDistinct() {
        for (long days : new long[]{1, 21, 101})
            assertTrue(ChallengeCountdown.daysKey(days, "ru_ru").endsWith(".one"));
        for (long days : new long[]{2, 3, 4, 22, 104})
            assertTrue(ChallengeCountdown.daysKey(days, "ru_ru").endsWith(".few"));
        for (long days : new long[]{0, 5, 11, 12, 13, 14, 111})
            assertTrue(ChallengeCountdown.daysKey(days, "ru_ru").endsWith(".many"));
        assertTrue(ChallengeCountdown.daysKey(1, "en_us").endsWith(".one"));
        assertTrue(ChallengeCountdown.daysKey(21, "en_us").endsWith(".many"));
    }
}
