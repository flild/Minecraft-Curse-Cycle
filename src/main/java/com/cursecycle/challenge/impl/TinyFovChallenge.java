package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class TinyFovChallenge extends Challenge {
    public static final String ID = "tiny_fov";
    public static final double FOV_DEGREES = 15.0;

    // Технические нюансы; не включать в описание для игрока.
    // Поле зрения фиксируется на 15 градусах во всех перспективах. Ванильные FOV-модификаторы, включая спринт,
    // скорость, лук и подзорную трубу, не влияют. Настройки пользователя и масштаб интерфейса сохраняются.
    public TinyFovChallenge() {
        super(ID, "Тесновато",
                "Смотришь на мир будто через узкую трубку.",
                "Обзор сузился до крошечного окошка. По сторонам почти ничего не видно.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }
}
