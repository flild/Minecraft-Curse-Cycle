package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.LockedContainersChallenge;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LockedContainersChallengeTest {
    @BeforeEach void register() { ChallengeRegistry.clear(); ModChallenges.registerAll(); }

    @Test void metadataRegistrationAndPool() {
        var c = ChallengeRegistry.get(LockedContainersChallenge.ID);
        assertInstanceOf(LockedContainersChallenge.class, c);
        assertEquals("Сундуки не для тебя", c.getName());
        assertEquals("Сундуки, шалкеры и эндер-сундуки недоступны.", c.getShortDescription());
        assertEquals(ChallengeDifficulty.MEDIUM, c.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, c.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(x -> x.getId().equals(c.getId())).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(c));
        c.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(c));
    }

    @Test void globalPersonalAndDoubleActivationPersistWithoutStorageSnapshots() {
        var ids = List.of(LockedContainersChallenge.ID, "no_sprint");
        assertEquals(ids, ChallengeUtils.resolveToActiveIds("double_challenge:locked_containers,no_sprint"));
        var s = new CurseCycleState();
        var uuid = UUID.randomUUID();
        s.beginActivation();
        s.getActiveChallengeIds().addAll(ids);
        s.getPersonalActiveChallengeIds(uuid).add(LockedContainersChallenge.ID);
        var loaded = CurseCycleState.createFromNbt(s.writeNbt(new NbtCompound(), null), null);
        assertEquals(ids, loaded.getActiveChallengeIds());
        assertEquals(List.of(LockedContainersChallenge.ID), loaded.getPersonalActiveChallengeIds(uuid));
        assertTrue(loaded.getChallengeData().isEmpty());
    }
}
