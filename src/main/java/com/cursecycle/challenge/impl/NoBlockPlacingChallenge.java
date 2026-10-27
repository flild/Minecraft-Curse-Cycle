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

    public NoBlockPlacingChallenge() {
        super(ID, "Ну ты же не строитель", "Нельзя ставить никакие блоки, включая жидкости и огонь.",
                "Запрещено напрямую устанавливать любые блоки: строительные, функциональные, растения, "
                        + "жидкости из вёдер, рыхлый снег и огонь. Попытка отменяется до изменения мира "
                        + "и расхода предмета. Открывать двери, пользоваться верстаками и контейнерами, "
                        + "ломать блоки и размещать сущности разрешено.",
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
