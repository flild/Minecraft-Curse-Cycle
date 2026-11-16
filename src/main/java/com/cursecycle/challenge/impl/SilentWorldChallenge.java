package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class SilentWorldChallenge extends Challenge {
    public static final String ID = "silent_world";

    // Технические нюансы; не включать в описание для игрока.
    // Отключается вывод всех звуков Minecraft, включая интерфейс и мод. Настройки громкости не меняются.
    // Субтитры, изображение, внешние приложения и голос вне звукового движка Minecraft не затрагиваются.
    public SilentWorldChallenge() {
        super(ID, "Тихий час",
                "Все звуки Minecraft отключены.",
                "Ни шагов, ни музыки, ни шипения за спиной — до конца испытания полная тишина.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }
}
