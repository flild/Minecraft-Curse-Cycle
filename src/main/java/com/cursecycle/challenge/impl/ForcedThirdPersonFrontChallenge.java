package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

public class ForcedThirdPersonFrontChallenge extends Challenge {
    public static final String ID = "forced_third_person_front";

    public ForcedThirdPersonFrontChallenge() {
        super(ID, "Смотри мне в глаза.", "Впереди явно что-то интересное, но пока смотри назад",
                "Испытание принудительно переключает камеру игрока на вид от третьего лица спереди. "
                        + "Все попытки изменить перспективу блокируются до завершения испытания. "
                        + "Блокировка действует только в Survival и Adventure, включая F5 и прямую смену перспективы. "
                        + "Creative и Spectator используют стандартную камеру. "
                        + "Передвижение, бой, копание и взаимодействия с миром разрешены. "
                        + "После завершения восстанавливаются свободное переключение камеры и исходный вид. "
                        + "При одновременном испытании «Как будто это рпг» приоритет имеет вид спереди.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    public static boolean affectsGameMode(GameMode mode) {
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    @Override
    public void onStart(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null && affectsGameMode(player.interactionManager.getGameMode())) {
            player.sendMessage(Text.literal("Смотри мне в глаза! Добро пожаловать в ад от третьего лица спереди"), false);
        }
    }

    @Override
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null && affectsGameMode(player.interactionManager.getGameMode())) {
            player.sendMessage(Text.literal("Испытание завершено. Камера вернулась в привычное положение"), false);
        }
    }

    @Override
    public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        // Disconnecting is not completion. The client restores its own camera on disconnect.
    }
}
