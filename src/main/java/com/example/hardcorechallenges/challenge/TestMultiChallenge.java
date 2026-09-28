package com.example.hardcorechallenges.challenge;

import com.example.hardcorechallenges.api.challenge.BaseChallenge;
import com.example.hardcorechallenges.api.challenge.ChallengeDefinition;
import com.example.hardcorechallenges.api.challenge.ChallengeDifficulty;
import com.example.hardcorechallenges.api.challenge.ChallengeScope;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;

public class TestMultiChallenge extends BaseChallenge {

    public static final Identifier ID = Identifier.of("hardcorechallenges", "test_multi");

    public TestMultiChallenge() {
        super(new ChallengeDefinition(
                ID,
                "No Jumping",
                "Do not jump! Just a warning though.",
                "movement",
                ChallengeDifficulty.EASY,
                6000,
                ChallengeScope.GLOBAL,
                List.of("movement", "jump"),
                List.of(Identifier.of("hardcorechallenges", "conflicting_challenge")), // Пример конфликта
                Collections.emptyList(),
                false
        ));
    }

    @Override
    public void onJump(ServerPlayerEntity player) {
        if (getState() == com.example.hardcorechallenges.api.challenge.ChallengeState.ACTIVE) {
            player.sendMessage(Text.literal("§e[No Jumping] You jumped! Watch out!"), true);
        }
    }
}
