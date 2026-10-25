package com.cursecycle;

import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.InventoryLockdownChallenge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryLockdownChallengeTest {
    @Test
    void registeredOnceWithRequestedIdentityAndDifficulty() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get("inventory_lockdown");
        assertInstanceOf(InventoryLockdownChallenge.class, challenge);
        assertEquals("Пломба на карманах", challenge.getName());
        assertEquals("Инвентарь заблокирован, работает только хотбар.", challenge.getShortDescription());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM).contains(challenge));
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(challenge.getId())).count());
    }

    @Test
    void locksPlayerInventoryIndicesRatherThanScreenSlotIds() {
        for (int index = 0; index < 9; index++) assertFalse(InventoryLockdownChallenge.isLockedIndex(index));
        for (int index = 9; index <= 40; index++) assertTrue(InventoryLockdownChallenge.isLockedIndex(index));
        assertFalse(InventoryLockdownChallenge.isLockedIndex(-1));
        assertFalse(InventoryLockdownChallenge.isLockedIndex(41));
        assertFalse(InventoryLockdownChallenge.isLockedIndex(100));
    }
}
