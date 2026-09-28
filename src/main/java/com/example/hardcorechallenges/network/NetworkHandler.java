package com.example.hardcorechallenges.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public class NetworkHandler {
    public static void registerPackets() {
        PayloadTypeRegistry.playS2C().register(SyncStatePayload.ID, SyncStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RouletteStartPayload.ID, RouletteStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NotificationPayload.ID, NotificationPayload.CODEC);
    }

    public static void sendToAll(net.minecraft.server.MinecraftServer server, net.minecraft.network.packet.CustomPayload payload) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendToPlayer(ServerPlayerEntity player, net.minecraft.network.packet.CustomPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
