package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class OffsetCrosshairChallenge extends Challenge {
    public static final String ID = "offset_crosshair";
    public static final int OFFSET_X = 80;
    public static final int OFFSET_Y = 40;
    public static final int EDGE_MARGIN = 8;

    public OffsetCrosshairChallenge() {
        super(ID, "Конфига симпла", "Прицел визуально смещён вправо и вниз, настоящий выстрел идёт из центра.",
                "Видимый прицел смещается на 80 единиц масштабированного GUI вправо и 40 вниз от центра экрана. "
                        + "Настоящее направление взгляда, атаки, добычи и использования остаётся центральным. "
                        + "Мир, камера, курсор меню и остальной HUD не сдвигаются.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
}
