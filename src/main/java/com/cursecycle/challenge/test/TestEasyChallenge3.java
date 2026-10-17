package com.cursecycle.challenge.test;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public class TestEasyChallenge3 extends Challenge {
    public static final String ID = "test_easy_3";

    public TestEasyChallenge3() {
        super(
            ID,
            "Тестовое лёгкое испытание III",
            "Третье служебное лёгкое испытание",
            "Третье тестовое испытание лёгкой сложности для наполнения пула колеса фортуны.",
            ChallengeDifficulty.EASY,
            ChallengeScope.GLOBAL
        );
    }
}
