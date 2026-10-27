package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.CraftingTaxChallenge;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CraftingTaxChallengeTest {
    @BeforeEach void register() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
    }

    @Test void registeredOnceWithSpecifiedMetadataAndPool() {
        var challenge = ChallengeRegistry.get(CraftingTaxChallenge.ID);
        assertInstanceOf(CraftingTaxChallenge.class, challenge);
        assertEquals("Крафт по талонам", challenge.getName());
        assertEquals("Каждое выполнение рецепта крафта стоит один уровень опыта.", challenge.getShortDescription());
        assertEquals(ChallengeDifficulty.EASY, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(challenge.getId())).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.EASY).contains(challenge));
        challenge.setEnabled(false);
        assertFalse(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.EASY).contains(challenge));
    }

    @Test void doubleAndPersonalActivityRoundTripWithoutCustomTaxState() {
        var ids = List.of(CraftingTaxChallenge.ID, "no_sprint");
        assertEquals(ids, ChallengeUtils.resolveToActiveIds("double_challenge:crafting_tax,no_sprint"));
        var state = new CurseCycleState();
        var uuid = UUID.randomUUID();
        state.beginActivation();
        state.getActiveChallengeIds().addAll(ids);
        state.getPersonalActiveChallengeIds(uuid).add(CraftingTaxChallenge.ID);
        var loaded = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
        assertEquals(ids, loaded.getActiveChallengeIds());
        assertEquals(List.of(CraftingTaxChallenge.ID), loaded.getPersonalActiveChallengeIds(uuid));
        assertEquals(state.getActivationSerial(), loaded.getActivationSerial());
        assertTrue(loaded.getChallengeData().isEmpty());
    }
}
