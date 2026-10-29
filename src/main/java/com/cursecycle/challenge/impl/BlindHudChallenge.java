package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class BlindHudChallenge extends Challenge {
    public static final String ID = "blind_hud";

    public BlindHudChallenge() {
        super(ID, "На ощупь", "Здоровье, голод, броня, воздух и опыт скрыты.",
                "Скрываются индикаторы собственного здоровья, поглощения, брони, голода, воздуха, уровня и полоски опыта. "
                        + "Реальные значения не меняются. Хотбар, предметы, прицел, чат, эффекты зелий, игровые меню и HUD испытаний остаются доступными. "
                        + "Настройка F1 и пользовательские настройки интерфейса не переписываются.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }
}
