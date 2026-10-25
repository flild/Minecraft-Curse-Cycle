package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdhdNoStandingStillChallengeTest {
    private static final BlockPos ORIGIN = new BlockPos(-1, 64, -1);

    @Test
    void registrationDoubleChallengeAndSavedSelection() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(AdhdNoStandingStillChallenge.ID);
        assertInstanceOf(AdhdNoStandingStillChallenge.class, challenge);
        assertEquals("У тебя СДВГ?", challenge.getName());
        assertEquals(ChallengeDifficulty.HARD, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.HARD).contains(challenge));
        challenge.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.HARD).contains(challenge));
        challenge.setEnabled(true);
        var ids = List.of(AdhdNoStandingStillChallenge.ID, "no_sprint");
        assertEquals(ids, ChallengeUtils.resolveToActiveIds("double_challenge:adhd_no_standing_still,no_sprint"));
        var state = new CurseCycleState();
        state.getActiveChallengeIds().addAll(ids);
        var restored = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
        assertEquals(ids, restored.getActiveChallengeIds());
    }

    @Test
    void strictThresholdAndBoundedCadenceWithEscalatingDuration() {
        var tracker = new AdhdNoStandingStillChallenge.StandingTracker();
        assertEquals(0, observe(tracker, 0));
        for (int tick = 1; tick <= 60; tick++) assertEquals(0, observe(tracker, tick));
        assertEquals(200, observe(tracker, 61));
        assertEquals(0, observe(tracker, 61), "Duplicate dispatch cannot stack a penalty");
        for (int tick = 62; tick <= 121; tick++) assertEquals(0, observe(tracker, tick));
        assertEquals(400, observe(tracker, 122));
        assertEquals(600, observe(tracker, 183));
    }

    @Test
    void blockChangesResetTheClockButKeepAccumulation() {
        var tracker = new AdhdNoStandingStillChallenge.StandingTracker();
        observe(tracker, 0);
        assertEquals(200, observe(tracker, 61));
        BlockPos next = ORIGIN.east();
        assertEquals(0, tracker.observe(100, 1, "overworld", next, 64));
        assertEquals(0, tracker.observe(160, 1, "overworld", next, 64));
        assertEquals(400, tracker.observe(161, 1, "overworld", next, 64));
        assertEquals(0, observe(tracker, 162), "Returning to the previous block starts a fresh timer");
        assertEquals(0, observe(tracker, 222));
        assertEquals(600, observe(tracker, 223));
    }

    @Test
    void verticalFallingWithinOneBlockAndJumpingDoNotCountAsStanding() {
        var tracker = new AdhdNoStandingStillChallenge.StandingTracker();
        for (int tick = 0; tick <= 200; tick++) {
            assertEquals(0, tracker.observe(tick, 1, "overworld", ORIGIN, 64.9 - tick * 0.001));
        }
        for (int tick = 201; tick <= 400; tick++) {
            assertEquals(0, tracker.observe(tick, 1, "overworld", ORIGIN, 64.7 + (tick - 200) * 0.001));
        }
        assertEquals(0, tracker.observe(460, 1, "overworld", ORIGIN, 64.9));
        assertEquals(200, tracker.observe(461, 1, "overworld", ORIGIN, 64.9));
    }

    @Test
    void lifecycleResetsOnlyPositionAndPlayersRemainIndependent() {
        var first = new AdhdNoStandingStillChallenge.StandingTracker();
        var second = new AdhdNoStandingStillChallenge.StandingTracker();
        observe(first, 0);
        assertEquals(200, observe(first, 61));
        observe(second, 61);
        first.resetPosition(); // Exempt modes, vehicles, death and disconnect.
        first.resetPosition();
        assertEquals(0, observe(first, 100));
        assertEquals(0, observe(first, 160));
        assertEquals(400, observe(first, 161));
        assertEquals(200, observe(second, 122));
        assertEquals(0, first.observe(162, 2, "overworld", ORIGIN, 64), "Respawn creates a new entity");
        assertEquals(0, first.observe(222, 2, "overworld", ORIGIN, 64));
        assertEquals(600, first.observe(223, 2, "overworld", ORIGIN, 64));
        assertEquals(0, first.observe(224, 2, "nether", ORIGIN, 64), "Even identical coordinates in another dimension reset");
        assertEquals(0, first.observe(284, 2, "nether", ORIGIN, 64));
        assertEquals(800, first.observe(285, 2, "nether", ORIGIN, 64));
    }

    @Test
    void tickCounterWrapDoesNotDelayThePenalty() {
        var tracker = new AdhdNoStandingStillChallenge.StandingTracker();
        int start = Integer.MAX_VALUE - 30;
        observe(tracker, start);
        assertEquals(0, observe(tracker, start + 60));
        assertEquals(200, observe(tracker, start + 61));
    }

    private static int observe(AdhdNoStandingStillChallenge.StandingTracker tracker, int tick) {
        return tracker.observe(tick, 1, "overworld", ORIGIN, 64);
    }
}
