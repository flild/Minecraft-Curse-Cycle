package com.cursecycle.network;

import com.cursecycle.CurseCycleMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class NetworkHandler {

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(CurseCycleSyncPayload.ID, CurseCycleSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TriggerWheelPayload.ID, TriggerWheelPayload.CODEC);
    }

    public static void syncToAll(MinecraftServer server, long currentDay, long ticksUntilNext, List<String> activeChallengeIds, String nextChallengeId, List<String> historyIds) {
        CurseCycleSyncPayload payload = new CurseCycleSyncPayload(currentDay, ticksUntilNext, activeChallengeIds, nextChallengeId, historyIds);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void syncToPlayer(ServerPlayerEntity player, long currentDay, long ticksUntilNext, List<String> activeChallengeIds, String nextChallengeId, List<String> historyIds) {
        CurseCycleSyncPayload payload = new CurseCycleSyncPayload(currentDay, ticksUntilNext, activeChallengeIds, nextChallengeId, historyIds);
        ServerPlayNetworking.send(player, payload);
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
