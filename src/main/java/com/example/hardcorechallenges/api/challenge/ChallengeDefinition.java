package com.example.hardcorechallenges.api.challenge;

import net.minecraft.util.Identifier;

public record ChallengeDefinition(
        Identifier id,
        String name,
        String description,
        String category,
        int difficulty,
        int durationTicks,
        ChallengeScope scope
) {
}
