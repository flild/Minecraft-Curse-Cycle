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

    // Технические нюансы; не включать в описание для игрока.
    // В Survival/Adventure фиксируется вид от третьего лица спереди; F5 и прямая смена перспективы
    // блокируются. Creative и Spectator свободны. Игровые действия не ограничены. После завершения
    // возвращаются исходный вид и переключение камеры. При совмещении с forced_third_person_rpg вид спереди
    // имеет приоритет.
    public ForcedThirdPersonFrontChallenge() {
        super(ID, "Смотри мне в глаза.",
                "Впереди явно что-то интересное, но пока смотри назад.",
                "Камера застряла перед лицом. До конца испытания придётся смотреть на себя, а не туда, куда идёшь.",
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
