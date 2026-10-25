package com.cursecycle;

import com.cursecycle.challenge.impl.GillsChallenge.AirSupply;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GillsChallengeTest {
    @Test void depletionAndDamageBoundary() {
        var air = new AirSupply(300, 0);
        for (int i = 0; i < 300; i++) assertFalse(air.step(false));
        assertEquals(0, air.air);
        for (int i = 0; i < 19; i++) assertFalse(air.step(false));
        assertTrue(air.step(false));
        assertEquals(0, air.dryTicks);
    }
    @Test void waterRecoveryAndPartialPhase() {
        var air = new AirSupply(0, 19);
        assertFalse(air.step(true));
        assertEquals(4, air.air);
        assertEquals(0, air.dryTicks);
        for (int i = 0; i < 100; i++) air.step(true);
        assertEquals(300, air.air);
        var saved = new AirSupply(0, 19);
        assertTrue(saved.step(false));
    }
}
