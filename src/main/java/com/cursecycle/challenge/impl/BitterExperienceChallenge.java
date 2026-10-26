package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class BitterExperienceChallenge extends Challenge {
    public static final String ID = "bitter_experience";
    public static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of("cursecycle", ID));

    public BitterExperienceChallenge() {
        super(ID, "Горький опыт", "Подбор сферы опыта наносит полсердца урона.",
                "Каждый успешный ванильный подбор порции сферы опыта вызывает один запрос урона "
                        + "на 1 единицу после обычной починки предметов и начисления опыта. "
                        + "Номинал порции не увеличивает урон; полностью потраченный на починку опыт "
                        + "тоже вызывает запрос. Объединённые сферы учитываются по отдельно подобранным порциям. "
                        + "Броня не снижает этот урон; сопротивление, поглощение, тотемы и обычная "
                        + "неуязвимость после удара сохраняются. Притяжение и несостоявшийся подбор, "
                        + "прямое начисление опыта и расход уровней урона не вызывают. "
                        + "Сферы после собственной смерти учитываются на общих основаниях. "
                        + "Действует на живых игроков Survival/Adventure во всех измерениях; "
                        + "Creative и Spectator исключены. Завершение отключает будущие запросы "
                        + "и не возвращает здоровье, опыт или состояние предметов.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    /** Called once, after vanilla has consumed one orb portion and distributed all its XP. */
    public static void afterOrbPickup(ServerPlayerEntity player) {
        if (!ChallengeActivity.active(player, ID)) return;
        var source = new DamageSource(player.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE_TYPE));
        player.damage(source, 1.0F);
    }
}
