package com.cursecycle.challenge.test;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public class TestMediumChallenge extends Challenge {
    public static final String ID = "test_medium";

    public TestMediumChallenge() {
        super(
            ID,
            "Тестовое среднее испытание",
            "Служебное среднее испытание для проверки системы",
            "Это тестовое испытание средней сложности. Используется для проверки алгоритма выборки и визуализации на колесе.",
            ChallengeDifficulty.MEDIUM,
            ChallengeScope.GLOBAL
        );
    }
}
