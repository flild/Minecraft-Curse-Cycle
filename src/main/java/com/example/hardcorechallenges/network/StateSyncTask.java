package com.example.hardcorechallenges.network;

import com.example.hardcorechallenges.manager.ChallengeManager;
import com.example.hardcorechallenges.api.challenge.Challenge;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.stream.Collectors;

public class StateSyncTask {

    private static int tickCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StateSyncTask::onTick);
    }

    private static void onTick(MinecraftServer server) {
        tickCounter++;
        // Синхронизация каждую секунду (20 тиков)
        if (tickCounter >= 20) {
            tickCounter = 0;
            syncNow(server);
        }
    }

    public static void syncNow(MinecraftServer server) {
        ChallengeManager manager = ChallengeManager.getInstance();
        List<String> activeNames = manager.getActiveChallenges().stream()
                .map(c -> c.getDefinition().name())
                .collect(Collectors.toList());

        SyncStatePayload payload = new SyncStatePayload(
                activeNames,
                manager.getTotalTicks(),
                manager.getGameMode().name()
        );

        NetworkHandler.sendToAll(server, payload);
    }
}
