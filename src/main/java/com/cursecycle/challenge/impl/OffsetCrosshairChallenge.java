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
    // Прицел смещён на 80 единиц масштабированного GUI вправо и 40 вниз. Off-axis проекция мира и руки
    // совмещает ванильный луч действий и выстрелы со смещённой отметкой; yaw/pitch и пакеты действий ванильные.
    public OffsetCrosshairChallenge() {
        super(ID, "Конфига симпла",
                "Прицел и направление действий смещены от центра экрана.",
                "Добыча блоков, удары и выстрелы направлены в смещённый прицел справа и снизу от центра экрана.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
}
