package com.cursecycle;

import com.cursecycle.client.ClientDataHolder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientDataHolderTest {
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
