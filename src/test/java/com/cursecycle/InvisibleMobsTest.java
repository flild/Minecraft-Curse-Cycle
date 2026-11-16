package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.InvisibleMobsChallenge;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.InvisibleMobs;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvisibleMobsTest {
    private static final String ID = InvisibleMobsChallenge.ID;
    @AfterEach void reset() { ClientDataHolder.reset(); }

    @Test void registrationMatchesSpecification() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(ID);
        assertInstanceOf(InvisibleMobsChallenge.class, challenge);
        assertEquals("Их здесь нет", challenge.getName());
        assertEquals("Враждебных мобов не видно, но они всё ещё здесь.", challenge.getShortDescription());
        assertEquals("Враждебные мобы исчезли из виду. Вот только нападать на тебя они не перестали.", challenge.getDetailedRules());
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count());
        assertEquals(List.of(ID, "no_sprint"), ChallengeUtils.resolveToActiveIds("double_challenge:invisible_mobs,no_sprint"));
    }

    @Test void requiresCurrentSyncedLivingSurvivalOrAdventure() {
        assertFalse(InvisibleMobs.shouldHide(null, null));
        for (var mode : GameMode.values()) {
            assertEquals(mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE,
                    InvisibleMobs.affects(true, List.of(ID, ID, "no_sprint"), mode, true));
            assertFalse(InvisibleMobs.affects(false, List.of(ID), mode, true));
            assertFalse(InvisibleMobs.affects(true, List.of(ID), mode, false));
            assertFalse(InvisibleMobs.affects(true, List.of("no_sprint"), mode, true));
        }
        assertFalse(InvisibleMobs.affects(true, List.of(ID), null, true));
        ClientDataHolder.updateState(1, 20, List.of(ID, "no_sprint"), "", List.of());
        assertTrue(active());
        ClientDataHolder.reset();
        assertFalse(active());
        ClientDataHolder.updateState(2, 40, List.of("no_sprint"), "", List.of(ID));
        assertFalse(active());
    }

    private boolean active() {
        return InvisibleMobs.affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(), GameMode.SURVIVAL, true);
    }
}
