package com.cursecycle.challenge.special;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public class DoubleChallenge extends Challenge {
    public static final String ID = "double_challenge";

    public DoubleChallenge() {
        super(
            ID,
            "Два испытания одновременно",
            "Действуют два случайных испытания",
            "Специальное испытание. Выбирает два различных обычных испытания из пула, которые действуют одновременно в течение этого периода.",
            ChallengeDifficulty.HARD,
            ChallengeScope.GLOBAL
        );
    }
}
