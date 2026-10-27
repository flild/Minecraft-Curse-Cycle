package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.client.ClientDataHolder;
import net.minecraft.entity.player.PlayerEntity;

/** Stateless physics gate: activation is persisted and synchronized by the shared cycle. */
public final class GravityTenChallenge extends Challenge {
    public static final String ID = "gravity_ten";
    public static final int MULTIPLIER = 3;

    public GravityTenChallenge() {
        super(ID, "Земля тянет к себе", "Гравитационное ускорение игрока увеличено в 3 раз.",
                "Действующее на игрока ускорение тяжести умножается на 3. Прыжок сохраняет исходный стартовый импульс, но быстрее гасится; падение ускоряется. Медленное падение, жидкости и элитры сохраняют свою механику, однако их собственная составляющая гравитационного ускорения тоже умножается на 3. Урон от падения умножается.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    public static boolean active(PlayerEntity player) {
        if (!player.isAlive() || player.isRemoved() || player.isCreative() || player.isSpectator()
                || player.hasVehicle()) return false;
        if (player.getWorld().isClient)
            return ClientDataHolder.isSynced() && ClientDataHolder.getActiveChallengeIds().contains(ID);
        return ChallengeActivity.active(player, ID);
    }
}
