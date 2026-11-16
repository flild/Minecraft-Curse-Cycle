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
    private static final float DAMAGE = 2.0F;
    public static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of("cursecycle", ID));

    // Технические нюансы; не включать в описание для игрока.
    // Каждый успешный подбор порции опыта наносит DAMAGE единиц урона независимо от числа очков. Начисление
    // опыта и починка сохраняются. Прямое начисление командами и расход уровней не вызывают урон.
    public BitterExperienceChallenge() {
        super(ID, "Горький опыт",
                "Опыт теперь достаётся с болью.",
                "Каждая подобранная порция опыта отнимает здоровье. Вот он, горький опыт.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    /** Called once, after vanilla has consumed one orb portion and distributed all its XP. */
    public static void afterOrbPickup(ServerPlayerEntity player) {
        if (!ChallengeActivity.active(player, ID)) return;
        var source = new DamageSource(player.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE_TYPE));
        player.damage(source, DAMAGE);
    }
}
