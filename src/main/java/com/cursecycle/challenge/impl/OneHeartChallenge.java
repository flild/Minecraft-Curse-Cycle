package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.client.ClientDataHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class OneHeartChallenge extends Challenge {
    public static final String ID = "one_heart";
    public OneHeartChallenge() {
        super(ID, "Последнее сердечко", "Максимум одно сердце; дополнительные сердца не выдаются.",
                "Доступно максимум 2 единицы здоровья — одно сердце. При активации текущее здоровье обрезается до этого предела без исцеления. Дополнительные сердца поглощения недоступны, а увеличение максимального здоровья не поднимает предел. После завершения обычный максимум возвращается, потерянное здоровье не восстанавливается.", ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }
    public static boolean active(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity p)
            return ((HealthInitialization) p).cursecycle$healthInitialized() && ChallengeActivity.active(p, ID);
        return player.getWorld().isClient() && player.getAbilities() != null && player.isAlive()
                && !player.isCreative() && !player.isSpectator() && ClientDataHolder.isSynced()
                && ClientDataHolder.getActiveChallengeIds().contains(ID);
    }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { onTick(server, player); }
    @Override public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null && active(player)) {
            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
            player.setAbsorptionAmount(0);
        }
    }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) { onStart(server, newPlayer); }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) { }
}
