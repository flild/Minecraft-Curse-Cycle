package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;


public class NoHealingChallenge extends Challenge {
    public static final String ID = "no_healing";
    public NoHealingChallenge() {
        super(ID, "Само не заживёт", "Здоровье не восстанавливается: ни едой, ни зельями, ни яблоками.",
                "Запрещено любое увеличение текущего здоровья живого игрока: естественная регенерация, эффекты регенерации и мгновенного лечения, еда, яблоки и прямое лечение. Голод, насыщение и золотые сердца поглощения работают обычно. Возрождение создаёт игрока с обычным здоровьем, а тотем спасает с 1 единицей, но его последующая регенерация здоровья блокируется.", ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

}
