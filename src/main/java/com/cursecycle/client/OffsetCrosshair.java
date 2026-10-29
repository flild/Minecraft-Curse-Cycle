package com.cursecycle.client;

import com.cursecycle.challenge.impl.OffsetCrosshairChallenge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameMode;

import java.util.List;

/** A render-local translation; no cached window size, options, player or activation state. */
public final class OffsetCrosshair {
    private OffsetCrosshair() {}

    public static boolean affects(boolean synced, List<String> activeIds, GameMode mode, boolean alive) {
        return synced && alive && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && activeIds.contains(OffsetCrosshairChallenge.ID);
    }

    public static boolean shouldApply(MinecraftClient client) {
        return client != null && client.world != null && client.player != null
                && client.interactionManager != null && !client.player.isRemoved()
                && affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(),
                        client.interactionManager.getCurrentGameMode(), client.player.isAlive());
    }

    public static int translation(int size, int offset) {
        if (size < 2 * OffsetCrosshairChallenge.EDGE_MARGIN) return 0;
        int center = size / 2; // Same integer center as vanilla's crosshair and indicators.
        return Math.clamp(center + offset, OffsetCrosshairChallenge.EDGE_MARGIN,
                size - OffsetCrosshairChallenge.EDGE_MARGIN) - center;
    }
}
