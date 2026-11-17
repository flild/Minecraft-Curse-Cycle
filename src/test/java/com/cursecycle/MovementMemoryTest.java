package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.impl.MovementMemoryChallenge;
import com.cursecycle.challenge.impl.BlockedDirectionChallenge.Direction;
import com.cursecycle.client.BlockedDirectionInput;
import com.cursecycle.client.InvertedKeyboardInput;
import com.cursecycle.client.MovementMemoryInput;
import com.cursecycle.network.MovementMemoryPayload;
import net.minecraft.client.input.Input;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MovementMemoryTest {
    private static final String ID = MovementMemoryChallenge.ID;
    private static Input input(float forward, float sideways) {
        var result = new Input(); result.movementForward = forward; result.movementSideways = sideways;
        result.pressingForward = forward > 0; result.pressingBack = forward < 0;
        result.pressingLeft = sideways > 0; result.pressingRight = sideways < 0; return result;
    }
    @Test void registrationAndDescriptions() {
        ChallengeRegistry.clear(); ModChallenges.registerAll();
        var c = ChallengeRegistry.get(ID);
        assertInstanceOf(MovementMemoryChallenge.class, c);
        assertFalse(c.getName().isBlank());
        assertFalse(c.getShortDescription().isBlank());
        assertFalse(c.getDetailedRules().isBlank());
        assertEquals(ChallengeDifficulty.MEDIUM, c.getDifficulty()); assertEquals(ChallengeScope.GLOBAL, c.getScope());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(ch -> ID.equals(ch.getId())).count());
        assertEquals(List.of(ID, "no_sprint"), ChallengeUtils.resolveToActiveIds("double_challenge:movement_memory,no_sprint"));
    }
    @Test void stationaryActivationOneReleaseAndAuthoritativeEnd() {
        var m = new MovementMemoryInput.Memory(); var packets = new ArrayList<MovementMemoryPayload>(); m.context(5);
        for (int i = 0; i < 50; i++) m.apply(input(0, 0), 1, packets::add);
        assertTrue(packets.isEmpty());
        m.apply(input(1, -1), 1, packets::add);
        var release = input(0, 0); m.apply(release, 1, packets::add);
        assertEquals(1, release.movementForward); assertEquals(-1, release.movementSideways);
        var request = packets.getFirst(); assertTrue(request.holding());
        var requests = List.copyOf(packets);
        for (int i = 0; i < 200; i++) m.apply(input(0, 0), 1, packets::add);
        assertEquals(2, packets.size(), "One independent release per key; client ticks never extend intervals");
        for (var pending : requests)
            m.receive(new MovementMemoryPayload(5, pending.sequence(), pending.direction(), false));
        for (int i = 0; i < 30; i++) {
            var zero = input(0, 0); m.apply(zero, 1, packets::add);
            assertEquals(0, zero.movementForward); assertEquals(0, zero.movementSideways);
        }
        assertEquals(2, packets.size()); assertFalse(m.holding());
    }
    @Test void rightThenForwardRetainsRightAndExpiresIndependently() {
        var m = new MovementMemoryInput.Memory(); var packets = new ArrayList<MovementMemoryPayload>();
        m.context(9); m.apply(input(0, -1), 1, packets::add);
        var forward = input(1, 0); m.apply(forward, 1, packets::add);
        assertEquals(1, forward.movementForward); assertEquals(-1, forward.movementSideways);
        var right = packets.getFirst(); assertEquals(MovementMemoryPayload.RIGHT, right.direction());
        m.apply(input(0, 0), 1, packets::add);
        var up = packets.getLast(); assertEquals(MovementMemoryPayload.FORWARD, up.direction());
        m.receive(new MovementMemoryPayload(9, right.sequence(), right.direction(), false));
        var next = input(0, 0); m.apply(next, 1, packets::add);
        assertEquals(1, next.movementForward); assertEquals(0, next.movementSideways);
        m.receive(new MovementMemoryPayload(8, up.sequence(), up.direction(), false)); assertTrue(m.holding());
        m.receive(new MovementMemoryPayload(9, up.sequence(), up.direction(), false)); assertFalse(m.holding());
    }
    @Test void rePressRefreshesOnlyItsKeyAndStaleRepliesCannotEndNewRelease() {
        var m = new MovementMemoryInput.Memory(); var packets = new ArrayList<MovementMemoryPayload>();
        m.context(9); m.apply(input(1, -1), 1, packets::add); m.apply(input(0, 0), 1, packets::add);
        var oldRight = packets.getLast();
        var right = input(0, -1); m.apply(right, 1, packets::add);
        assertEquals(1, right.movementForward); assertFalse(packets.getLast().holding());
        assertEquals(MovementMemoryPayload.RIGHT, packets.getLast().direction());
        m.apply(input(0, 0), 1, packets::add);
        var freshRight = packets.getLast(); assertTrue(freshRight.holding());
        m.receive(new MovementMemoryPayload(9, oldRight.sequence(), oldRight.direction(), false));
        var both = input(0, 0); m.apply(both, 1, packets::add);
        assertEquals(1, both.movementForward); assertEquals(-1, both.movementSideways);
        m.receive(new MovementMemoryPayload(9, packets.getFirst().sequence(), MovementMemoryPayload.FORWARD, false));
        var onlyRight = input(0, 0); m.apply(onlyRight, 1, packets::add);
        assertEquals(0, onlyRight.movementForward); assertEquals(-1, onlyRight.movementSideways);
        m.context(10); m.apply(input(0, 0), 1, packets::add); assertFalse(m.holding());
    }
    @Test void opposingKeysCancelUntilTheirSeparateIntervalsEnd() {
        var m = new MovementMemoryInput.Memory(); var packets = new ArrayList<MovementMemoryPayload>(); m.context(1);
        m.apply(input(1, 0), 1, packets::add);
        var back = input(-1, 0); m.apply(back, 1, packets::add);
        assertEquals(0, back.movementForward); assertTrue(back.pressingForward); assertTrue(back.pressingBack);
        var forwardRelease = packets.getFirst();
        m.receive(new MovementMemoryPayload(1, forwardRelease.sequence(), forwardRelease.direction(), false));
        back = input(-1, 0); m.apply(back, 1, packets::add);
        assertEquals(-1, back.movementForward);
    }
    @Test void freshSneakJumpOpposingKeysAndTransformOrder() {
        for (int mask = 0; mask < 16; mask++) for (boolean inverted : new boolean[]{false, true}) {
            var m = new MovementMemoryInput.Memory(); var packets = new ArrayList<MovementMemoryPayload>(); m.context(1);
            float f = ((mask & 1) != 0 ? 1 : 0) - ((mask & 2) != 0 ? 1 : 0);
            float s = ((mask & 4) != 0 ? 1 : 0) - ((mask & 8) != 0 ? 1 : 0);
            var original = input(f * .3f, s * .3f);
            original.pressingForward = (mask & 1) != 0; original.pressingBack = (mask & 2) != 0;
            original.pressingLeft = (mask & 4) != 0; original.pressingRight = (mask & 8) != 0;
            if (inverted) { InvertedKeyboardInput.invert(original); f = -f; s = -s; }
            m.apply(original, .3f, packets::add);
            var held = input(0, 0); held.jumping = true; m.apply(held, 1, packets::add);
            assertEquals(f, held.movementForward, .00001f); assertEquals(s, held.movementSideways, .00001f);
            assertTrue(held.jumping); assertFalse(held.sneaking);
            BlockedDirectionInput.block(held, Direction.FORWARD);
            assertEquals(Math.min(0, f), held.movementForward, .00001f);
            var slow = input(0, 0); slow.sneaking = true; m.apply(slow, .2f, packets::add);
            assertEquals(f * .2f, slow.movementForward, .00001f); assertEquals(s * .2f, slow.movementSideways, .00001f);
            assertFalse(slow.jumping); assertTrue(slow.sneaking);
            assertEquals(Integer.bitCount(mask), packets.size());
        }
    }
    @Test void exclusionsCancelAndResetRequireNewInput() {
        for (var mode : GameMode.values()) for (int excluded = -1; excluded < 6; excluded++) {
            boolean expected = (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && excluded == -1;
            assertEquals(expected, MovementMemoryInput.affects(true, List.of(ID), mode, excluded != 0,
                    excluded == 1, excluded == 2, excluded == 3, excluded == 4, excluded == 5));
        }
        assertFalse(MovementMemoryInput.affects(false, List.of(ID), GameMode.SURVIVAL, true, false, false, false, false, false));
        assertFalse(MovementMemoryInput.affects(true, List.of(), GameMode.SURVIVAL, true, false, false, false, false, false));
        var m = new MovementMemoryInput.Memory(); var packets = new ArrayList<MovementMemoryPayload>(); m.context(1);
        m.apply(input(1, 0), 1, packets::add); m.apply(input(0, 0), 1, packets::add);
        var old = packets.getFirst(); m.cancel(packets::add); assertFalse(packets.getLast().holding());
        m.receive(old); m.receive(new MovementMemoryPayload(1, -1, false));
        var zero = input(0, 0); m.apply(zero, 1, packets::add); assertEquals(0, zero.movementForward);
        m.reset(); m.apply(input(1, 0), 1, packets::add); m.context(2);
        zero = input(0, 0); m.apply(zero, 1, packets::add); assertEquals(0, zero.movementForward);
    }
}
