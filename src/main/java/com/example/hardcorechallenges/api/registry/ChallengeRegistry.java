package com.example.hardcorechallenges.api.registry;

import com.example.hardcorechallenges.api.challenge.Challenge;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ChallengeRegistry {
    private static final Map<Identifier, Supplier<Challenge>> REGISTRY = new HashMap<>();

    public static void register(Challenge challenge) {
        REGISTRY.put(challenge.getDefinition().id(), () -> challenge);
    }

    public static void register(Supplier<Challenge> challengeSupplier) {
        Challenge dummy = challengeSupplier.get();
        REGISTRY.put(dummy.getDefinition().id(), challengeSupplier);
    }

    public static Challenge createChallenge(Identifier id) {
        Supplier<Challenge> supplier = REGISTRY.get(id);
        return supplier != null ? supplier.get() : null;
    }

    public static Collection<Identifier> getRegisteredIds() {
        return REGISTRY.keySet();
    }

    public static Collection<Supplier<Challenge>> getAll() {
        return REGISTRY.values();
    }
}
