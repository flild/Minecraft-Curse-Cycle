package com.cursecycle;

import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.InvertedKeyboardChallenge;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.InvertedKeyboardInput;
import net.minecraft.client.input.Input;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InvertedKeyboardInputTest {
    private static final String ID = InvertedKeyboardChallenge.ID;
    @AfterEach void resetClient() { ClientDataHolder.reset(); }

    @Test void registrationAndCompositeUseExactSpecification() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(ID);
        assertInstanceOf(InvertedKeyboardChallenge.class, challenge);
        assertEquals("Да как ходить то!", challenge.getName());
        assertEquals("Вперёд и назад, влево и вправо поменялись местами.", challenge.getShortDescription());
        assertEquals(ChallengeDifficulty.HARD, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals(1L, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count());
        assertTrue(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.HARD).contains(challenge));
        assertEquals(List.of(ID, "no_sprint"),
                ChallengeUtils.resolveToActiveIds("double_challenge:inverted_keyboard,no_sprint"));
    }

    @Test void directionPairsAndVehicleFlagsInvertWhileOtherInputAndMagnitudeStayUnchanged() {
        // All 16 directional combinations, including opposite keys and both diagonals.
        for (int mask = 0; mask < 16; mask++) {
            for (float scale : new float[]{1, .3f, .2f}) {
                var input = new Input();
                boolean forward = input.pressingForward = (mask & 1) != 0;
                boolean back = input.pressingBack = (mask & 2) != 0;
                boolean left = input.pressingLeft = (mask & 4) != 0;
                boolean right = input.pressingRight = (mask & 8) != 0;
                float f = input.movementForward = ((forward ? 1 : 0) - (back ? 1 : 0)) * scale;
                float s = input.movementSideways = ((left ? 1 : 0) - (right ? 1 : 0)) * scale;
                input.jumping = true;
                input.sneaking = true;
                InvertedKeyboardInput.invert(input);
                assertEquals(-f, input.movementForward);
                assertEquals(-s, input.movementSideways);
                assertEquals(f * f + s * s, input.movementForward * input.movementForward
                        + input.movementSideways * input.movementSideways);
                assertEquals(back, input.pressingForward);
                assertEquals(forward, input.pressingBack);
                assertEquals(right, input.pressingLeft);
                assertEquals(left, input.pressingRight);
                assertTrue(input.jumping);
                assertTrue(input.sneaking);
            }
        }
    }

    @Test void eligibilityRequiresSyncedLivingSurvivalOrAdventureAndCurrentServerId() {
        for (var mode : GameMode.values()) {
            assertEquals(mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE,
                    InvertedKeyboardInput.affects(true, List.of(ID, ID, "no_sprint"), mode, true));
            assertFalse(InvertedKeyboardInput.affects(false, List.of(ID), mode, true));
            assertFalse(InvertedKeyboardInput.affects(true, List.of(ID), mode, false));
            assertFalse(InvertedKeyboardInput.affects(true, List.of("no_sprint"), mode, true));
        }
        assertFalse(InvertedKeyboardInput.affects(true, List.of(ID), null, true));
    }

    @Test void resetAndFreshSynchronizationCannotCarryAnEndedActivationToAnotherConnection() {
        ClientDataHolder.updateState(1, 20, List.of(ID), "", List.of());
        assertTrue(active());
        ClientDataHolder.reset();
        assertFalse(active());
        ClientDataHolder.updateState(2, 40, List.of("no_sprint"), "", List.of(ID));
        assertFalse(active());
        ClientDataHolder.updateState(3, 60, List.of(ID), "", List.of());
        assertTrue(active());
        assertFalse(InvertedKeyboardInput.shouldInvert(null, new Input()));
    }

    private boolean active() {
        return InvertedKeyboardInput.affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(),
                GameMode.SURVIVAL, true);
    }
}
