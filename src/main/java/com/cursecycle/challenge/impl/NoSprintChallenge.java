package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class NoSprintChallenge extends Challenge {
    public static final String ID = "no_sprint";

    public NoSprintChallenge() {
        super(
            ID,
            "Без бега",
            "Запрещает бегать на спринте",
            "Пока испытание активно, игроки не могут использовать спринт (бег). Обычная ходьба работает без изменений.",
            ChallengeDifficulty.EASY,
            ChallengeScope.GLOBAL
        );
    }

    public static boolean isNoSprintActiveForPlayer(PlayerEntity player) {
        if (player == null) return false;
        if (player.getWorld().isClient()) {
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                return ClientDataHolder.getActiveChallengeIds().contains(ID);
            }
            return false;
        } else if (player instanceof ServerPlayerEntity serverPlayer) {
            Challenge challenge = ChallengeRegistry.get(ID);
            if (challenge == null || !challenge.isEnabled()) return false;
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

    @Override
    public void onStart(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) {
            player.setSprinting(false);
        } else if (server != null) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.setSprinting(false);
            }
        }
    }

    @Override
    public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) {
            player.setSprinting(false);
        }
    }

    @Override
    public void onPlayerJoin(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) {
            player.setSprinting(false);
        }
    }

    @Override
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) player.setSprinting(false);
    }
}
