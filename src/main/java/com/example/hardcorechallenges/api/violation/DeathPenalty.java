package com.example.hardcorechallenges.api.violation;

import com.example.hardcorechallenges.api.challenge.GameMode;
import com.example.hardcorechallenges.manager.ChallengeManager;
import net.minecraft.text.Text;

public class DeathPenalty implements ChallengePenalty {

    @Override
    public void apply(ChallengeViolation violation, ChallengeManager manager) {
        if (violation.player() == null) return;

        GameMode mode = manager.getGameMode();
        if (mode == GameMode.LIGHT) {
            // Replace death with damage or warning in light mode
            violation.player().sendMessage(Text.literal("§c[!] You violated a rule! You would have died in harder modes."), false);
            violation.player().damage(violation.player().getDamageSources().magic(), 4.0f);
        } else {
            // Hardcore / Classic / Chaos: Death
            violation.player().kill();
            violation.player().sendMessage(Text.literal("§4[!] You died due to violating challenge: " + violation.challengeId().getPath()), false);
        }
    }
}
