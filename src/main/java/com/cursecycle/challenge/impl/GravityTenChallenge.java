package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.client.ClientDataHolder;
import net.minecraft.entity.player.PlayerEntity;

/** Stateless physics gate: activation is persisted and synchronized by the shared cycle. */
public final class GravityTenChallenge extends Challenge {
    public static final String ID = "gravity_ten";
    public static final int MULTIPLIER = 3;

    // Технические нюансы; не включать в описание для игрока.
    // Гравитационное ускорение умножается на 3 без изменения стартового импульса прыжка. Медленное падение,
    // жидкости и элитры сохраняют механику, но их составляющая гравитации тоже масштабируется. Урон от падения
    // умножается.
    public GravityTenChallenge() {
        super(ID, "Как на Юпитере",
                "Гравитация стала втрое сильнее.",
                "Представь, что ты юпитире. К земле притягиваешься быстрее, а приземления стали больнее.",
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
