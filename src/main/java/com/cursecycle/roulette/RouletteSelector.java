package com.cursecycle.roulette;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.config.CurseCycleConfig;
import com.cursecycle.state.CurseCycleState;

import java.util.*;

public class RouletteSelector {

    public static class WheelCandidate {
        public final Challenge challenge;
        public final ChallengeDifficulty difficulty;

        public WheelCandidate(Challenge challenge, ChallengeDifficulty difficulty) {
            this.challenge = challenge;
            this.difficulty = difficulty;
        }
    }

    /**
     * Prepares a candidate list (wheel items) and selects a winner according to config weights & history.
     */
    public static WheelResult selectNextChallenge(CurseCycleConfig config, CurseCycleState state) {
        List<Integer> weights = config.getWeightsForDay(state.getCurrentDay());
        int targetEasyCount = weights.get(0);
        int targetMediumCount = weights.get(1);
        int targetHardCount = weights.get(2);

        List<Challenge> availableEasy = ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.EASY);
        List<Challenge> availableMedium = ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.MEDIUM);
        List<Challenge> availableHard = ChallengeRegistry.getEnabledByDifficulty(ChallengeDifficulty.HARD);

        // Track pool in state if using pool-exhaustion tracking
        List<String> pool = state.getCurrentPool();

        // Ensure pool has available options if empty or exhausted
        if (pool.isEmpty()) {
            refillPool(pool, availableEasy, availableMedium, availableHard, state.getLastDrawnChallengeId());
        }

        // Gather candidates for each difficulty slot
        List<Challenge> selectedForWheel = new ArrayList<>();
        selectedForWheel.addAll(pickCandidates(availableEasy, targetEasyCount, pool));
        selectedForWheel.addAll(pickCandidates(availableMedium, targetMediumCount, pool));
        selectedForWheel.addAll(pickCandidates(availableHard, targetHardCount, pool));

        if (selectedForWheel.isEmpty()) {
            // Fallback: pick any enabled challenge
            for (Challenge c : ChallengeRegistry.getAll()) {
                if (c.isEnabled()) {
                    selectedForWheel.add(c);
                }
            }
        }

        if (selectedForWheel.isEmpty()) {
            return null; // No registered/enabled challenges
        }

        // Shuffle wheel options for visual variety
        Collections.shuffle(selectedForWheel);

        // Pick winning challenge randomly from the wheel candidates
        Random random = new Random();
        Challenge winner = selectedForWheel.get(random.nextInt(selectedForWheel.size()));

        // Remove winner from pool and update last drawn
        pool.remove(winner.getId());
        state.setLastDrawnChallengeId(winner.getId());
        state.markDirty();

        return new WheelResult(selectedForWheel, winner);
    }

    private static void refillPool(List<String> pool, List<Challenge> easy, List<Challenge> medium, List<Challenge> hard, String lastDrawnId) {
        pool.clear();
        Set<String> allIds = new LinkedHashSet<>();
        easy.forEach(c -> allIds.add(c.getId()));
        medium.forEach(c -> allIds.add(c.getId()));
        hard.forEach(c -> allIds.add(c.getId()));

        if (allIds.size() > 1 && lastDrawnId != null) {
            // Prefer not repeating the last drawn challenge immediately if other options exist
            allIds.remove(lastDrawnId);
            pool.addAll(allIds);
            pool.add(lastDrawnId); // append at end
        } else {
            pool.addAll(allIds);
        }
    }

    private static List<Challenge> pickCandidates(List<Challenge> available, int count, List<String> pool) {
        List<Challenge> result = new ArrayList<>();
        if (available.isEmpty()) return result;

        // Prioritize challenges currently in pool
        List<Challenge> inPool = available.stream().filter(c -> pool.contains(c.getId())).toList();
        List<Challenge> sourceList = !inPool.isEmpty() ? inPool : available;

        List<Challenge> copy = new ArrayList<>(sourceList);
        Collections.shuffle(copy);

        for (int i = 0; i < count; i++) {
            if (copy.isEmpty()) {
                copy = new ArrayList<>(available);
                Collections.shuffle(copy);
            }
            if (!copy.isEmpty()) {
                result.add(copy.remove(0));
            }
        }
        return result;
    }

    public static class WheelResult {
        public final List<Challenge> wheelItems;
        public final Challenge winner;

        public WheelResult(List<Challenge> wheelItems, Challenge winner) {
            this.wheelItems = wheelItems;
            this.winner = winner;
        }
    }
}
