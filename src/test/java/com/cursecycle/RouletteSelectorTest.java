package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.config.CurseCycleConfig;
import com.cursecycle.roulette.RouletteSelector;
import com.cursecycle.state.CurseCycleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RouletteSelectorTest {
    private CurseCycleConfig config;
    private CurseCycleState state;

    @BeforeEach
    void setUp() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        config = new CurseCycleConfig();
        state = new CurseCycleState();
    }

    @Test
    void everyChallengeWinsOnceBeforeTheBagRefills() {
        Set<String> remaining = new HashSet<>();
        ChallengeRegistry.getAll().forEach(challenge -> remaining.add(challenge.getId()));
        int size = remaining.size();
        for (int i = 0; i < size; i++) {
            var result = RouletteSelector.selectNextChallenge(config, state);
            assertNotNull(result);
            assertTrue(result.wheelItems.stream().allMatch(challenge -> remaining.contains(challenge.getId())),
                    "Used challenges must not return as filler sectors");
            assertTrue(remaining.remove(result.winner.getId()));
        }
        assertTrue(remaining.isEmpty());
        assertTrue(state.getCurrentPool().isEmpty());
    }

    @Test
    void refillingDefersTheLastWinnerWithoutDroppingItFromTheBag() {
        for (Challenge previous : ChallengeRegistry.getAll()) {
            state.getCurrentPool().clear();
            state.setLastDrawnChallengeId(previous.getId());
            var result = RouletteSelector.selectNextChallenge(config, state);
            assertNotNull(result);
            assertTrue(result.wheelItems.stream().noneMatch(c -> c.getId().equals(previous.getId())));
            assertTrue(state.getCurrentPool().contains(previous.getId()));
        }
    }

    @Test
    void disabledAndRemovedIdsDoNotKeepTheBagStuck() {
        state.getCurrentPool().addAll(List.of("no_sprint", "removed_challenge"));
        config.challengeToggles.put("no_sprint", false);
        config.applyChallengeToggles();
        var result = RouletteSelector.selectNextChallenge(config, state);
        assertNotNull(result);
        assertTrue(result.wheelItems.stream().noneMatch(c -> c.getId().equals("no_sprint")));
        assertFalse(state.getCurrentPool().contains("removed_challenge"));
    }

    @Test
    void oneRemainingEnabledChallengeCanRepeat() {
        ChallengeRegistry.getAll().forEach(c -> c.setEnabled(c.getId().equals("no_sprint")));
        state.setLastDrawnChallengeId("no_sprint");
        assertEquals("no_sprint", RouletteSelector.selectNextChallenge(config, state).winner.getId());
        assertEquals("no_sprint", RouletteSelector.selectNextChallenge(config, state).winner.getId());
        ChallengeRegistry.get("no_sprint").setEnabled(false);
        assertNull(RouletteSelector.selectNextChallenge(config, state));
    }

    @Test
    void zeroWeightCategoriesStayExcludedAcrossRefills() {
        config.difficultyWeightsByDay = Map.of("1", List.of(0, 0, 1));
        String previous = null;
        for (int i = 0; i < 20; i++) {
            var result = RouletteSelector.selectNextChallenge(config, state);
            assertNotNull(result);
            assertEquals(ChallengeDifficulty.HARD, result.winner.getDifficulty());
            assertNotEquals(previous, result.winner.getId());
            previous = result.winner.getId();
        }
    }

    @Test
    void doubleChallengeSelectsDistinctEnabledChildrenAndKeepsTheEncodedResult() {
        state.getCurrentPool().add("double_challenge");
        ChallengeRegistry.get("no_sprint").setEnabled(false);
        var result = RouletteSelector.selectNextChallenge(config, state);
        assertEquals("double_challenge", result.winner.getId());
        List<String> children = ChallengeUtils.parseSubChallengeIds(result.getEncodedWinnerId());
        assertEquals(2, new HashSet<>(children).size());
        assertFalse(children.contains("double_challenge"));
        assertFalse(children.contains("no_sprint"));
        assertEquals(children, ChallengeUtils.resolveToActiveIds(result.getEncodedWinnerId()));
    }
}
