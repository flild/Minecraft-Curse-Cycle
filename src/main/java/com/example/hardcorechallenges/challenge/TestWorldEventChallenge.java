package com.example.hardcorechallenges.challenge;

import com.example.hardcorechallenges.api.challenge.BaseChallenge;
import com.example.hardcorechallenges.api.challenge.ChallengeContext;
import com.example.hardcorechallenges.api.challenge.ChallengeDefinition;
import com.example.hardcorechallenges.api.challenge.ChallengeDifficulty;
import com.example.hardcorechallenges.api.challenge.ChallengeScope;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;

public class TestWorldEventChallenge extends BaseChallenge {

    public static final Identifier ID = Identifier.of("hardcorechallenges", "test_world_event");
    private boolean wasRaining = false;

    public TestWorldEventChallenge() {
        super(new ChallengeDefinition(
                ID,
                "Eternal Rain",
                "It rains non-stop while this is active.",
                "world",
                ChallengeDifficulty.HARD,
                12000,
                ChallengeScope.GLOBAL,
                List.of("world", "weather"),
                Collections.emptyList(),
                Collections.emptyList(),
                false
        ));
    }

    @Override
    public void onActivate(ChallengeContext context) {
        super.onActivate(context);
        if (context.server() != null) {
            context.server().getPlayerManager().broadcast(Text.literal("§b[!] Eternal Rain has started!"), false);
            wasRaining = context.server().getOverworld().isRaining();
            context.server().getOverworld().setWeather(0, 12000, true, false); // Start rain
        }
    }

    @Override
    public void onDeactivate(ChallengeContext context) {
        super.onDeactivate(context);
        if (context.server() != null) {
            context.server().getPlayerManager().broadcast(Text.literal("§e[!] Eternal Rain has ended!"), false);
            if (!wasRaining) {
                context.server().getOverworld().setWeather(12000, 0, false, false); // Stop rain
            }
        }
    }

    @Override
    public void tick(ChallengeContext context) {
        super.tick(context);
        if (getState() == com.example.hardcorechallenges.api.challenge.ChallengeState.ACTIVE) {
            if (context.server() != null && !context.server().getOverworld().isRaining()) {
                context.server().getOverworld().setWeather(0, 12000, true, false); // Enforce rain
            }
        }
    }
}
