package com.cursecycle.state;

import com.cursecycle.config.CurseCycleConfig;
import com.cursecycle.config.TimingSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimingSettingsTest {
    @Test void rejectsInvalidTimesAndNoticeBeyondInterval() {
        assertTrue(new TimingSettings(1, 0, 0).isValid());
        assertTrue(new TimingSettings(2.5, 0.5, 60).isValid());
        assertTrue(new TimingSettings(0.5, 0.5, 0).isValid());
        assertTrue(new TimingSettings(TimingSettings.MIN_INTERVAL_DAYS, 0, 0).isValid());
        assertTrue(new TimingSettings(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE).isValid());
        for (var settings : new TimingSettings[]{new TimingSettings(0, 0, 60), new TimingSettings(-1, 0, 60),
                new TimingSettings(5, -1, 60), new TimingSettings(5, 6, 60), new TimingSettings(5, 1, -1),
                new TimingSettings(0.5, 0.6, 60), new TimingSettings(0.000001, 0, 60),
                new TimingSettings(Double.NaN, 0, 60), new TimingSettings(Double.POSITIVE_INFINITY, 0, 60),
                new TimingSettings(5, Double.NaN, 60), new TimingSettings(5, Double.POSITIVE_INFINITY, 60),
                new TimingSettings((double) Integer.MAX_VALUE + 1, 0, 60)})
            assertFalse(settings.isValid());
    }

    @Test void fractionalIntervalChangeRestartsCountdownWithoutLosingTheWinner() {
        var state = new CurseCycleState();
        state.restartCycleAt(0);
        state.getActiveChallengeIds().add("no_sprint");
        state.setNextChallengeId("no_jumping");
        var next = new TimingSettings(2.5, 0.5, 60);
        CycleManager.applyTimingChange(state, new TimingSettings(5, 1, 60), next, 119_999);
        var config = new CurseCycleConfig();
        next.applyTo(config);
        assertEquals(60_000, CycleManager.ticksUntilNext(state, config, 119_999));
        assertEquals(12_000, config.noticeTicks());
        assertEquals("no_jumping", state.getNextChallengeId());
    }

    @Test void newIntervalRetainsEffectsHistoryPoolAndPromisedWinner() {
        var state = new CurseCycleState();
        state.restartCycleAt(0);
        state.getActiveChallengeIds().add("no_sprint");
        state.addHistory("no_sprint", 1);
        state.getCurrentPool().add("gills");
        state.setNextChallengeId("no_jumping");
        long serial = state.getActivationSerial();
        var next = new TimingSettings(2, 1, 60);
        CycleManager.applyTimingChange(state, new TimingSettings(5, 1, 60), next, 119_999);
        var config = new CurseCycleConfig(); next.applyTo(config);
        assertEquals(48_000, CycleManager.ticksUntilNext(state, config, 119_999));
        assertEquals(java.util.List.of("no_sprint"), state.getActiveChallengeIds());
        assertEquals("no_jumping", state.getNextChallengeId());
        assertEquals(1, state.getLastSpinCycle());
        assertEquals(1, state.getHistory().size());
        assertEquals(java.util.List.of("gills"), state.getCurrentPool());
        assertEquals(serial, state.getActivationSerial());
    }

    @Test void preparationRestartsBeforeWheelAndNoticeAloneDoesNotRebase() {
        var state = new CurseCycleState();
        state.setInitialPreparationTicks(400);
        var old = new TimingSettings(5, 1, 60);
        var next = new TimingSettings(5, 1, 120);
        CycleManager.applyTimingChange(state, old, next, 0);
        assertEquals(2400, state.getInitialPreparationTicks());
        state.setInitialPreparationTicks(0);
        state.setInitialWheelTicks(44);
        CycleManager.applyTimingChange(state, next, old, 0);
        assertEquals(0, state.getInitialPreparationTicks());
        assertEquals(44, state.getInitialWheelTicks());
        state.restartCycleAt(123);
        CycleManager.applyTimingChange(state, old, new TimingSettings(5, 2, 120), 12_345);
        assertEquals(123, state.getCycleOriginTicks());
    }
}
