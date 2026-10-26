package com.cursecycle;

import com.cursecycle.challenge.impl.HotbarShuffleChallenge;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HotbarShuffleChallengeTest {
    @Test void fullIntervalDuplicateDispatchAndNoCatchUpBurst() {
        var clock = new HotbarShuffleChallenge.Clock(23);
        assertFalse(clock.due(23));
        assertFalse(clock.due(122));
        assertTrue(clock.due(123));
        assertFalse(clock.due(123));
        assertFalse(clock.due(222));
        assertTrue(clock.due(223));
        assertTrue(clock.due(1000));
        assertFalse(clock.due(1000));
        assertFalse(clock.due(1099));
        assertTrue(clock.due(1100));
    }

    @Test void identicalAndEmptyInputsDoNotDrawRandomNumbers() {
        for (var input : List.of(List.<String>of(), List.of(""), List.of("", "", ""), List.of("a", "a", "a"))) {
            assertSame(input, HotbarShuffleChallenge.shuffled(input, Objects::equals,
                    bound -> { throw new AssertionError("No distinct contents to shuffle"); }));
        }
    }

    @Test void rejectsVisuallyUnchangedPermutationsIncludingDuplicateContents() {
        // First Fisher-Yates draw swaps two equal entries only; second moves the distinct entry.
        var draws = List.of(2, 0, 0, 1);
        var index = new AtomicInteger();
        var input = List.of("a", "a", "b");
        assertEquals(List.of("b", "a", "a"), HotbarShuffleChallenge.shuffled(input, Objects::equals,
                bound -> draws.get(index.getAndIncrement())));
        assertEquals(4, index.get());
        assertEquals(List.of("a", "a", "b"), input);
    }

    @Test void exhaustiveFisherYatesDrawsWeightEveryChangedArrangementEqually() {
        for (var input : List.of(List.of("a", "b", "c"), List.of("a", "a", "b"))) {
            var counts = new HashMap<List<String>, Integer>();
            for (int a = 0; a < 3; a++) for (int b = 0; b < 2; b++) {
                var draws = List.of(a, b);
                var index = new AtomicInteger();
                try {
                    var result = HotbarShuffleChallenge.shuffled(input, Objects::equals, bound -> {
                        int i = index.getAndIncrement();
                        if (i >= 2) throw new Rejected();
                        assertTrue(draws.get(i) < bound);
                        return draws.get(i);
                    });
                    assertNotEquals(input, result);
                    assertEquals(input.stream().sorted().toList(), result.stream().sorted().toList());
                    counts.merge(result, 1, Integer::sum);
                } catch (Rejected expected) { /* Identity/equal-content draws must be rejected. */ }
            }
            assertEquals(input.get(0).equals(input.get(1)) ? 2 : 5, counts.size());
            assertEquals(1, counts.values().stream().distinct().count());
        }
    }

    private static class Rejected extends RuntimeException {}
}
