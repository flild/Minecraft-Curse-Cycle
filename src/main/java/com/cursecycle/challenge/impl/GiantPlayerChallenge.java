package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Vanilla tracked size, reach and step; tiny_player takes priority when both are active. */
public final class GiantPlayerChallenge extends Challenge {
    public static final String ID = "giant_player";
    public static final double SCALE = 2, RANGE = 1.5, STEP = 2;
    public static final Identifier SCALE_MODIFIER = Identifier.of("cursecycle", "giant_player/scale");
    public static final Identifier BLOCK_MODIFIER = Identifier.of("cursecycle", "giant_player/block_range");
    public static final Identifier ENTITY_MODIFIER = Identifier.of("cursecycle", "giant_player/entity_range");
    public static final Identifier STEP_MODIFIER = Identifier.of("cursecycle", "giant_player/step");

    public GiantPlayerChallenge() {
        super(ID, "Большой и неудобный", "Размер вдвое больше, дальше взаимодействие и выше шаг.",
                "Ты вдвое больше: обычные двери и проходы шириной в блок слишком тесны. Дальность "
                        + "взаимодействия ×1,5, высота шага ×2. В тесноте рост ждёт свободного места. "
                        + "Если действует «Мелкий, но проблемный», его размер и ограничения имеют приоритет.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    public static void reconcile(PlayerEntity player) { PlayerSizeAttributes.reconcile(player); }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { reconcile(player); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        PlayerSizeAttributes.stop(player, ID);
    }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        PlayerSizeAttributes.apply(player, false, false);
    }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) {
        PlayerSizeAttributes.apply(oldPlayer, false, false);
        reconcile(newPlayer);
    }
}
