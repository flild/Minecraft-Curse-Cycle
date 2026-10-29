package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class TinyFovChallenge extends Challenge {
    public static final String ID = "tiny_fov";
    public static final double FOV_DEGREES = 15.0;

    public TinyFovChallenge() {
        super(ID, "Тесновато", "Поле зрения сужено до 15 градусов.",
                "Эффективное поле зрения игровой камеры фиксировано на 15 градусах во всех перспективах. "
                        + "Спринт, скорость, натягивание лука, подзорная труба и другие ванильные FOV-модификаторы "
                        + "не расширяют и не сужают его. Настройки пользователя и масштаб интерфейса сохраняются.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }
}
