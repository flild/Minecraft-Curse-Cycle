package com.cursecycle.network;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.BlockedDirectionChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashSet;
import java.util.List;

public class NetworkHandler {

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(FoodHistoryPayload.ID, FoodHistoryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CurseCycleSyncPayload.ID, CurseCycleSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TriggerWheelPayload.ID, TriggerWheelPayload.CODEC);
    }

    public static void syncToAll(MinecraftServer server, long currentDay, long ticksUntilNext, List<String> activeChallengeIds, String nextChallengeId, List<String> historyIds) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            syncToPlayer(player, currentDay, ticksUntilNext, activeChallengeIds, nextChallengeId, historyIds);
        }
    }

    public static void syncToPlayer(ServerPlayerEntity player, long currentDay, long ticksUntilNext, List<String> activeChallengeIds, String nextChallengeId, List<String> historyIds) {
        List<String> effectiveIds = getEffectiveActiveIds(activeChallengeIds,
                CurseCycleState.getServerState(player.getServer()).getPersonalActiveChallengeIds(player.getUuid()));
        var direction = BlockedDirectionChallenge.reconcile(player.getServer());
        String choice = direction != null && effectiveIds.contains(BlockedDirectionChallenge.ID) ? direction.id() : "";
        CurseCycleSyncPayload payload = new CurseCycleSyncPayload(currentDay, ticksUntilNext, effectiveIds, nextChallengeId, historyIds, choice);
        ServerPlayNetworking.send(player, payload);
    }

    static List<String> getEffectiveActiveIds(List<String> globalIds, List<String> personalIds) {
        // Client input and prediction must follow the server's enabled IDs in both scopes.
        var effectiveIds = new LinkedHashSet<String>();
        for (String id : globalIds) {
            var challenge = ChallengeRegistry.get(id);
            if (challenge != null && challenge.isEnabled()) effectiveIds.add(id);
        }
        for (String id : personalIds) {
            var challenge = ChallengeRegistry.get(id);
            if (challenge != null && challenge.isEnabled()) effectiveIds.add(id);
        }
        return List.copyOf(effectiveIds);
    }

    public static void triggerWheelForPlayer(ServerPlayerEntity player, List<String> candidateIds, String winnerId) {
        ServerPlayNetworking.send(player, new TriggerWheelPayload(candidateIds, winnerId));
    }

    public static void triggerWheelForAll(MinecraftServer server, List<String> candidateIds, String winnerId) {
        TriggerWheelPayload payload = new TriggerWheelPayload(candidateIds, winnerId);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
