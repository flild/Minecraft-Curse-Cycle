package com.example.hardcorechallenges.api.challenge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

public record ChallengeContext(
        MinecraftServer server,
        @Nullable ServerPlayerEntity player
) {
}
