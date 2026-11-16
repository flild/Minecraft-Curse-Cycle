package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;


public class NoHealingChallenge extends Challenge {
    public static final String ID = "no_healing";
    // Технические нюансы; не включать в описание для игрока.
    // Блокируется любое увеличение текущего здоровья живого игрока, включая регенерацию, мгновенное и прямое
    // лечение. Голод, насыщение и поглощение работают штатно. Респаун даёт обычное здоровье; тотем спасает с
    // одной единицей, но последующая регенерация блокируется.
    public NoHealingChallenge() {
        super(ID, "Само не заживёт",
                "Потерянные сердца больше не востанавливаются.",
                "Раны не заживают. Ни еда, ни зелья, ни золотые яблоки не восстановят потерянное здоровье.", ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

}
