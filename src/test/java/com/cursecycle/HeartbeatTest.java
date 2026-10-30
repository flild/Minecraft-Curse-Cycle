package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.HeartbeatChallenge;
import com.cursecycle.challenge.impl.HeartbeatChallenge.Rhythm;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HeartbeatTest {
    @Test void registrationMatchesSpecification() {
        ChallengeRegistry.clear(); ModChallenges.registerAll();
        var c = ChallengeRegistry.get("heartbeat");
        assertInstanceOf(HeartbeatChallenge.class, c);
        assertEquals("Сердце на ручном приводе", c.getName());
        assertEquals("Нажимай B в ритм: пропуск удара наносит урон.", c.getShortDescription());
        assertEquals("Сердце работает на ручном приводе: нажимай назначенную клавишу удара B раз в секунду в отмеченный момент. После 5 секунд подготовки каждый пропущенный удар наносит 2 единицы урона. Допуск — 5 серверных тиков в каждую сторону; ранние и лишние нажатия просто не засчитываются.", c.getDetailedRules());
        assertEquals(ChallengeDifficulty.HARD, c.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, c.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(ch -> ch.getId().equals("heartbeat")).count());
        assertEquals(List.of("heartbeat", "no_sprint"), ChallengeUtils.resolveToActiveIds("double_challenge:heartbeat,no_sprint"));
    }
    @Test void inclusiveEdgesAndOnePressPerWindow() {
        for (int tick = 0; tick <= 126; tick++) {
            var r = new Rhythm();
            assertEquals(tick >= 115 && tick <= 125, r.press(tick), "tick " + tick);
            if (r.accepted()) assertFalse(r.press(tick));
        }
    }
    @Test void preparationAndSinglePenaltyAt126Then146() {
        var r = new Rhythm();
        for (int tick = 0; tick <= 125; tick++) assertFalse(r.advance(tick));
        assertTrue(r.advance(126)); assertEquals(140, r.target());
        for (int i = 0; i < 100; i++) assertFalse(r.advance(126));
        for (int tick = 127; tick <= 145; tick++) assertFalse(r.advance(tick));
        assertTrue(r.advance(146)); assertEquals(160, r.target());
    }
    @Test void acceptedBeatCannotCoverNextAndWrongPressDoesNotReserveIt() {
        var r = new Rhythm();
        assertFalse(r.press(114)); assertTrue(r.press(115));
        assertFalse(r.advance(126)); assertFalse(r.accepted());
        assertFalse(r.press(126)); assertFalse(r.press(134));
        assertTrue(r.advance(146));
        assertFalse(r.press(146)); assertTrue(r.press(155));
        assertFalse(r.advance(166));
    }
    @Test void skippedTicksNeverQueuePenaltiesOrDuplicateRequests() {
        var r = new Rhythm();
        assertTrue(r.advance(1_000_000));
        assertTrue(r.target() + 5 >= 1_000_000);
        assertFalse(r.advance(1_000_000));
        assertFalse(r.advance(1_000_001));
        var fresh = new Rhythm();
        assertEquals(120, fresh.target()); assertFalse(fresh.accepted());
    }
    @Test void independentPhasesAndServerTimeOnly() {
        var first = new Rhythm(); var late = new Rhythm();
        assertTrue(first.press(125)); assertFalse(late.press(105));
        assertFalse(first.advance(126)); assertFalse(late.advance(106));
        assertTrue(late.press(115)); assertFalse(late.advance(126));
        for (int i = 0; i < 1000; i++) assertFalse(first.advance(126));
        assertEquals(140, first.target());
    }
}
