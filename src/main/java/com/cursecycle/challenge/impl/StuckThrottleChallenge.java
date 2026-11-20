package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class StuckThrottleChallenge extends Challenge {
    public static final String ID = "stuck_throttle";

    public StuckThrottleChallenge() {
        super(ID, "Газ газ газ",
                "Только вперед и только газ.",
                "Ты идёшь вперёд и не можешь остановиться. ",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
}
