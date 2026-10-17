package com.cursecycle.challenge.test;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public class TestMediumChallenge2 extends Challenge {
    public static final String ID = "test_medium_2";

    public TestMediumChallenge2() {
        super(
            ID,
            "Тестовое среднее испытание II",
            "Второе служебное среднее испытание",
            "Второе тестовое испытание средней сложности.",
            ChallengeDifficulty.MEDIUM,
            ChallengeScope.GLOBAL
        );
    }
}
