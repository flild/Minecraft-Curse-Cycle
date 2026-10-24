package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

public class ForcedThirdPersonRpgChallenge extends Challenge {
    public static final String ID = "forced_third_person_rpg";

    public ForcedThirdPersonRpgChallenge() {
        super(ID, "Как будто это рпг", "Вид от первого лица заблокирован.",
                "Камера насильно переключается в режим обзора от третьего лица (сзади). "
                        + "В Survival и Adventure нельзя переключиться на вид от первого лица "
                        + "или от третьего лица спереди, в том числе клавишей F5. "
                        + "Передвижение, бой, строительство и взаимодействия разрешены. "
                        + "Creative и Spectator не ограничиваются. После завершения "
                        + "восстанавливаются свободное переключение камеры и исходный вид.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }

    public static boolean affectsGameMode(GameMode mode) {
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    @Override
    public void onStart(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) player.sendMessage(Text.literal("Добро пожаловать в RPG!"), false);
    }

    @Override
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) player.sendMessage(Text.literal("Режим RPG завершён."), false);
    }

    @Override
    public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        // Disconnecting is not completion. The client restores its own camera on disconnect.
    }
}
