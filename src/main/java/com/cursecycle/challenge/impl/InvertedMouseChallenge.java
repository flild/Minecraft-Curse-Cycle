package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class InvertedMouseChallenge extends Challenge {
    public static final String ID = "inverted_mouse";

    public InvertedMouseChallenge() {
        super(ID, "Лучше бы вечная тошнота", "Мышь поворачивает камеру в противоположную сторону.",
                "Горизонтальное и вертикальное вращение камеры от мыши инвертируются относительно текущих "
                        + "пользовательских настроек. Чувствительность сохраняется. Курсор и клики в меню, "
                        + "кнопки атаки/использования и колесо прокрутки работают обычно.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }
}
