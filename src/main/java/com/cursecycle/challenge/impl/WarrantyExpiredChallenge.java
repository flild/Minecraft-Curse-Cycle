package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class WarrantyExpiredChallenge extends Challenge {
    public static final String ID = "warranty_expired";
    public static final int WEAR_MULTIPLIER = 3;

    public WarrantyExpiredChallenge() {
        super(ID, "Гарантия закончилась",
                "Используемые предметы и надетая броня изнашиваются втрое быстрее.",
                "Обычный расход прочности при использовании предметов и повреждении надетой брони умножается на 3. "
                        + "Прочность и Починка работают как обычно; неразрушимые предметы не ломаются. "
                        + "Лежащие без использования предметы не портятся. Потраченная прочность после окончания не возвращается.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
}
