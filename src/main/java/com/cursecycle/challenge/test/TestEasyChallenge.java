package com.cursecycle.challenge.test;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public class TestEasyChallenge extends Challenge {
    public static final String ID = "test_easy";

    public TestEasyChallenge() {
        super(
            ID,
            "Тестовое лёгкое испытание",
            "Служебное лёгкое испытание для проверки системы",
            "Это тестовое испытание лёгкой сложности. Используется только для проверки выборки, вращения колеса и смены испытаний.",
            ChallengeDifficulty.EASY,
            ChallengeScope.GLOBAL
        );
    }
}
