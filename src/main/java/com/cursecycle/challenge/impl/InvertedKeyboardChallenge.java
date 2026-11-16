package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class InvertedKeyboardChallenge extends Challenge {
    public static final String ID = "inverted_keyboard";

    // Технические нюансы; не включать в описание для игрока.
    // Инвертируются текущие назначения движения, не фиксированные коды WASD. Прыжок, приседание, спринт,
    // инвентарь и текст не меняются.
    public InvertedKeyboardChallenge() {
        super(ID, "Да как ходить то!",
                "Вперёд и назад, влево и вправо поменялись местами.",
                "Жмёшь вперёд — идёшь назад, жмёшь влево — идёшь вправо. Ноги всё поняли наоборот.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }
}
