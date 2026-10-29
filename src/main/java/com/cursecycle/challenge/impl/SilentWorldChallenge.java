package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class SilentWorldChallenge extends Challenge {
    public static final String ID = "silent_world";

    public SilentWorldChallenge() {
        super(ID, "Тихий час", "Все звуки Minecraft отключены.",
                "Для игроков полностью отключается вывод звука Minecraft: окружение, существа, шаги, интерфейс, музыка и звуки мода. "
                        + "Значения громкости в настройках не переписываются. Изображение, субтитры, звук внешних приложений "
                        + "и голос вне звукового движка Minecraft не меняются.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }
}
