package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.HeavyBodyChallenge;
import com.cursecycle.client.HeavyBodyInput;
import com.cursecycle.network.HeavyBodyPayload;
import com.cursecycle.network.HeavyBodyStatePayload;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class HeavyBodyTest {
    @Test void registration() {
        ChallengeRegistry.clear(); ModChallenges.registerAll();
        var c = ChallengeRegistry.get("heavy_body");
        assertInstanceOf(HeavyBodyChallenge.class, c);
        assertFalse(c.getName().isBlank());
        assertFalse(c.getShortDescription().isBlank());
        assertEquals(ChallengeDifficulty.MEDIUM, c.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, c.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(ch -> ch.getId().equals("heavy_body")).count());
    }
    @Test void exactStartIntervalAndRest() {
        var b = new HeavyBodyChallenge.Breathing();
        for (int i = 1; i < 50; i++) { assertFalse(b.tick(true)); assertEquals(50-i, b.stamina()); }
        assertTrue(b.tick(true)); assertTrue(b.panting());
        assertEquals(0, b.stamina()); assertEquals(HeavyBodyChallenge.RECOVERY_TICKS, b.recoveryTicks());
        for (int i = 1; i < HeavyBodyChallenge.REGEN_DELAY_TICKS; i++) { assertFalse(b.tick(true)); assertTrue(b.panting()); }
        assertFalse(b.tick(true));
        assertEquals(0, b.stamina());
        int regenTicks = HeavyBodyChallenge.RECOVERY_TICKS - HeavyBodyChallenge.REGEN_DELAY_TICKS;
        for (int i = 1; i < regenTicks; i++) {
            assertFalse(b.tick(false)); assertTrue(b.panting()); assertEquals(i * 50 / regenTicks, b.stamina());
        }
        assertFalse(b.tick(false)); assertFalse(b.panting());
        assertEquals(50, b.stamina());
        for (int i = 1; i < 50; i++) assertFalse(b.tick(true));
        assertTrue(b.tick(true));
    }
    @Test void partialRestRecoversInsteadOfResettingAndWalkingNeverExhausts() {
        var b = new HeavyBodyChallenge.Breathing();
        for (int i = 0; i < 1000; i++) assertFalse(b.tick(false));
        assertEquals(50, b.stamina());
        for (int i = 0; i < 25; i++) assertFalse(b.tick(true));
        assertEquals(25, b.stamina());
        for (int i = 0; i < HeavyBodyChallenge.REGEN_DELAY_TICKS; i++) {
            assertFalse(b.tick(false)); assertEquals(25, b.stamina());
        }
        for (int i = 0; i < 10; i++) assertFalse(b.tick(false));
        assertEquals(45, b.stamina());
        for (int i = 0; i < 44; i++) assertFalse(b.tick(true));
        assertTrue(b.tick(true));
        for (int i = 1; i <= HeavyBodyChallenge.RECOVERY_TICKS; i++) {
            b.tick(true); assertEquals(i < HeavyBodyChallenge.RECOVERY_TICKS, b.panting());
            assertEquals(Math.max(0, (i - HeavyBodyChallenge.REGEN_DELAY_TICKS) * 50
                    / (HeavyBodyChallenge.RECOVERY_TICKS - HeavyBodyChallenge.REGEN_DELAY_TICKS)), b.stamina());
        }
    }
    @Test void spendingRestartsDelayAndNeverRegeneratesBetweenSprintSamples() {
        var b = new HeavyBodyChallenge.Breathing();
        for (int spent = 1; spent <= 40; spent++) {
            b.tick(true);
            assertEquals(50 - spent, b.stamina());
            for (int rest = 0; rest < HeavyBodyChallenge.REGEN_DELAY_TICKS; rest++) {
                b.tick(false);
                assertEquals(50 - spent, b.stamina(), "Short rests must not refill stamina");
            }
        }
        b.tick(false); assertEquals(12, b.stamina());
        b.tick(true); assertEquals(11, b.stamina());
        for (int i = 0; i < HeavyBodyChallenge.REGEN_DELAY_TICKS; i++) b.tick(false);
        assertEquals(11, b.stamina());
        b.tick(false); assertEquals(13, b.stamina());
    }
    @Test void clientStateRejectsStaleActivationAndFadesWithoutUnlockingEarly() {
        var s = new HeavyBodyInput.Stamina();
        assertFalse(s.receive(new HeavyBodyStatePayload(7, 0, HeavyBodyChallenge.RECOVERY_TICKS)));
        s.context(7); assertTrue(s.receive(new HeavyBodyStatePayload(7, 0, HeavyBodyChallenge.RECOVERY_TICKS)));
        for (int i = 0; i < 500; i++) s.tick();
        assertTrue(s.exhausted()); assertTrue(s.visible()); assertEquals(.6F, s.darkness(), .00001);
        assertFalse(s.receive(new HeavyBodyStatePayload(6, 50, 0))); assertTrue(s.exhausted());
        s.context(7); assertTrue(s.exhausted());
        assertTrue(s.receive(new HeavyBodyStatePayload(7, 50, 0)));
        for (int i = 0; i < 5; i++) s.tick();
        assertFalse(s.exhausted()); assertFalse(s.visible()); assertEquals(0, s.darkness());
        s.receive(new HeavyBodyStatePayload(7, -1, 500)); assertEquals(0, s.value());
        s.context(8); assertFalse(s.exhausted()); assertEquals(50, s.value());
        s.clearSample(); assertTrue(s.receive(new HeavyBodyStatePayload(8, 45, 0)));
        assertEquals(45, s.value());
        s.receive(new HeavyBodyStatePayload(8, 500, -1));
        assertEquals(50, s.value()); assertFalse(s.exhausted()); assertFalse(s.visible());
        s.reset(); assertFalse(s.receive(new HeavyBodyStatePayload(8, 0, 40)));
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
