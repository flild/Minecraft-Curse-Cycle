package com.cursecycle;

import com.cursecycle.client.ClientDataHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientDataHolderTest {
    @AfterEach
    void clearClientState() { ClientDataHolder.reset(); }

    @Test
    void stateSyncCannotRevealWinnerUntilTheWheelFinishes() {
        ClientDataHolder.updateState(4, 1000, List.of("no_sprint"), "old_winner", List.of("no_sprint"));
        long wheel = ClientDataHolder.beginWheel();
        assertEquals("", ClientDataHolder.getNextChallengeId());
        for (int tick = 0; tick < 100; tick++) {
            ClientDataHolder.updateState(4, 1000 - tick, List.of("no_sprint"),
                    "double_challenge:no_jumping,no_sprint", List.of("no_sprint"));
            assertEquals("", ClientDataHolder.getNextChallengeId(), "sync during spin at tick " + tick);
            assertEquals(List.of("no_sprint"), ClientDataHolder.getActiveChallengeIds());
        }
        ClientDataHolder.finishWheel(wheel);
        assertEquals("double_challenge:no_jumping,no_sprint", ClientDataHolder.getNextChallengeId());
    }

    @Test
    void hiddenFirstWinnerDoesNotTurnActivationCountdownBackIntoPreparation() {
        ClientDataHolder.updateState(1, 1200, List.of(), "", List.of());
        assertFalse(ClientDataHolder.isWaitingForFirstActivation());
        long wheel = ClientDataHolder.beginWheel();
        ClientDataHolder.updateState(1, 100, List.of(), "no_sprint", List.of());
        assertEquals("", ClientDataHolder.getNextChallengeId());
        assertTrue(ClientDataHolder.isWaitingForFirstActivation());
        ClientDataHolder.finishWheel(wheel);
        assertEquals("no_sprint", ClientDataHolder.getNextChallengeId());
        ClientDataHolder.updateState(1, 120_000, List.of("no_sprint"), "", List.of("no_sprint"));
        assertFalse(ClientDataHolder.isWaitingForFirstActivation());
    }

    @Test
    void removingAnOldWheelCannotRevealTheReplacementOrANewConnection() {
        long oldWheel = ClientDataHolder.beginWheel();
        long replacement = ClientDataHolder.beginWheel();
        ClientDataHolder.updateState(1, 100, List.of(), "no_jumping", List.of());
        ClientDataHolder.finishWheel(oldWheel);
        assertEquals("", ClientDataHolder.getNextChallengeId());
        ClientDataHolder.finishWheel(replacement);
        assertEquals("no_jumping", ClientDataHolder.getNextChallengeId());

        ClientDataHolder.reset();
        long newConnectionWheel = ClientDataHolder.beginWheel();
        ClientDataHolder.updateState(1, 100, List.of(), "no_sprint", List.of());
        ClientDataHolder.finishWheel(replacement);
        assertEquals("", ClientDataHolder.getNextChallengeId());
        ClientDataHolder.finishWheel(newConnectionWheel);
        assertEquals("no_sprint", ClientDataHolder.getNextChallengeId());
    }

    @Test
    void withoutAnimationAndAfterDisconnectUpcomingChallengesAreVisibleNormally() {
        ClientDataHolder.updateState(4, 100, List.of("no_sprint"), "no_jumping", List.of("no_sprint"));
        assertEquals("no_jumping", ClientDataHolder.getNextChallengeId());
        ClientDataHolder.beginWheel();
        ClientDataHolder.reset();
        ClientDataHolder.updateState(4, 100, List.of("no_sprint"), "no_jumping", List.of("no_sprint"));
        assertEquals("no_jumping", ClientDataHolder.getNextChallengeId());
    }

    @Test
    void preparationEndsWhenFirstChallengeArrivesAndDisconnectClearsThePhase() {
        ClientDataHolder.updateState(1, 1200, List.of(), "", List.of());
        assertTrue(ClientDataHolder.isPreparingInitialChallenge());
        ClientDataHolder.updateState(1, 100, List.of(), "no_sprint", List.of());
        assertTrue(ClientDataHolder.isPreparingInitialChallenge());
        ClientDataHolder.updateState(1, 120_000, List.of("no_sprint"), "", List.of("no_sprint"));
        assertFalse(ClientDataHolder.isPreparingInitialChallenge());
        ClientDataHolder.reset();
        assertFalse(ClientDataHolder.isPreparingInitialChallenge());
    }

    @Test
    void disconnectClearsRestrictionsAndAllDataFromThePreviousServer() {
        ClientDataHolder.updateState(20, 100, List.of("no_sprint", "no_jumping"),
                "test_easy", List.of("test_hard"));
        assertTrue(ClientDataHolder.isSynced());
        ClientDataHolder.reset();
        assertFalse(ClientDataHolder.isSynced());
        assertTrue(ClientDataHolder.getActiveChallengeIds().isEmpty());
        assertTrue(ClientDataHolder.getHistoryChallengeIds().isEmpty());
        assertEquals("", ClientDataHolder.getNextChallengeId());
        assertEquals(1, ClientDataHolder.getCurrentDay());
        assertEquals(0, ClientDataHolder.getTicksUntilNext());
    }
}
