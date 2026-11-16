package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class BlindHudChallenge extends Challenge {
    public static final String ID = "blind_hud";

    // Технические нюансы; не включать в описание для игрока.
    // Скрываются индикаторы здоровья, поглощения, брони, голода, воздуха, уровня и полоски опыта без изменения
    // значений. Хотбар, предметы, прицел, чат, эффекты, меню и HUD испытаний остаются доступны. Настройки F1 и
    // интерфейса не переписываются.
    public BlindHudChallenge() {
        super(ID, "На ощупь",
                "Здоровье, голод, броня и опыт скрыты.",
                "Привычные шкалы исчезли: ни сердец, ни голода, ни брони, ни воздуха, ни опыта. О своём состоянии придётся судить на ощупь.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }
}
