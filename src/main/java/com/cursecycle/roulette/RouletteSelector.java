package com.cursecycle.roulette;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeUtils;
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

        List<String> pool = state.getCurrentPool();
        Set<String> eligibleIds = new LinkedHashSet<>();
        availableEasy.forEach(c -> eligibleIds.add(c.getId()));
        availableMedium.forEach(c -> eligibleIds.add(c.getId()));
        availableHard.forEach(c -> eligibleIds.add(c.getId()));
        pool.retainAll(eligibleIds);

        if (pool.isEmpty()) {
            pool.addAll(eligibleIds);
        }

        // Keep the previous winner in the bag, but defer it for this draw if alternatives exist.
        List<String> drawPool = new ArrayList<>(pool);
        if (drawPool.size() > 1) drawPool.remove(state.getLastDrawnChallengeId());

        List<Challenge> selectedForWheel = new ArrayList<>();
        selectedForWheel.addAll(pickCandidates(availableEasy, targetEasyCount, drawPool));
        selectedForWheel.addAll(pickCandidates(availableMedium, targetMediumCount, drawPool));
        selectedForWheel.addAll(pickCandidates(availableHard, targetHardCount, drawPool));

        // Difficulty weights are preferred sector counts. Fill shortages with other unseen challenges,
        // including zero-weight difficulties, before repeating any challenge within the wheel.
        int targetCount = targetEasyCount + targetMediumCount + targetHardCount;
        Set<String> selectedIds = new HashSet<>();
        selectedForWheel.forEach(c -> selectedIds.add(c.getId()));
        List<Challenge> fallback = ChallengeRegistry.getAll().stream()
                .filter(Challenge::isEnabled)
                .filter(c -> !selectedIds.contains(c.getId()))
                .toList();
        selectedForWheel.addAll(pickCandidates(fallback, targetCount - selectedForWheel.size(), drawPool));

        if (selectedForWheel.isEmpty()) {
            state.markDirty();
            return null;
        }

        // Only repeat sectors when the entire remaining bag is smaller than the wheel.
        List<Challenge> repeatCandidates = new ArrayList<>(selectedForWheel);
        Collections.shuffle(repeatCandidates);
        for (int i = 0; selectedForWheel.size() < targetCount; i++) {
            selectedForWheel.add(repeatCandidates.get(i % repeatCandidates.size()));
        }

        Collections.shuffle(selectedForWheel);

        Random random = new Random();
        Challenge winner = selectedForWheel.get(random.nextInt(selectedForWheel.size()));

        pool.remove(winner.getId());
        state.setLastDrawnChallengeId(winner.getId());
        state.markDirty();

        List<Challenge> subChallenges = new ArrayList<>();
        if (ChallengeUtils.isDoubleChallenge(winner.getId())) {
            List<String> subIds = ChallengeUtils.resolveToActiveIds(winner.getId());
            for (String subId : subIds) {
                Challenge sc = ChallengeRegistry.get(subId);
                if (sc != null) subChallenges.add(sc);
            }
        }

        return new WheelResult(selectedForWheel, winner, subChallenges);
    }

    private static List<Challenge> pickCandidates(List<Challenge> available, int count, List<String> pool) {
        List<Challenge> result = new ArrayList<>();
        if (available.isEmpty()) return result;

        List<Challenge> inPool = available.stream().filter(c -> pool.contains(c.getId())).toList();
        if (inPool.isEmpty()) return result;

        List<Challenge> copy = new ArrayList<>(inPool);
        Collections.shuffle(copy);

        for (int i = 0; i < count && i < copy.size(); i++) {
            result.add(copy.get(i));
        }
        return result;
    }

    public static class WheelResult {
        public final List<Challenge> wheelItems;
        public final Challenge winner;
        public final List<Challenge> subChallenges;

        public WheelResult(List<Challenge> wheelItems, Challenge winner, List<Challenge> subChallenges) {
            this.wheelItems = wheelItems;
            this.winner = winner;
            this.subChallenges = subChallenges != null ? subChallenges : Collections.emptyList();
        }

        public String getEncodedWinnerId() {
            if (ChallengeUtils.isDoubleChallenge(winner.getId()) && !subChallenges.isEmpty()) {
                List<String> subIds = subChallenges.stream().map(Challenge::getId).toList();
                return ChallengeUtils.encodeDoubleChallenge(subIds);
            }
            return winner.getId();
        }
    }
}
