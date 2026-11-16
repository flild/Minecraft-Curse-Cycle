package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class InvisibleMobsChallenge extends Challenge {
    public static final String ID = "invisible_mobs";

    // Технические нюансы; не включать в описание для игрока.
    // Скрывается рендер категории MONSTER, кроме дракона Края и Иссушителя: модели, броня, предметы, глаза,
    // тени, имена и светящийся контур. Звуки, частицы, снаряды, столкновения и атаки сохраняются. Настоящий
    // статус невидимости не выдаётся; ИИ не меняется.
    public InvisibleMobsChallenge() {
        super(ID, "Их здесь нет",
                "Все враждебные мобы невидимые",
                "Враждебные мобы исчезли из виду. Вот только нападать на тебя они не перестали.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
}
