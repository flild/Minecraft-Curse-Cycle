package com.cursecycle.challenge;

import com.cursecycle.challenge.impl.ArmorDisabledChallenge;
import com.cursecycle.challenge.impl.NoJumpingChallenge;
import com.cursecycle.challenge.impl.NoSprintChallenge;
import com.cursecycle.challenge.special.DoubleChallenge;
import com.cursecycle.challenge.test.*;

public class ModChallenges {
    public static void registerAll() {
        ChallengeRegistry.register(new ArmorDisabledChallenge());
        ChallengeRegistry.register(new NoJumpingChallenge());
        ChallengeRegistry.register(new NoSprintChallenge());
        ChallengeRegistry.register(new TestEasyChallenge());
        ChallengeRegistry.register(new TestEasyChallenge2());
        ChallengeRegistry.register(new TestEasyChallenge3());
        ChallengeRegistry.register(new TestMediumChallenge());
        ChallengeRegistry.register(new TestMediumChallenge2());
        ChallengeRegistry.register(new TestHardChallenge());
        ChallengeRegistry.register(new DoubleChallenge());
    }
}
