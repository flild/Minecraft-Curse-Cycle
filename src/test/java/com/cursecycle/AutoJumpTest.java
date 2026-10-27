package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.AutoJumpChallenge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutoJumpTest {
    @Test void registration() {
        ChallengeRegistry.clear(); ModChallenges.registerAll();
        var c = ChallengeRegistry.get("auto_jump");
        assertInstanceOf(AutoJumpChallenge.class, c);
        assertEquals("Попрыгунчик", c.getName());
        assertEquals("Персонаж сам прыгает при каждом приземлении.", c.getShortDescription());
        assertEquals(ChallengeDifficulty.EASY, c.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, c.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(ch -> ch.getId().equals("auto_jump")).count());
    }
    @Test void firstAttemptAndExactIntervalEvenAfterFailure() {
        var clock = new AutoJumpChallenge.Clock();
        assertTrue(clock.ready(600)); clock.attempted(600);
        for (int i=600; i<610; i++) assertFalse(clock.ready(i));
        assertTrue(clock.ready(610)); assertFalse(clock.jumpedThisTick(600));
    }
    @Test void manualJumpRestartsAutoIntervalAndDeduplicatesOnlyItsOwnTick() {
        var clock = new AutoJumpChallenge.Clock(); clock.attempted(10); clock.jumped(13);
        assertTrue(clock.jumpedThisTick(13)); assertFalse(clock.jumpedThisTick(14));
        assertFalse(clock.ready(22)); assertTrue(clock.ready(23));
        assertTrue(new AutoJumpChallenge.Clock().ready(13));
    }
    @Test void serverTickIntegerWrapKeepsElapsedInterval() {
        var clock = new AutoJumpChallenge.Clock(); clock.jumped(Integer.MAX_VALUE-4);
        assertFalse(clock.ready(Integer.MIN_VALUE+4)); assertTrue(clock.ready(Integer.MIN_VALUE+5));
    }
}
