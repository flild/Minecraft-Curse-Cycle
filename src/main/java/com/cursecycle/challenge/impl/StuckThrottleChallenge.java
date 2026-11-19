package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class StuckThrottleChallenge extends Challenge {
    public static final String ID = "stuck_throttle";

    public StuckThrottleChallenge() {
        super(ID, "Газ заклинило",
                "Ты постоянно идёшь вперёд, даже отпустив клавиши.",
                "Движение вперёд не остановить клавишами, даже в меню, воде и транспорте. "
                        + "Поворачивай, прыгай и взаимодействуй как обычно. Приседание замедляет ход, стены останавливают. "
                        + "Спринт запускается только клавишей бега. Принудительный ход сильнее инверсии, памяти и запрета вперёд.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
}
