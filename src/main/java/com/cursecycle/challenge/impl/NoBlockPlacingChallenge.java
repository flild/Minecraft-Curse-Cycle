package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.client.ClientDataHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

public final class NoBlockPlacingChallenge extends Challenge {
    public static final String ID = "no_block_placing";

    // Технические нюансы; не включать в описание для игрока.
    // Запрещена прямая установка строительных и функциональных блоков, растений, жидкостей из вёдер, рыхлого
    // снега и огня. Попытка отменяется до изменения мира и расхода предмета. Двери, верстаки, контейнеры,
    // разрушение и размещение сущностей не ограничиваются.
    public NoBlockPlacingChallenge() {
        super(ID, "Ну ты же не строитель",
                "Поставить блок больше не получится.",
                "Строительство отменяется: ни блок поставить, ни воду вылить, ни огонь разжечь.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }

    /** The same item hooks prevent client prediction; only the server decides actual placement. */
    public static boolean blocks(PlayerEntity player) {
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) return false;
        if (!player.getWorld().isClient()) return ChallengeActivity.active(player, ID);
        // ClientDataHolder contains only common Java types; no client classes are loaded on a server.
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                && ChallengeRegistry.get(ID) != null && ChallengeRegistry.get(ID).isEnabled()
                && ClientDataHolder.getActiveChallengeIds().contains(ID);
    }
}
