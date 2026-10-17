package com.cursecycle.challenge.test;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public class TestHardChallenge extends Challenge {
    public static final String ID = "test_hard";

    public TestHardChallenge() {
        super(
            ID,
            "Тестовое сложное испытание",
            "Служебное сложное испытание для проверки системы",
            "Это тестовое испытание высокой сложности для проверки системы колеса и интерфейсов.",
            ChallengeDifficulty.HARD,
            ChallengeScope.GLOBAL
        );
    }
}
