package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class InvertedMouseChallenge extends Challenge {
    public static final String ID = "inverted_mouse";

    // Технические нюансы; не включать в описание для игрока.
    // Обе оси вращения инвертируются относительно текущих пользовательских настроек. Чувствительность
    // сохраняется. Курсор и клики в меню, атака, использование и колесо работают штатно.
    public InvertedMouseChallenge() {
        super(ID, "Лучше бы вечная тошнота",
                "Камера поворачивается в другую сторону.",
                "Мышь больше не слушается привычек: и вверх-вниз, и влево-вправо теперь наоборот.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }
}
