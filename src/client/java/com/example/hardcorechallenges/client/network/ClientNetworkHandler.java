package com.example.hardcorechallenges.client.network;

import com.example.hardcorechallenges.client.ui.RouletteScreen;
import com.example.hardcorechallenges.network.NotificationPayload;
import com.example.hardcorechallenges.network.RouletteStartPayload;
import com.example.hardcorechallenges.network.SyncStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ClientNetworkHandler {
    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(SyncStatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientStateCache.activeChallenges = payload.activeChallengeNames();
                ClientStateCache.totalTicks = payload.totalTicks();
                ClientStateCache.gameMode = payload.gameMode();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(RouletteStartPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                MinecraftClient.getInstance().setScreen(new RouletteScreen(payload.selectedChallengeNames(), payload.candidatesNames()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(NotificationPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                // В будущем можно сделать кастомные Toast уведомления
                String prefix = switch(payload.type()) {
                    case 1 -> "§e[Warning]";
                    case 2 -> "§4[Critical]";
                    default -> "§b[Info]";
                };
                context.client().player.sendMessage(Text.literal(prefix + " " + payload.title() + " - " + payload.message()), false);
            });
        });
    }
}
