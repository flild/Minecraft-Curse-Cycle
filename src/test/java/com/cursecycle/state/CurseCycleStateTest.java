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
}
