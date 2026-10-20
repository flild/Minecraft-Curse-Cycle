package com.cursecycle.challenge;

import com.cursecycle.challenge.special.DoubleChallenge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChallengeUtils {

    public static final String DOUBLE_CHALLENGE_PREFIX = DoubleChallenge.ID + ":";

    public static boolean isDoubleChallenge(String challengeId) {
        return challengeId != null && (DoubleChallenge.ID.equals(challengeId) || challengeId.startsWith(DOUBLE_CHALLENGE_PREFIX));
    }

    public static String getBaseChallengeId(String challengeId) {
        if (challengeId == null) return "";
        if (challengeId.startsWith(DOUBLE_CHALLENGE_PREFIX)) {
            return DoubleChallenge.ID;
        }
        return challengeId;
    }

    public static List<String> parseSubChallengeIds(String challengeId) {
        List<String> subIds = new ArrayList<>();
        if (challengeId == null || challengeId.isEmpty()) return subIds;

        if (challengeId.startsWith(DOUBLE_CHALLENGE_PREFIX)) {
            String[] parts = challengeId.substring(DOUBLE_CHALLENGE_PREFIX.length()).split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    subIds.add(trimmed);
                }
            }
        }
        return subIds;
    }

    public static String encodeDoubleChallenge(List<String> subIds) {
        if (subIds == null || subIds.isEmpty()) {
            return DoubleChallenge.ID;
        }
        return DOUBLE_CHALLENGE_PREFIX + String.join(",", subIds);
    }

    /**
     * Resolves a challenge ID into a list of actual challenge IDs to activate.
     * If the ID is "double_challenge" without sub-ids specified, picks 2 random enabled challenges.
     */
    public static List<String> resolveToActiveIds(String challengeId) {
        List<String> activeIds = new ArrayList<>();
        if (challengeId == null || challengeId.isEmpty()) return activeIds;

        if (challengeId.startsWith(DOUBLE_CHALLENGE_PREFIX)) {
            activeIds.addAll(parseSubChallengeIds(challengeId));
        } else if (DoubleChallenge.ID.equals(challengeId)) {
            List<Challenge> candidates = ChallengeRegistry.getAll().stream()
                    .filter(Challenge::isEnabled)
                    .filter(c -> !DoubleChallenge.ID.equals(c.getId()))
                    .toList();
            List<Challenge> copy = new ArrayList<>(candidates);
            Collections.shuffle(copy);
            if (copy.size() >= 2) {
                activeIds.add(copy.get(0).getId());
                activeIds.add(copy.get(1).getId());
            } else if (!copy.isEmpty()) {
                activeIds.add(copy.get(0).getId());
            }
        } else {
            activeIds.add(challengeId);
        }
        return activeIds;
    }

    public static String getFormattedDisplayName(String challengeId) {
        if (challengeId == null || challengeId.isEmpty()) return "Нет";

        if (challengeId.startsWith(DOUBLE_CHALLENGE_PREFIX)) {
            List<String> subIds = parseSubChallengeIds(challengeId);
            List<String> names = subIds.stream().map(id -> {
                Challenge c = ChallengeRegistry.get(id);
                return c != null ? c.getName() : id;
            }).toList();
            return "2x (" + String.join(" & ", names) + ")";
        }

        Challenge c = ChallengeRegistry.get(challengeId);
        return c != null ? c.getName() : challengeId;
    }
}
