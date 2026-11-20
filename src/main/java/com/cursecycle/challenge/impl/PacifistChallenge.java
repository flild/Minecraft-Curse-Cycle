package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.DamageReceipt;
import com.cursecycle.challenge.DamageHooks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class PacifistChallenge extends Challenge {
    public static final String ID = "pacifist";
    public static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of("cursecycle", ID));

    public PacifistChallenge() {
        super(ID, "Пацифист",
                "Урон существам возвращается тебе.",
                "Урон от твоих ударов и снарядов получают и цель, и ты. Броня от этого не спасает.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    /** Attribution alone is insufficient: old fire/kill credit, pets, Thorns and explosions are indirect. */
    public static ServerPlayerEntity attacker(LivingEntity target, DamageSource source) {
        var player = DamageHooks.directPlayerAttacker(source);
        return target instanceof ArmorStandEntity || player == null || player.isRemoved()
                || !ChallengeActivity.active(player, ID) ? null : player;
    }

    public static void reflect(DamageReceipt receipt) {
        var player = attacker(receipt.target, receipt.source);
        if (player == null || receipt.amount() <= 0) return;
        // No attacker/projectile: this return cannot itself qualify, even in global PvP.
        var source = new DamageSource(player.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE_TYPE));
        player.damage(source, receipt.amount());
    }
}
