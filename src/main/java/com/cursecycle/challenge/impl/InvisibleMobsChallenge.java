package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;

public final class InvisibleMobsChallenge extends Challenge {
    public static final String ID = "invisible_mobs";

    public InvisibleMobsChallenge() {
        super(ID, "Их здесь нет", "Обычные враждебные мобы невидимы, но всё ещё опасны.",
                "Для игрока скрывается внешний вид мобов категории MONSTER, кроме дракона Края и Иссушителя. "
                        + "Скрыты их модели, броня, предметы в руках, глаза, тени, имена и светящийся контур. "
                        + "Звуки, частицы, снаряды, столкновения и атаки сохраняются. "
                        + "Мобы не получают настоящий эффект невидимости и ведут себя как обычно.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
}
