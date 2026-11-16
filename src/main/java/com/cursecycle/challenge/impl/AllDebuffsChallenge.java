package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.effect.EffectLedger;
import net.minecraft.entity.effect.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.List;

public class AllDebuffsChallenge extends Challenge {
    public static final String ID = "all_debuffs";
    // Технические нюансы; не включать в описание для игрока.
    // Используются ванильные эффекты категории HARMFUL I уровня, кроме иссушения и мгновенного урона.
    // Продолжительные эффекты поддерживаются постоянно и после молока возвращаются на следующем серверном
    // тике.
    public AllDebuffsChallenge() {
        super(ID, "Собрал все болячки",
                "Целый букет неприятных эффектов сразу.",
                "Слабость, медлительность, тошнота и прочие неприятности теперь с тобой до конца испытания.", ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }
    public static List<RegistryEntry.Reference<StatusEffect>> effects() {
        return Registries.STATUS_EFFECT.streamEntries().filter(entry -> {
            var key = entry.registryKey().getValue();
            return key.getNamespace().equals("minecraft") && entry.value().getCategory() == StatusEffectCategory.HARMFUL
                    && !entry.matchesKey(StatusEffects.INSTANT_DAMAGE.getKey().orElseThrow())
                    && !entry.matchesKey(StatusEffects.WITHER.getKey().orElseThrow());
        }).toList();
    }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { onTick(server, player); }
    @Override public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return;
        if (!ChallengeActivity.active(player, ID)) { EffectLedger.of(player).remove(ID); return; }
        for (var effect : effects()) EffectLedger.of(player).maintain(ID, effect, 0, 40, 20);
    }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) EffectLedger.of(player).remove(ID);
    }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) { onStop(server, player); }
}
