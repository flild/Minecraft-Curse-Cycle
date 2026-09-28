package com.example.hardcorechallenges.api.violation;

import com.example.hardcorechallenges.manager.ChallengeManager;

public interface ChallengePenalty {
    void apply(ChallengeViolation violation, ChallengeManager manager);
}
