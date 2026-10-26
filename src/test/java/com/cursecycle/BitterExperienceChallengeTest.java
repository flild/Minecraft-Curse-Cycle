package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.BitterExperienceChallenge;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BitterExperienceChallengeTest {
    @BeforeEach void register() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
    }

    @Test void registeredOnceAndSelectableWithSpecifiedMetadata() {
        var challenge = ChallengeRegistry.get(BitterExperienceChallenge.ID);
        assertInstanceOf(BitterExperienceChallenge.class, challenge);
        assertEquals("Горький опыт", challenge.getName());
        assertEquals("Подбор сферы опыта наносит полсердца урона.", challenge.getShortDescription());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(challenge.getId())).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
        challenge.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
    }

    @Test void doubleAndPersonalActivationPersistWithoutCustomPickupState() {
        var ids = List.of(BitterExperienceChallenge.ID, "no_sprint");
        assertEquals(ids, ChallengeUtils.resolveToActiveIds("double_challenge:bitter_experience,no_sprint"));
        var state = new CurseCycleState();
        var uuid = UUID.randomUUID();
        state.getActiveChallengeIds().addAll(ids);
        state.getPersonalActiveChallengeIds(uuid).add(BitterExperienceChallenge.ID);
        var loaded = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
        assertEquals(ids, loaded.getActiveChallengeIds());
        assertEquals(List.of(BitterExperienceChallenge.ID), loaded.getPersonalActiveChallengeIds(uuid));
        assertTrue(loaded.getChallengeData().isEmpty());
    }
}
