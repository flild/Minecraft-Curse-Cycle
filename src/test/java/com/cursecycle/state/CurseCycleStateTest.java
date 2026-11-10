package com.cursecycle.state;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CurseCycleStateTest {
    @Test
    void readOnlyLookupsDoNotRetainVisitorsOrDirtyTheSave() {
        var state = new CurseCycleState();
        for (int i = 0; i < 10_000; i++)
            assertTrue(state.findPersonalActiveChallengeIds(new UUID(0, i)).isEmpty());
        assertTrue(state.getPersonalActiveChallenges().isEmpty());
        assertFalse(state.isDirty());
        assertTrue(state.writeNbt(new NbtCompound(), null).getCompound("personalActiveChallenges").isEmpty());
    }

    @Test
    void mutableAssignmentsRemainVisibleToLookupAndSurviveNbt() {
        var state = new CurseCycleState();
        var uuid = UUID.randomUUID();
        state.getPersonalActiveChallengeIds(uuid).add("no_sprint");
        state.markDirty();
        assertEquals(List.of("no_sprint"), state.findPersonalActiveChallengeIds(uuid));
        var restored = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
        assertEquals(List.of("no_sprint"), restored.findPersonalActiveChallengeIds(uuid));
        state.getPersonalActiveChallengeIds(uuid).clear();
        assertTrue(state.findPersonalActiveChallengeIds(uuid).isEmpty());
        assertEquals(List.of("no_sprint"), restored.findPersonalActiveChallengeIds(uuid));
    }
    @Test
    void manualSchedulePersistsWithoutClearingHistoryOrBagAndLegacyNbtKeepsZeroOrigin() {
        var state = new CurseCycleState();
        state.addHistory("gills", 4);
        state.getCurrentPool().add("no_jumping");
        state.setNextChallengeId("all_debuffs");
        state.restartCycleAt(119_999);
        assertEquals(5, state.getCurrentDay());
        assertEquals(119_999, state.getTotalInGameTicks());
        assertEquals(1, state.getHistory().size());
        assertEquals(List.of("no_jumping"), state.getCurrentPool());
        assertNull(state.getNextChallengeId());
        assertFalse(state.isSpinTriggeredForCurrentCycle());
        assertEquals(0, state.getLastActivatedCycle());
        assertEquals(-1, state.getLastSpinCycle());
        assertTrue(state.isDirty());
        var tag = state.writeNbt(new NbtCompound(), null);
        var restored = CurseCycleState.createFromNbt(tag, null);
        assertEquals(119_999, restored.getCycleOriginTicks());
        tag.remove("cycleOriginTicks");
        assertEquals(0, CurseCycleState.createFromNbt(tag, null).getCycleOriginTicks());
    }

}
