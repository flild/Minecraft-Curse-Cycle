package com.cursecycle.network;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.InventoryLockdownChallenge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkHandlerTest {
    @Test
    void keyboardInputFollowsServerEnabledGlobalsAndRecipientPersonalIds() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var id = "inverted_keyboard";
        var globals = List.of(id, "no_sprint", id);
        assertEquals(List.of(id, "no_sprint"), NetworkHandler.getEffectiveActiveIds(globals, List.of(id)));
        var challenge = ChallengeRegistry.get(id);
        try {
            challenge.setEnabled(false);
            assertEquals(List.of("no_sprint"), NetworkHandler.getEffectiveActiveIds(globals, List.of(id)));
            challenge.setEnabled(true);
            assertEquals(List.of("no_sprint", id), NetworkHandler.getEffectiveActiveIds(List.of("no_sprint"), List.of(id)));
            assertEquals(List.of("no_sprint"), NetworkHandler.getEffectiveActiveIds(List.of("no_sprint"), List.of()));
        } finally {
            challenge.setEnabled(true);
        }
    }

    @Test
    void personalPredictionIdsAreAddedOnlyToTheirRecipientWithoutDuplicates() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var global = List.of("no_sprint");
        var ownerIds = List.of("no_sprint", InventoryLockdownChallenge.ID, "missing_challenge");
        assertEquals(List.of("no_sprint", InventoryLockdownChallenge.ID), NetworkHandler.getEffectiveActiveIds(global, ownerIds));
        assertEquals(global, NetworkHandler.getEffectiveActiveIds(global, List.of()));
        var challenge = ChallengeRegistry.get(InventoryLockdownChallenge.ID);
        try {
            challenge.setEnabled(false);
            assertEquals(global, NetworkHandler.getEffectiveActiveIds(global, ownerIds));
        } finally {
            challenge.setEnabled(true);
        }
    }
}
