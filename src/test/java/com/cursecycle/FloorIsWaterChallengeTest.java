package com.cursecycle;

import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.FloorIsWaterChallenge;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FloorIsWaterChallengeTest {
    @BeforeEach
    void register() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
    }

    @Test
    void registeredAsMediumGlobalChallenge() {
        var challenge = ChallengeRegistry.get("floor_is_water");
        assertInstanceOf(FloorIsWaterChallenge.class, challenge);
        assertEquals("Водобоязнь", challenge.getName());
        assertEquals("Касание воды приводит к мгновенной смерти.", challenge.getShortDescription());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
        challenge.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
    }

    @Test
    void resolvesAlongsideAnotherChallenge() {
        assertEquals(List.of("floor_is_water", "no_sprint"),
                ChallengeUtils.resolveToActiveIds("double_challenge:floor_is_water,no_sprint"));
    }

    @Test
    void activeSelectionSurvivesSharedStateSerialization() {
        var state = new CurseCycleState();
        state.getActiveChallengeIds().addAll(List.of(FloorIsWaterChallenge.ID, "no_sprint"));
        state.setLastActivatedCycle(2);
        state.addHistory("double_challenge:floor_is_water,no_sprint", 11);
        UUID player = UUID.randomUUID();
        state.getPersonalActiveChallengeIds(player).add(FloorIsWaterChallenge.ID);

        var restored = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
        assertEquals(state.getActiveChallengeIds(), restored.getActiveChallengeIds());
        assertEquals(List.of(FloorIsWaterChallenge.ID), restored.getPersonalActiveChallengeIds(player));
        assertEquals(2, restored.getLastActivatedCycle());
        assertEquals("double_challenge:floor_is_water,no_sprint", restored.getHistory().getFirst().challengeId);
    }
}
