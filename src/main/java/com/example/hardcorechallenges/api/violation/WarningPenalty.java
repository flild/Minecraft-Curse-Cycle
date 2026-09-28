package com.example.hardcorechallenges.api.violation;

import com.example.hardcorechallenges.manager.ChallengeManager;
import net.minecraft.text.Text;

public class WarningPenalty implements ChallengePenalty {

    @Override
    public void apply(ChallengeViolation violation, ChallengeManager manager) {
        if (violation.player() != null) {
            violation.player().sendMessage(Text.literal("§e[Warning] " + violation.reason()), false);
        }
    }
}
