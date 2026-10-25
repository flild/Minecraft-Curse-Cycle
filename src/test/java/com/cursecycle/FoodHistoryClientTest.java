package com.cursecycle;

import com.cursecycle.client.ClientDataHolder;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FoodHistoryClientTest {
    @Test void authoritativeHistoryAndDisconnectReset() {
        ClientDataHolder.reset();
        ClientDataHolder.updateState(1, 10, List.of("one_time_menu"), "", List.of());
        ClientDataHolder.updateFoodHistory(List.of("minecraft:bread"));
        assertTrue(ClientDataHolder.hasEatenFood("minecraft:bread"));
        assertFalse(ClientDataHolder.hasEatenFood("minecraft:beef"));
        ClientDataHolder.updateState(1, 10, List.of("no_sprint"), "", List.of());
        assertFalse(ClientDataHolder.hasEatenFood("minecraft:bread"));
        ClientDataHolder.updateFoodHistory(List.of("minecraft:bread"));
        ClientDataHolder.reset();
        assertFalse(ClientDataHolder.hasEatenFood("minecraft:bread"));
    }
}
