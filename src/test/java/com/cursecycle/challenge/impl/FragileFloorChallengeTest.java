package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FragileFloorChallengeTest {
    @Test void thresholdDuplicatesAndIndependentSupports() {
        var clock = new FragileFloorChallenge.Contacts<String, String>();
        for (int tick = 0; tick < 39; tick++) {
            assertTrue(clock.observe(tick, Map.of("a", "stone", "b", "chest")).isEmpty());
            assertTrue(clock.observe(tick, Map.of("a", "stone", "b", "chest")).isEmpty());
        }
        assertEquals(Map.of("a", "stone", "b", "chest"), clock.observe(39, Map.of("a", "stone", "b", "chest")));
        assertTrue(clock.observe(39, Map.of("a", "stone")).isEmpty());
    }

    @Test void lossTypeReplacementAndMissedTicksRestartOnlyAffectedContact() {
        var clock = new FragileFloorChallenge.Contacts<String, String>();
        for (int tick = 0; tick < 39; tick++) clock.observe(tick, Map.of("a", "stone", "b", "chest"));
        assertEquals(Map.of("b", "chest"), clock.observe(39, Map.of("a", "dirt", "b", "chest")));
        for (int tick = 40; tick < 78; tick++) assertTrue(clock.observe(tick, Map.of("a", "dirt")).isEmpty());
        assertEquals(Map.of("a", "dirt"), clock.observe(78, Map.of("a", "dirt")));
        clock.observe(79, Map.of());
        for (int tick = 80; tick < 119; tick++) assertTrue(clock.observe(tick, Map.of("a", "dirt")).isEmpty());
        assertTrue(clock.observe(120, Map.of("a", "dirt")).isEmpty(), "A skipped server tick interrupts standing");
        assertTrue(clock.observe(24000, Map.of("a", "dirt")).isEmpty(), "Time jumps cannot complete a contact");
    }

    @Test void metadataSelectionAndSavedActivationWithoutTimers() {
        ChallengeRegistry.clear(); ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(FragileFloorChallenge.ID);
        assertInstanceOf(FragileFloorChallenge.class, challenge);
        assertEquals("Хрупкий пол", challenge.getName());
        assertEquals("Стоишь на блоке 2 секунды — он ломается. Любой.", challenge.getShortDescription());
        assertEquals(ChallengeDifficulty.HARD, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.HARD).contains(challenge));
        var ids = List.of(FragileFloorChallenge.ID, "no_sprint");
        assertEquals(ids, ChallengeUtils.resolveToActiveIds("double_challenge:fragile_floor,no_sprint"));
        var state = new CurseCycleState(); state.beginActivation();
        state.getActiveChallengeIds().addAll(ids);
        var uuid = UUID.randomUUID(); state.getPersonalActiveChallengeIds(uuid).add(FragileFloorChallenge.ID);
        var loaded = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
        assertEquals(ids, loaded.getActiveChallengeIds());
        assertEquals(List.of(FragileFloorChallenge.ID), loaded.getPersonalActiveChallengeIds(uuid));
        assertEquals(state.getActivationSerial(), loaded.getActivationSerial());
        assertTrue(loaded.getChallengeData().isEmpty());
    }
}
