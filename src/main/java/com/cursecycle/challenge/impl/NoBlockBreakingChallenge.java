package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.effect.EffectLedger;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class NoBlockBreakingChallenge extends Challenge {
    public static final String ID = "no_block_breaking";
    public NoBlockBreakingChallenge() {
        super(ID, "Не ломается, баг наверно", "Утомление X постоянно действует до конца испытания.",
                "Постоянно действует ванильное Утомление X: minecraft:mining_fatigue, amplifier 9. Эффект накладывается сразу и поддерживается без перерывов до завершения испытания. После молока он возвращается на следующем серверном тике. Все игровые действия, включая добычу блоков, подчиняются обычным правилам с этим эффектом; отдельного запрета разрушения нет. При завершении снимается только собственный вклад испытания.", ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
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
