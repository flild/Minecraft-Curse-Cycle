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

    // Технические нюансы; не включать в описание для игрока.
    // В Survival/Adventure фиксируется задний вид от третьего лица; первый и передний виды недоступны, включая
    // F5. Игровые действия не ограничены. Creative и Spectator свободны. После завершения восстанавливаются
    // исходный вид и свободное переключение.
    public ForcedThirdPersonRpgChallenge() {
        super(ID, "Как будто это рпг",
                "Теперь смотришь на себя со стороны.",
                "Камера остаётся за спиной до конца испытания. Вид от первого лица пока недоступен.",
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
