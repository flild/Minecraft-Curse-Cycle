package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.effect.EffectLedger;
import com.cursecycle.state.EndlessRaidState;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;

public final class EndlessRaidChallenge extends Challenge {
    public static final String ID = "endless_raid";

    public EndlessRaidChallenge() {
        super(ID, "Рейд без выходных", "У деревень рейды возобновляются после завершения предыдущих.",
                "Пока испытание активно, игроки Survival/Adventure постоянно провоцируют ванильные рейды при посещении подходящих деревень. После завершения одного рейда следующий запускается через обычную ванильную подготовку. Вдали от деревень искусственные волны не создаются. Одну деревню обслуживает один рейд; при окончании испытания его собственные незавершённые рейды прекращаются, но уже появившиеся разбойники остаются в мире.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    public static boolean eligible(ServerPlayerEntity player) {
        var world = player.getServerWorld();
        return ChallengeActivity.active(player, ID) && world.getDifficulty() != Difficulty.PEACEFUL
                && !world.getGameRules().getBoolean(GameRules.DISABLE_RAIDS) && world.getDimension().hasRaids();
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        for (var player : server.getPlayerManager().getPlayerList()) check(player);
    }

    public static void check(ServerPlayerEntity player) {
        var ledger = EffectLedger.of(player);
        ledger.reconcile();
        if (!eligible(player)) { ledger.remove(ID); return; }
        // Includes vanilla victory/loss celebration: wait until vanilla removes the old raid.
        if (player.getServerWorld().getRaidAt(player.getBlockPos()) != null) {
            ledger.remove(ID);
            return;
        }
        if (!player.hasStatusEffect(StatusEffects.BAD_OMEN) && !player.hasStatusEffect(StatusEffects.RAID_OMEN))
            ledger.maintain(ID, StatusEffects.BAD_OMEN, 0, -1, 0);
    }

    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { check(player); }

    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        EffectLedger.of(player).remove(ID);
        EndlessRaidState.get(server).stopActivation(server);
    }

    // Disconnecting is not the end of a world event. The ledger persists in player NBT.
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) { }
}
