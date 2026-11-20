package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class BlindHudChallenge extends Challenge {
    public static final String ID = "blind_hud";

    // Технические нюансы; не включать в описание для игрока.
    // Скрываются индикаторы здоровья, поглощения, брони, голода, воздуха, уровня и полоски опыта без изменения
    // значений, а также хотбар с предметами и слотом второй руки. Прицел, чат, эффекты, меню и HUD испытаний
    // остаются доступны. Выбор и использование предметов работают как обычно. Настройки F1 и
    // интерфейса не переписываются.
    public BlindHudChallenge() {
        super(ID, "На ощупь",
                "Здоровье, голод, броня, опыт и хотбар скрыты.",
                "Привычные шкалы и хотбар исчезли: ни сердец, ни голода, ни брони, ни воздуха, ни опыта, ни панели предметов.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }
}
