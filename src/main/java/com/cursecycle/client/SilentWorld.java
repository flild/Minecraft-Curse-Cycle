package com.cursecycle.client;

import com.cursecycle.challenge.impl.SilentWorldChallenge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameMode;

import java.util.List;

/** No saved options or player references: only the current local audio output is affected. */
public final class SilentWorld {
    private SilentWorld() {}

    public static boolean affects(boolean synced, List<String> ids, GameMode mode, boolean alive) {
        return synced && alive && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && ids.contains(SilentWorldChallenge.ID);
    }

    public static boolean shouldMute(MinecraftClient client) {
        return client != null && client.world != null && client.player != null
                && client.interactionManager != null && !client.player.isRemoved()
                && affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(),
                        client.interactionManager.getCurrentGameMode(), client.player.isAlive());
    }
}
