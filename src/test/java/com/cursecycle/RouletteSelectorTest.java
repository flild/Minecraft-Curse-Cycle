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
            state.setCurrentDay(1L + i * 5L);
            var result = RouletteSelector.selectNextChallenge(config, state);
            assertNotNull(result);
            int sectors = config.getWeightsForDay(state.getCurrentDay()).stream().mapToInt(Integer::intValue).sum();
            assertEquals(sectors, result.wheelItems.size(), "Exhausted difficulties must not leave empty sectors");
            assertEquals(Math.min(sectors, remaining.size()),
                    result.wheelItems.stream().map(Challenge::getId).distinct().count(),
                    "Use every available distinct challenge before repeating sectors");
            assertTrue(result.wheelItems.stream().allMatch(challenge -> remaining.contains(challenge.getId())),
                    "Used challenges must not return as filler sectors");
            assertTrue(remaining.remove(result.winner.getId()));
            state = CurseCycleState.createFromNbt(state.writeNbt(new net.minecraft.nbt.NbtCompound(), null), null);
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
    void zeroWeightCategoriesOnlyFillMissingSectorsAfterPreferredChallengesRunOut() {
        config.difficultyWeightsByDay = Map.of("1", List.of(0, 0, 1));
        Set<String> hardIds = new HashSet<>();
        ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.HARD).forEach(c -> hardIds.add(c.getId()));
        String previous = null;
        for (int i = 0; i < hardIds.size(); i++) {
            var result = RouletteSelector.selectNextChallenge(config, state);
            assertNotNull(result);
            assertEquals(ChallengeDifficulty.HARD, result.winner.getDifficulty());
            assertNotEquals(previous, result.winner.getId());
            previous = result.winner.getId();
        }
        var fallback = RouletteSelector.selectNextChallenge(config, state);
        assertNotNull(fallback);
        assertNotEquals(ChallengeDifficulty.HARD, fallback.winner.getDifficulty());
        assertTrue(state.getCurrentPool().stream().noneMatch(hardIds::contains),
                "Do not refill a difficulty while other unseen challenges remain");
    }

    @Test
    void fullWheelKeepsDifficultyQuotasWhenEnoughDistinctChallengesRemain() {
        var result = RouletteSelector.selectNextChallenge(config, state);
        assertNotNull(result);
        assertEquals(6, result.wheelItems.size());
        assertEquals(6, result.wheelItems.stream().map(Challenge::getId).distinct().count());
        assertEquals(3, result.wheelItems.stream().filter(c -> c.getDifficulty() == ChallengeDifficulty.EASY).count());
        assertEquals(2, result.wheelItems.stream().filter(c -> c.getDifficulty() == ChallengeDifficulty.MEDIUM).count());
        assertEquals(1, result.wheelItems.stream().filter(c -> c.getDifficulty() == ChallengeDifficulty.HARD).count());
    }

    @Test
    void sixRemainingHardChallengesFillAllSixSectors() {
        List<String> remaining = ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.HARD).stream()
                .limit(6).map(Challenge::getId).toList();
        assertEquals(6, remaining.size());
        state.getCurrentPool().addAll(remaining);
        var result = RouletteSelector.selectNextChallenge(config, state);
        assertEquals(6, result.wheelItems.size());
        assertEquals(new HashSet<>(remaining), new HashSet<>(result.wheelItems.stream().map(Challenge::getId).toList()));
        assertEquals(5, state.getCurrentPool().size(), "Only the winner leaves the bag");
    }

    @Test
    void shortagesUseOtherDifficultiesBeforeRepeatingAnySector() {
        List<String> remaining = new ArrayList<>();
        remaining.add(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.EASY).getFirst().getId());
        remaining.addAll(ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.HARD).stream()
                .limit(5).map(Challenge::getId).toList());
        state.getCurrentPool().addAll(remaining);
        var result = RouletteSelector.selectNextChallenge(config, state);
        assertEquals(6, result.wheelItems.size());
        assertEquals(new HashSet<>(remaining), new HashSet<>(result.wheelItems.stream().map(Challenge::getId).toList()));
    }

    @Test
    void fallbackIgnoresDisabledChallengesAndRetainsZeroWeightChallengesAcrossStageChanges() {
        config.difficultyWeightsByDay = Map.of("1", List.of(6, 0, 0), "20", List.of(0, 6, 0));
        List<String> remaining = ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.HARD).stream()
                .limit(6).map(Challenge::getId).toList();
        state.getCurrentPool().addAll(remaining);
        ChallengeRegistry.get(remaining.getFirst()).setEnabled(false);
        state.getCurrentPool().add("removed_challenge");
        for (int day : new int[]{1, 20}) {
            state.setCurrentDay(day);
            Set<String> unseen = new HashSet<>(state.getCurrentPool());
            unseen.remove(remaining.getFirst());
            unseen.remove("removed_challenge");
            var result = RouletteSelector.selectNextChallenge(config, state);
            assertNotNull(result);
            assertEquals(6, result.wheelItems.size());
            assertEquals(unseen, new HashSet<>(result.wheelItems.stream().map(Challenge::getId).toList()));
            unseen.remove(result.winner.getId());
            assertEquals(unseen, new HashSet<>(state.getCurrentPool()));
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
    @Test
    void playableDefaultsRemoveOldPlaceholderPoolEntriesAndExcludeThemFromDoubleChallenges() {
        config.challengeToggles.clear(); // A config saved by the previous build.
        config.validate();
        config.applyChallengeToggles();
        for (var challenge : ChallengeRegistry.getAll()) {
            if (!challenge.isEnabled()) state.getCurrentPool().add(challenge.getId());
        }
        var result = RouletteSelector.selectNextChallenge(config, state);
        assertNotNull(result);
        assertTrue(result.wheelItems.stream().allMatch(c -> c.isEnabled()
                && !c.getClass().getPackageName().equals("com.cursecycle.challenge.test")));
        assertTrue(state.getCurrentPool().stream().allMatch(id -> ChallengeRegistry.get(id).isEnabled()));

        state.getCurrentPool().clear();
        state.getCurrentPool().add("double_challenge");
        state.setLastDrawnChallengeId(null);
        result = RouletteSelector.selectNextChallenge(config, state);
        assertEquals("double_challenge", result.winner.getId());
        assertEquals(2, result.subChallenges.size());
        assertTrue(result.subChallenges.stream().allMatch(c -> c.isEnabled()
                && !c.getClass().getPackageName().equals("com.cursecycle.challenge.test")));
    }

}
