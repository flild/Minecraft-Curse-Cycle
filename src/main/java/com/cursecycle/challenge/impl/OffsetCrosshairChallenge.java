package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class OffsetCrosshairChallenge extends Challenge {
    public static final String ID = "offset_crosshair";
    public static final int OFFSET_X = 80;
    public static final int OFFSET_Y = 40;
    public static final int EDGE_MARGIN = 8;

    // Технические нюансы; не включать в описание для игрока.
    // Прицел смещён на 80 единиц масштабированного GUI вправо и 40 вниз. Направление взгляда, атаки, добычи и
    // использования остаётся в центре. Мир, камера, курсор меню и остальной HUD не смещаются.
    public OffsetCrosshairChallenge() {
        super(ID, "Конфига симпла",
                "Прицел съехал и теперь врёт.",
                "Прицел оказался в стороне от привычного места. Попасть по цели стало сложнее.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
}
