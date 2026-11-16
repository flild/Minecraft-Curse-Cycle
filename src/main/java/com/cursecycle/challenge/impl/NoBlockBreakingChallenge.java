package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.effect.EffectLedger;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class NoBlockBreakingChallenge extends Challenge {
    public static final String ID = "no_block_breaking";
    // Технические нюансы; не включать в описание для игрока.
    // Постоянно поддерживается Утомление X: minecraft:mining_fatigue, amplifier 9. После молока эффект
    // возвращается на следующем серверном тике. Отдельного запрета разрушения нет, действуют обычные правила
    // добычи с эффектом. При завершении снимается только вклад испытания.
    public NoBlockBreakingChallenge() {
        super(ID, "Не ломается, баг наверно",
                "Блоки добываются мучительно медленно.",
                "Руки будто налились свинцом. Из-за сильного утомления даже простой блок добывать непривычно долго.", ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { onTick(server, player); }
    @Override public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) EffectLedger.of(player).maintain(ID, StatusEffects.MINING_FATIGUE, 9, 40, 20);
    }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) EffectLedger.of(player).remove(ID);
    }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) { onStop(server, player); }
}
