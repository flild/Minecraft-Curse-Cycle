package com.cursecycle.challenge;

import com.cursecycle.CurseCycleMod;

import java.util.*;
import java.util.stream.Collectors;

public class ChallengeRegistry {
    private static final Map<String, Challenge> REGISTRY = new LinkedHashMap<>();

    public static synchronized void register(Challenge challenge) {
        if (REGISTRY.containsKey(challenge.getId())) {
            CurseCycleMod.LOGGER.warn("Challenge with ID {} is already registered. Overwriting.", challenge.getId());
        }
        REGISTRY.put(challenge.getId(), challenge);
        CurseCycleMod.LOGGER.info("Registered challenge: {} [{}]", challenge.getName(), challenge.getId());
    }

    public static Challenge get(String id) {
        return REGISTRY.get(id);
    }

    public static Collection<Challenge> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static List<Challenge> getEnabledByDifficulty(ChallengeDifficulty difficulty) {
        return REGISTRY.values().stream()
                .filter(Challenge::isEnabled)
                .filter(c -> c.getDifficulty() == difficulty)
                .collect(Collectors.toList());
    }

    public static void clear() {
        REGISTRY.clear();
    }
}
