package com.cursecycle.challenge.test;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public class TestEasyChallenge2 extends Challenge {
    public static final String ID = "test_easy_2";

    public TestEasyChallenge2() {
        super(
            ID,
            "Тестовое лёгкое испытание II",
            "Второе служебное лёгкое испытание",
            "Второе тестовое испытание лёгкой сложности для наполнения пула колеса фортуны.",
            ChallengeDifficulty.EASY,
            ChallengeScope.GLOBAL
        );
    }
}
