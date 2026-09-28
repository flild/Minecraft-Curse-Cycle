package com.example.hardcorechallenges.api.challenge;

import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;

public record ChallengeDefinition(
        Identifier id,
        String name,
        String description,
        String category,
        ChallengeDifficulty difficulty,
        int durationTicks,
        ChallengeScope scope,
        List<String> tags,
        List<Identifier> conflicts,
        List<Identifier> requirements,
        boolean allowMultiple
) {
    public ChallengeDefinition(Identifier id, String name, String description, String category, ChallengeDifficulty difficulty, int durationTicks, ChallengeScope scope) {
        this(id, name, description, category, difficulty, durationTicks, scope, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false);
    }
}
