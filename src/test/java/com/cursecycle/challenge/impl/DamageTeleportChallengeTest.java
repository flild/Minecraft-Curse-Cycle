package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.challenge.ModChallenges;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DamageTeleportChallengeTest {
    private static final List<Integer> DIRECTIONS = List.of(0, 1, 2, 3, 4, 5, 6, 7);

    @Test void exactDistanceHeightOrderAndFiniteDistinctCandidates() {
        Vec3d origin = new Vec3d(10.25, 70.5, -19.75);
        var candidates = DamageTeleportChallenge.candidates(origin, DIRECTIONS);
        assertEquals(40, candidates.size());
        assertEquals(40, new HashSet<>(candidates).size());
        int[] heights = {0, 1, -1, 2, -2};
        for (int i = 0; i < candidates.size(); i++) {
            Vec3d candidate = candidates.get(i);
            assertEquals(5, Math.hypot(candidate.x - origin.x, candidate.z - origin.z), 1.0E-10);
            assertEquals(origin.y + heights[i % 5], candidate.y);
        }
    }

    @Test void stopsOnFirstSafeHeightAndUsesSuppliedDirectionOrder() {
        AtomicInteger checked = new AtomicInteger();
        var found = DamageTeleportChallenge.findDestination(new Vec3d(0, 10, 0), DIRECTIONS, pos -> {
            checked.incrementAndGet();
            return pos.x > 4.9 && pos.y == 11;
        });
        assertEquals(new Vec3d(5, 11, 0), found.orElseThrow());
        assertEquals(2, checked.get());
        var reversed = DamageTeleportChallenge.findDestination(Vec3d.ZERO, List.of(7, 6, 5, 4, 3, 2, 1, 0), pos -> true).orElseThrow();
        assertEquals(5 / Math.sqrt(2), reversed.x, 1.0E-10);
        assertEquals(-5 / Math.sqrt(2), reversed.z, 1.0E-10);
    }

    @Test void unsuccessfulSearchChecksExactlyFortyCandidates() {
        AtomicInteger checked = new AtomicInteger();
        assertTrue(DamageTeleportChallenge.findDestination(Vec3d.ZERO, DIRECTIONS, pos -> {
            checked.incrementAndGet();
            return false;
        }).isEmpty());
        assertEquals(40, checked.get());
    }

    @Test void registeredOnceAndAvailableInDoubleChallenge() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var challenge = ChallengeRegistry.get(DamageTeleportChallenge.ID);
        assertInstanceOf(DamageTeleportChallenge.class, challenge);
        assertEquals(ChallengeDifficulty.MEDIUM, challenge.getDifficulty());
        assertEquals(ChallengeScope.GLOBAL, challenge.getScope());
        assertEquals("Почти как эндермен", challenge.getName());
        assertEquals(1, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(DamageTeleportChallenge.ID)).count());
        assertEquals(List.of("damage_teleport", "damage_slot_drop"),
                ChallengeUtils.resolveToActiveIds("double_challenge:damage_teleport,damage_slot_drop"));
    }
}
