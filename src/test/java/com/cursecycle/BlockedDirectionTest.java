package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.BlockedDirectionChallenge;
import com.cursecycle.challenge.impl.BlockedDirectionChallenge.Direction;
import com.cursecycle.client.BlockedDirectionInput;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.InvertedKeyboardInput;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.client.input.Input;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BlockedDirectionTest {
    private static final String ID = BlockedDirectionChallenge.ID;
    @BeforeEach void register() { ChallengeRegistry.clear(); ModChallenges.registerAll(); }
    @AfterEach void reset() { ClientDataHolder.reset(); }

    @Test void metadataAndCompositeMatchSpecification() {
        var c = ChallengeRegistry.get(ID);
        assertInstanceOf(BlockedDirectionChallenge.class, c);
        assertEquals("Туда нельзя", c.getName());
        assertEquals("Одно направление движения отключено до конца испытания.", c.getShortDescription());
        assertEquals("При активации случайно выбирается одно общее направление: влево, вправо или вперёд. "
                + "В Survival и Adventure соответствующая составляющая собственного движения отключается до конца испытания. "
                + "Назад идти можно всегда; поворачивать камеру и обходить препятствие другой стороной разрешено.", c.getDetailedRules());
        assertEquals(ChallengeDifficulty.EASY, c.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, c.getScope());
        assertEquals(1L, ChallengeRegistry.getAll().stream().filter(ch -> ID.equals(ch.getId())).count());
        assertEquals(List.of(ID, "no_sprint"), ChallengeUtils.resolveToActiveIds("double_challenge:blocked_direction,no_sprint"));
    }

    @Test void choiceIsWorldOwnedDrawnOnceAndSurvivesNbtWithoutAnotherDraw() {
        var state = new CurseCycleState();
        var draws = new AtomicInteger();
        state.getActiveChallengeIds().add(ID);
        state.getChallengeData().putString("unrelated", "preserve");
        for (int index = 0; index < 3; index++) {
            state.beginActivation();
            int expectedIndex = index;
            var expected = Direction.values()[index];
            assertEquals(expected, BlockedDirectionChallenge.reconcile(state, bound -> {
                assertEquals(3, bound); draws.incrementAndGet(); return expectedIndex;
            }));
            assertEquals(index + 1, draws.get());
            assertEquals(expected.id(), state.getChallengeData().getCompound(ID).getString("direction"));
            assertEquals(state.getActivationSerial(), state.getChallengeData().getCompound(ID).getLong("activationSerial"));
            var loaded = CurseCycleState.createFromNbt(state.writeNbt(new NbtCompound(), null), null);
            for (int repeat = 0; repeat < 10; repeat++)
                assertEquals(expected, BlockedDirectionChallenge.reconcile(loaded, bound -> fail("Existing activation must not reroll")));
        }
        // Empty online player lists never enter into the state decision.
        state.getChallengeData().putString("unrelated", "preserve");
        state.getActiveChallengeIds().clear();
        assertNull(BlockedDirectionChallenge.reconcile(state, bound -> fail("Ended activation must not draw")));
        assertFalse(state.getChallengeData().contains(ID));
        assertEquals("preserve", state.getChallengeData().getString("unrelated"));
    }

    @Test void staleSerialInvalidChoicePersonalIdsAndDisabledCleanup() {
        var state = new CurseCycleState();
        state.getPersonalActiveChallenges().put(UUID.randomUUID(), new ArrayList<>(List.of(ID, ID)));
        var first = BlockedDirectionChallenge.reconcile(state, bound -> 0);
        assertEquals(Direction.LEFT, first);
        state.getChallengeData().getCompound(ID).putLong("activationSerial", -1);
        assertEquals(Direction.RIGHT, BlockedDirectionChallenge.reconcile(state, bound -> 1));
        state.getChallengeData().getCompound(ID).putString("direction", "back");
        assertEquals(Direction.FORWARD, BlockedDirectionChallenge.reconcile(state, bound -> 2));
        ChallengeRegistry.get(ID).setEnabled(false);
        assertNull(BlockedDirectionChallenge.reconcile(state, bound -> fail("Disabled must not draw")));
        assertFalse(state.getChallengeData().contains(ID));
    }

    @Test void allSignedComponentsClampAfterInversionWithoutChangingOtherActions() {
        for (var direction : Direction.values()) for (boolean inverted : new boolean[]{false, true})
            for (int mask = 0; mask < 64; mask++) for (float scale : new float[]{1, .3f, .2f}) {
                var input = input(mask, scale);
                if (inverted) InvertedKeyboardInput.invert(input);
                float f = input.movementForward, s = input.movementSideways;
                boolean back = input.pressingBack;
                BlockedDirectionInput.block(input, direction);
                float expectedF = direction == Direction.FORWARD && f > 0 ? 0 : f;
                float expectedS = (direction == Direction.LEFT && s > 0) || (direction == Direction.RIGHT && s < 0) ? 0 : s;
                assertEquals(expectedF, input.movementForward, 0f);
                assertEquals(expectedS, input.movementSideways, 0f);
                assertEquals(back, input.pressingBack);
                assertEquals((mask & 16) != 0, input.jumping);
                assertEquals((mask & 32) != 0, input.sneaking);
                // Reapplying is harmless; movement remains zero when opposing keys cancel.
                BlockedDirectionInput.block(input, direction);
                assertEquals(expectedF, input.movementForward, 0f);
                assertEquals(expectedS, input.movementSideways, 0f);
            }
    }

    @Test void modesLifeRidingAndElytraAreIndependentGates() {
        for (var mode : GameMode.values()) {
            assertEquals(mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE,
                    BlockedDirectionInput.affects(true, List.of(ID, ID, "no_sprint"), Direction.LEFT, mode, true, false, false));
            assertFalse(BlockedDirectionInput.affects(false, List.of(ID), Direction.LEFT, mode, true, false, false));
            assertFalse(BlockedDirectionInput.affects(true, List.of(ID), Direction.LEFT, mode, false, false, false));
            assertFalse(BlockedDirectionInput.affects(true, List.of(ID), Direction.LEFT, mode, true, true, false));
            assertFalse(BlockedDirectionInput.affects(true, List.of(ID), Direction.LEFT, mode, true, false, true));
        }
        assertFalse(BlockedDirectionInput.affects(true, List.of(ID), null, GameMode.SURVIVAL, true, false, false));
        assertFalse(BlockedDirectionInput.affects(true, List.of("no_sprint"), Direction.LEFT, GameMode.SURVIVAL, true, false, false));
        assertFalse(BlockedDirectionInput.shouldBlock(null, new Input()));
    }

    @Test void synchronizedChoiceAndFeedbackClearOnStopDisconnectAndInvalidData() {
        for (var direction : Direction.values()) {
            ClientDataHolder.updateState(1, 20, List.of(ID), "", List.of(), direction.id());
            assertEquals(direction, ClientDataHolder.getBlockedDirection());
            assertEquals(direction.feedback(), BlockedDirectionInput.feedback(ID));
            assertEquals("", BlockedDirectionInput.feedback("no_sprint"));
        }
        ClientDataHolder.updateState(1, 20, List.of("no_sprint"), "", List.of(ID), "left");
        assertNull(ClientDataHolder.getBlockedDirection());
        assertEquals("", BlockedDirectionInput.feedback(ID));
        ClientDataHolder.updateState(1, 20, List.of(ID), "", List.of(), "back");
        assertNull(ClientDataHolder.getBlockedDirection());
        ClientDataHolder.updateState(1, 20, List.of(ID), "", List.of(), "left");
        ClientDataHolder.reset();
        assertNull(ClientDataHolder.getBlockedDirection());
        assertFalse(ClientDataHolder.isSynced());
    }

    private static Input input(int mask, float scale) {
        var input = new Input();
        input.pressingForward = (mask & 1) != 0; input.pressingBack = (mask & 2) != 0;
        input.pressingLeft = (mask & 4) != 0; input.pressingRight = (mask & 8) != 0;
        input.movementForward = (((mask & 1) != 0 ? 1 : 0) - ((mask & 2) != 0 ? 1 : 0)) * scale;
        input.movementSideways = (((mask & 4) != 0 ? 1 : 0) - ((mask & 8) != 0 ? 1 : 0)) * scale;
        input.jumping = (mask & 16) != 0; input.sneaking = (mask & 32) != 0;
        return input;
    }
}
