package com.cursecycle.client;

import com.cursecycle.challenge.impl.InvertedMouseChallenge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameMode;

import java.util.List;

/** Stateless gate for freshly computed mouse look; never writes options or retains a player. */
public final class InvertedMouseInput {
    private InvertedMouseInput() {}

    public static boolean affects(boolean synced, List<String> activeIds, GameMode mode, boolean alive) {
        return synced && alive && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && activeIds.contains(InvertedMouseChallenge.ID);
    }

    public static boolean shouldInvert(MinecraftClient client) {
        return client != null && client.world != null && client.player != null
                && client.interactionManager != null && !client.player.isRemoved()
                && affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(),
                        client.interactionManager.getCurrentGameMode(), client.player.isAlive());
    }
}
