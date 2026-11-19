package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.DamageReceipt;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class PacifistChallenge extends Challenge {
    public static final String ID = "pacifist";
    public static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of("cursecycle", ID));

    public PacifistChallenge() {
        super(ID, "Пацифист", "Прямой урон существам возвращается тебе.",
                "Урон от ударов и твоих снарядов получает и цель, и ты. Ловушки и огонь не отражаются. "
                        + "Возвращается потерянное целью здоровье и поглощение; броня и щит от возврата не спасают.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    /** Attribution alone is insufficient: old fire/kill credit, pets, Thorns and explosions are indirect. */
    public static ServerPlayerEntity attacker(LivingEntity target, DamageSource source) {
        if (target instanceof ArmorStandEntity || !(source.getAttacker() instanceof ServerPlayerEntity player)
                || player.isRemoved() || !ChallengeActivity.active(player, ID)) return null;
        boolean melee = source.isOf(DamageTypes.PLAYER_ATTACK) && source.getSource() == player;
        boolean projectile = source.isIn(DamageTypeTags.IS_PROJECTILE) && source.getSource() instanceof ProjectileEntity;
        return melee || projectile ? player : null;
    }

    public static void reflect(DamageReceipt receipt) {
        var player = attacker(receipt.target, receipt.source);
        if (player == null || receipt.amount() <= 0) return;
        // No attacker/projectile: this return cannot itself qualify, even in global PvP.
        var source = new DamageSource(player.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE_TYPE));
        player.damage(source, receipt.amount());
    }
}
