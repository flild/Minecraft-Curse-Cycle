package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class InvertedKeyboardChallenge extends Challenge {
    public static final String ID = "inverted_keyboard";

    public InvertedKeyboardChallenge() {
        super(ID, "Да как ходить то!", "Вперёд и назад, влево и вправо поменялись местами.",
                "Назначения движения вперёд/назад и влево/вправо временно меняются местами. "
                        + "По умолчанию W движет назад, S вперёд, A вправо, D влево. "
                        + "Используются текущие назначенные клавиши, а не фиксированные коды WASD. "
                        + "Прыжок, приседание, спринт, инвентарь и текст не меняются.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }
}
