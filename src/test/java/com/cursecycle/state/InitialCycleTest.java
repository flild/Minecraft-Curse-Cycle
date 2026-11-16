package com.cursecycle.state;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.config.CurseCycleConfig;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InitialCycleTest {
    private CurseCycleState state;
    private CurseCycleConfig config;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        config = new CurseCycleConfig();
        config.applyChallengeToggles();
        state = new CurseCycleState();
    }

    private boolean advance(int tick, long worldTime, boolean online) {
        return CycleManager.updateInitialCycle(state, config, worldTime, tick, online, id -> {
            events.add("activate:" + id);
            state.beginActivation();
            state.getActiveChallengeIds().addAll(ChallengeUtils.resolveToActiveIds(id));
            state.addHistory(id, state.getCurrentDay());
        }, result -> events.add("announce:" + result.getEncodedWinnerId()));
    }

    @Test
    void firstPlayerGetsOneMinuteThenAFullWheelBeforeActivationAndAFullNormalInterval() {
        advance(0, 900_000, false);
        assertEquals(1200, CycleManager.ticksUntilNext(state, config, 900_000));
        assertTrue(events.isEmpty());
        advance(100, 900_000, true);
        assertEquals(1200, CycleManager.ticksUntilNext(state, config, 900_000));
        for (int tick = 101; tick < 1300; tick++) advance(tick, 900_000, true);
        assertEquals(1, CycleManager.ticksUntilNext(state, config, 900_000));
        assertTrue(events.isEmpty());
        advance(1300, 900_000, true);
        String winner = state.getNextChallengeId();
        assertEquals(List.of("announce:" + winner), events);
        assertTrue(state.getActiveChallengeIds().isEmpty());
        assertEquals(100, CycleManager.ticksUntilNext(state, config, 900_000));
        for (int tick = 1301; tick < 1400; tick++) advance(tick, 900_000, true);
        assertTrue(state.getActiveChallengeIds().isEmpty());
        advance(1400, 12_000_000, true);
        assertEquals(List.of("announce:" + winner, "activate:" + winner), events);
        assertFalse(state.isInitialPreparationPending());
        assertNull(state.getNextChallengeId());
        assertEquals(12_000_000, state.getCycleOriginTicks());
        assertEquals(120_000, CycleManager.ticksUntilNext(state, config, 12_000_000));
        assertFalse(advance(1401, 12_000_001, true), "Normal scheduler takes over after the first activation");
    }

    @Test
    void countdownAndWheelPauseOfflineAndSurviveNbtAndWorldTimeJumps() {
        config.initialPreparationSeconds = 1;
        advance(0, 0, true);
        for (int tick = 1; tick <= 10; tick++) advance(tick, tick, true);
        state = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
        advance(500, 50_000_000, false);
        assertEquals(10, CycleManager.ticksUntilNext(state, config, 50_000_000));
        for (int tick = 501; tick <= 510; tick++) advance(tick, 0, true);
        String winner = state.getNextChallengeId();
        assertEquals(1, events.size());
        for (int tick = 511; tick <= 560; tick++) advance(tick, 0, true);
        state = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
        advance(0, 90_000_000, false);
        assertEquals(50, CycleManager.ticksUntilNext(state, config, 90_000_000));
        for (int tick = 1; tick <= 50; tick++) advance(tick, 123, true);
        assertEquals(List.of("announce:" + winner, "activate:" + winner), events);
        assertEquals(123, state.getCycleOriginTicks());
    }

    @Test
    void repeatedScheduleCallsOnOneServerTickDoNotShortenPreparation() {
        advance(0, 0, true);
        for (int i = 0; i < 100; i++) advance(0, 99_999_999, true);
        assertEquals(1200, CycleManager.ticksUntilNext(state, config, 0));
        advance(1, 0, true);
        assertEquals(1199, CycleManager.ticksUntilNext(state, config, 0));
    }

    @Test
    void legacyWorldsWithActiveChallengesOrHistoryOrScheduleAreNotRestarted() {
        for (int scenario = 0; scenario < 3; scenario++) {
            var saved = new CurseCycleState();
            if (scenario == 0) saved.getActiveChallengeIds().add("no_sprint");
            if (scenario == 1) saved.addHistory("no_sprint", 1);
            if (scenario == 2) saved.setLastActivatedCycle(0);
            var tag = saved.writeNbt(new NbtCompound(), null);
            tag.remove("initialPreparationComplete");
            tag.remove("initialPreparationTicks");
            tag.remove("initialWheelTicks");
            state = CurseCycleState.createFromNbt(tag, null);
            assertFalse(advance(0, 123, true));
            assertEquals(120_000 - 123, CycleManager.ticksUntilNext(state, config, 123));
        }
        assertTrue(events.isEmpty());
    }

    @Test
    void resetRestoresPreparationAndManualActivationBypassesIt() {
        state.restartCycleAt(123);
        assertFalse(advance(0, 123, true));
        state.reset();
        assertTrue(state.isInitialPreparationPending());
        advance(1, 0, true);
        assertEquals(1200, CycleManager.ticksUntilNext(state, config, 0));
        state.beginActivation();
        assertFalse(advance(2, 0, true));
        state = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
        assertFalse(state.isInitialPreparationPending());
    }

    @Test
    void manualSpinBypassesPreparationButStillWaitsForTheWheel() {
        var draw = CycleManager.selectUpcomingChallenge(state, config, 123);
        assertNotNull(draw);
        assertEquals(100, CycleManager.ticksUntilNext(state, config, 123));
        for (int tick = 0; tick < 99; tick++) advance(tick, 123, true);
        assertTrue(events.isEmpty());
        advance(99, 123, true);
        assertEquals(List.of("activate:" + draw.getEncodedWinnerId()), events);
    }

    @Test
    void unavailablePoolWaitsAndDisabledWheelWinnerIsRedrawn() {
        config.initialPreparationSeconds = 0;
        ChallengeRegistry.getAll().forEach(challenge -> challenge.setEnabled(false));
        for (int tick = 0; tick < 20; tick++) advance(tick, tick, true);
        assertTrue(events.isEmpty());
        assertTrue(state.isInitialPreparationPending());
        ChallengeRegistry.get("no_sprint").setEnabled(true);
        advance(20, 20, true);
        assertEquals("no_sprint", state.getNextChallengeId());
        ChallengeRegistry.get("no_sprint").setEnabled(false);
        ChallengeRegistry.get("gills").setEnabled(true);
        advance(21, 21, true);
        assertEquals("gills", state.getNextChallengeId());
        assertTrue(state.getActiveChallengeIds().isEmpty());
        assertEquals(100, CycleManager.ticksUntilNext(state, config, 21));
        for (int tick = 22; tick <= 121; tick++) advance(tick, tick, true);
        assertEquals(List.of("gills"), state.getActiveChallengeIds());
        assertEquals(1, state.getHistory().size());
    }
}
