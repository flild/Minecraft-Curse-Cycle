package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Server-owned, vanilla tracked attributes; no saved size or client-local activation guesses. */
public final class TinyPlayerChallenge extends Challenge {
    public static final String ID = "tiny_player";
    public static final double JUMP = .7, RANGE = .6;
    public static final Identifier SCALE_MODIFIER = Identifier.of("cursecycle", "tiny_player/scale");
    public static final Identifier JUMP_MODIFIER = Identifier.of("cursecycle", "tiny_player/jump");
    public static final Identifier BLOCK_MODIFIER = Identifier.of("cursecycle", "tiny_player/block_range");
    public static final Identifier ENTITY_MODIFIER = Identifier.of("cursecycle", "tiny_player/entity_range");

    public TinyPlayerChallenge() {
        super(ID, "Мелкий, но проблемный", "Рост один блок, ниже прыжки и меньше дальность взаимодействия.",
                "Твой рост — один блок: можно пройти под низким потолком. Прыжок слабее, а до блоков и существ "
                        + "нужно подойти ближе. После окончания в тесноте ты ползёшь до выхода на простор.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    public static double scaleFactor(PlayerEntity player) {
        return 1.0 / player.getBaseDimensions(EntityPose.STANDING).height();
    }

    /** Also reconciles giant_player atomically; tiny_player has priority. */
    public static void reconcile(PlayerEntity player) { PlayerSizeAttributes.reconcile(player); }

    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { reconcile(player); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) { PlayerSizeAttributes.stop(player, ID); }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        PlayerSizeAttributes.apply(player, false, false);
    }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) {
        PlayerSizeAttributes.apply(oldPlayer, false, false);
        reconcile(newPlayer);
    }
}
