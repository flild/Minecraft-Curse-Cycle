package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.HeavyBodyChallenge;
import com.cursecycle.client.HeavyBodyInput;
import com.cursecycle.network.HeavyBodyPayload;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class HeavyBodyTest {
    @Test void registration() {
        ChallengeRegistry.clear(); ModChallenges.registerAll();
        var c = ChallengeRegistry.get("heavy_body");
        assertInstanceOf(HeavyBodyChallenge.class, c);
        assertEquals("На массе", c.getName());
        assertEquals("Бегаешь медленнее, прыгаешь ниже и запыхиваешься.", c.getShortDescription());
        assertEquals(ChallengeDifficulty.EASY, c.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, c.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(ch -> ch.getId().equals("heavy_body")).count());
    }
    @Test void exactStartIntervalAndRest() {
        var b = new HeavyBodyChallenge.Breathing();
        for (int i = 1; i < 100; i++) assertFalse(b.tick(true));
        assertTrue(b.tick(true)); assertTrue(b.panting());
        for (int i = 1; i < 80; i++) assertFalse(b.tick(true));
        assertTrue(b.tick(true));
        for (int i = 1; i < 40; i++) { assertFalse(b.tick(false)); assertTrue(b.panting()); }
        assertFalse(b.tick(false)); assertFalse(b.panting());
        for (int i = 1; i < 100; i++) assertFalse(b.tick(true));
        assertTrue(b.tick(true));
    }
    @Test void resetBeforeThresholdAndShortRest() {
        var b = new HeavyBodyChallenge.Breathing();
        for (int i = 0; i < 99; i++) assertFalse(b.tick(true));
        assertFalse(b.tick(false));
        for (int i = 0; i < 99; i++) assertFalse(b.tick(true));
        assertTrue(b.tick(true));
        for (int i = 0; i < 39; i++) assertFalse(b.tick(false));
        for (int i = 0; i < 40; i++) assertFalse(b.tick(true));
        assertTrue(b.tick(true));
    }
    @Test void intentTransitionsAndLifecycle() {
        var i = new HeavyBodyInput.Intent(); var packets = new ArrayList<HeavyBodyPayload>();
        Object p = new Object(), w = new Object();
        i.report(p, w, true, packets::add); assertTrue(packets.isEmpty());
        i.context(7); i.report(p, w, true, packets::add);
        for (int n = 0; n < 500; n++) { i.context(7); i.report(p, w, true, packets::add); }
        assertEquals(1, packets.size());
        i.report(p, w, false, packets::add); assertFalse(packets.getLast().moving());
        i.report(p, w, true, packets::add);
        i.report(p, new Object(), true, packets::add);
        i.report(new Object(), w, true, packets::add);
        assertEquals(5, packets.size());
        i.context(8); i.report(p, w, true, packets::add);
        assertEquals(new HeavyBodyPayload(8, true), packets.getLast());
        i.reset(); i.report(p, w, true, packets::add); assertEquals(6, packets.size());
    }
}
