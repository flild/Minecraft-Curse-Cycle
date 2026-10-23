package com.cursecycle.state;

import com.cursecycle.challenge.*;
import com.cursecycle.config.CurseCycleConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CycleManagerTest {
    private CurseCycleState state;
    private CurseCycleConfig config;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        state = new CurseCycleState();
        config = new CurseCycleConfig();
    }

    private void advance(long time) {
        CycleManager.updateCycle(state, config, time, id -> {
            events.add("activate:" + id);
            state.getActiveChallengeIds().clear();
            state.getActiveChallengeIds().addAll(ChallengeUtils.resolveToActiveIds(id));
            state.addHistory(id, state.getCurrentDay());
        }, result -> events.add("announce:" + result.getEncodedWinnerId()));
    }

    @Test
    void timeJumpActivatesThePromisedChallengeBeforeAnnouncingTheNextOne() {
        advance(0);
        advance(96000);
        String promised = state.getNextChallengeId();
        events.clear();
        advance(216000);
        assertEquals(2, events.size());
        assertEquals("activate:" + promised, events.get(0));
        String following = state.getNextChallengeId();
        assertNotNull(following);
        assertEquals("announce:" + following, events.get(1));
        assertEquals(1, state.getLastActivatedCycle());
        assertEquals(2, state.getLastSpinCycle());
        events.clear();
        advance(240000);
        assertEquals(List.of("activate:" + following), events);
        assertNull(state.getNextChallengeId());
    }

    @Test
    void manualSpinSuppressesTheAutomaticDrawForTheSameCycle() {
        advance(0);
        var forced = CycleManager.selectUpcomingChallenge(state, config, 48000);
        assertNotNull(forced);
        assertEquals(1, state.getLastSpinCycle());
        events.clear();
        advance(96000);
        advance(100000);
        assertTrue(events.isEmpty());
        assertEquals(forced.getEncodedWinnerId(), state.getNextChallengeId());
        advance(120000);
        assertEquals(List.of("activate:" + forced.getEncodedWinnerId()), events);
    }

    @Test
    void resetClearsMarkersAndPersonalStateAndStartsANewFirstCycle() {
        advance(456000);
        state.getPersonalActiveChallengeIds(UUID.randomUUID()).add("no_sprint");
        state.reset();
        assertEquals(-1, state.getLastActivatedCycle());
        assertEquals(-1, state.getLastSpinCycle());
        assertEquals(1, state.getCurrentDay());
        assertEquals(0, state.getTotalInGameTicks());
        assertTrue(state.getPersonalActiveChallenges().isEmpty());
        assertTrue(state.getCurrentPool().isEmpty());
        assertTrue(state.getHistory().isEmpty());
        assertNull(state.getNextChallengeId());
        assertNull(state.getLastDrawnChallengeId());
        events.clear();
        advance(0);
        assertEquals(1, events.size());
        assertEquals(1, state.getHistory().size());
        assertFalse(state.getActiveChallengeIds().isEmpty());
        assertEquals(0, state.getLastActivatedCycle());
    }

    @Test
    void frozenClockAndReloadDoNotRepeatAnnouncementsOrActivations() {
        advance(96000);
        var nbt = state.writeNbt(new net.minecraft.nbt.NbtCompound(), null);
        state = CurseCycleState.createFromNbt(nbt, null);
        events.clear();
        for (int i = 0; i < 100; i++) advance(96000);
        assertTrue(events.isEmpty());
        assertNotNull(state.getNextChallengeId());
    }

    @Test
    void disabledSavedChallengesAreReplacedWithoutWaitingForTheNextCycle() {
        state.getActiveChallengeIds().add("no_sprint");
        state.setLastActivatedCycle(0);
        ChallengeRegistry.get("no_sprint").setEnabled(false);
        advance(1000);
        assertFalse(state.getActiveChallengeIds().contains("no_sprint"));
        assertFalse(state.getActiveChallengeIds().isEmpty());
        assertEquals(1, events.size());
    }

    @Test
    void disabledPendingChallengeIsNotActivatedAfterRestart() {
        state.setLastActivatedCycle(0);
        state.setLastSpinCycle(1);
        state.setNextChallengeId("no_jumping");
        ChallengeRegistry.get("no_jumping").setEnabled(false);
        advance(120000);
        assertFalse(state.getActiveChallengeIds().contains("no_jumping"));
        assertFalse(state.getActiveChallengeIds().isEmpty());
        assertNull(state.getNextChallengeId());
    }
}
