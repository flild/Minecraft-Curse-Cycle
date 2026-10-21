package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class NoJumpingChallenge extends Challenge {
    public static final String ID = "no_jumping";

    public NoJumpingChallenge() {
        super(
            ID,
            "Прикованные к земле",
            "Прыжки полностью заблокированы.",
            "Игроки теряют способность совершать обычные прыжки.",
            ChallengeDifficulty.EASY,
            ChallengeScope.GLOBAL
        );
    }

    public static boolean isNoJumpingActiveForPlayer(PlayerEntity player) {
        if (player == null) return false;
        if (player.isCreative() || player.isSpectator()) return false;

        if (player.getWorld().isClient()) {
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                return ClientDataHolder.getActiveChallengeIds().contains(ID);
            }
            return false;
        } else if (player instanceof ServerPlayerEntity serverPlayer) {
            MinecraftServer server = serverPlayer.getServer();
            if (server == null) return false;

            CurseCycleState state = CurseCycleState.getServerState(server);

            if (state.getActiveChallengeIds().contains(ID)) {
                return true;
            }

            return state.getPersonalActiveChallengeIds(serverPlayer.getUuid()).contains(ID);
        }
        return false;
    }
}
