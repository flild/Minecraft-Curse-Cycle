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
        var result = new Input(); result.movementForward = forward; result.movementSideways = sideways; return result;
    }
    @Test void registrationAndDescriptions() {
        ChallengeRegistry.clear(); ModChallenges.registerAll();
        var c = ChallengeRegistry.get(ID);
        assertInstanceOf(MovementMemoryChallenge.class, c);
        assertEquals("Ноги помнят", c.getName());
        assertEquals("После отпускания движения ноги продолжают идти ещё секунду.", c.getShortDescription());
        assertEquals("После перехода горизонтального ввода движения в ноль персонаж ещё 20 тиков продолжает последнее направление как будто клавиша всё ещё нажата. Новый ненулевой ввод сразу заменяет запомненный. Это действует на собственное наземное движение; вода, полёт, транспорт и открытые меню используют обычное управление.", c.getDetailedRules());
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
        for (int i = 0; i < 200; i++) m.apply(input(0, 0), 1, packets::add);
        assertEquals(1, packets.size(), "Client tick rate never restarts or expires a server interval");
        m.receive(new MovementMemoryPayload(5, request.sequence(), false));
        for (int i = 0; i < 30; i++) {
            var zero = input(0, 0); m.apply(zero, 1, packets::add);
            assertEquals(0, zero.movementForward); assertEquals(0, zero.movementSideways);
        }
        assertEquals(1, packets.size()); assertFalse(m.holding());
    }
    @Test void replacementAndStaleReplies() {
        var m = new MovementMemoryInput.Memory(); var packets = new ArrayList<MovementMemoryPayload>();
        m.context(9); m.apply(input(1, 0), 1, packets::add); m.apply(input(0, 0), 1, packets::add);
        var old = packets.getFirst();
        var reverse = input(-1, 1); m.apply(reverse, 1, packets::add);
        assertEquals(-1, reverse.movementForward); assertFalse(m.holding()); assertFalse(packets.getLast().holding());
        m.receive(new MovementMemoryPayload(9, old.sequence(), false));
        var next = input(0, 0); m.apply(next, 1, packets::add);
        assertEquals(-1, next.movementForward); assertEquals(1, next.movementSideways);
        m.receive(new MovementMemoryPayload(8, packets.getLast().sequence(), false)); assertTrue(m.holding());
        m.context(10); m.apply(input(0, 0), 1, packets::add); assertFalse(m.holding());
    }
    @Test void freshSneakJumpOpposingKeysAndTransformOrder() {
        for (int mask = 0; mask < 16; mask++) for (boolean inverted : new boolean[]{false, true}) {
            var m = new MovementMemoryInput.Memory(); var packets = new ArrayList<MovementMemoryPayload>(); m.context(1);
            float f = ((mask & 1) != 0 ? 1 : 0) - ((mask & 2) != 0 ? 1 : 0);
            float s = ((mask & 4) != 0 ? 1 : 0) - ((mask & 8) != 0 ? 1 : 0);
            var original = input(f * .3f, s * .3f);
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
            assertEquals(f != 0 || s != 0 ? 1 : 0, packets.size());
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
