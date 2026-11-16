package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class UpsideDownCameraChallenge extends Challenge {
    public static final String ID = "upside_down_camera";
    public static final float ROLL_DEGREES = 180f;

    // Технические нюансы; не включать в описание для игрока.
    // В Survival/Adventure мир поворачивается на 180 градусов вокруг оси взгляда. HUD, меню, текст, прицел и
    // курсор не поворачиваются. Направление взгляда, луч взаимодействия и управление не инвертируются; смена
    // перспективы не убирает переворот.
    public UpsideDownCameraChallenge() {
        super(ID, "С ног на голову",
                "Весь мир вверх ногами.",
                "Небо оказалось под ногами, а земля над головой. Так теперь выглядит мир до конца испытания.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
}
