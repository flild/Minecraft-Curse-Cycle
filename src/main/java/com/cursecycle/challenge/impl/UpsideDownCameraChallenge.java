package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class UpsideDownCameraChallenge extends Challenge {
    public static final String ID = "upside_down_camera";
    public static final float ROLL_DEGREES = 180f;

    public UpsideDownCameraChallenge() {
        super(ID, "С ног на голову", "Камера перевёрнута на 180°, интерфейс остаётся обычным.",
                "В Survival и Adventure изображение мира повёрнуто на 180 градусов вокруг оси взгляда. "
                        + "HUD, меню, текст, прицел и курсор остаются в обычной ориентации. "
                        + "Направление взгляда, луч взаимодействия и управление сами по себе не инвертируются; "
                        + "смена перспективы не убирает переворот.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
}
