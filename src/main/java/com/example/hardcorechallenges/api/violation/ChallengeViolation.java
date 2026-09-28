package com.example.hardcorechallenges.api.violation;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record ChallengeViolation(
        Identifier challengeId,
        @Nullable ServerPlayerEntity player,
        String reason
) {
}
