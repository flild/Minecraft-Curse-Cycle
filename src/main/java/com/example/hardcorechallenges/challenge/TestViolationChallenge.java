package com.example.hardcorechallenges.challenge;

import com.example.hardcorechallenges.api.challenge.BaseChallenge;
import com.example.hardcorechallenges.api.challenge.ChallengeContext;
import com.example.hardcorechallenges.api.challenge.ChallengeDefinition;
import com.example.hardcorechallenges.api.challenge.ChallengeDifficulty;
import com.example.hardcorechallenges.api.challenge.ChallengeScope;
import com.example.hardcorechallenges.api.violation.ChallengePenalty;
import com.example.hardcorechallenges.api.violation.ChallengeViolation;
import com.example.hardcorechallenges.api.violation.DeathPenalty;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;

public class TestViolationChallenge extends BaseChallenge {

    public static final Identifier ID = Identifier.of("hardcorechallenges", "test_violation");

    public TestViolationChallenge() {
        super(new ChallengeDefinition(
                ID,
                "No Sprinting",
                "Do not sprint, or face the consequences!",
                "movement",
                ChallengeDifficulty.MEDIUM,
                6000,
                ChallengeScope.GLOBAL,
                List.of("movement", "sprint"),
                Collections.emptyList(),
                Collections.emptyList(),
                false
        ));
    }

    @Override
    public void onSprintStart(ServerPlayerEntity player) {
        if (getState() == com.example.hardcorechallenges.api.challenge.ChallengeState.ACTIVE) {
            triggerViolation(player, "Sprinting is not allowed!");
        }
    }

    @Override
    public ChallengePenalty getPenalty(ChallengeViolation violation) {
        return new DeathPenalty();
    }
}
