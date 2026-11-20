package com.cursecycle.client;

import com.cursecycle.challenge.impl.OffsetCrosshairChallenge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameMode;
import org.joml.Matrix4f;

import java.util.List;

/** Matching HUD and off-axis scene projections; no cached settings or activation state. */
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

    public static Matrix4f offsetProjection(Matrix4f projection, int width, int height) {
        if (width <= 0 || height <= 0) return projection;
        // Pre-multiply in clip space so the vanilla aim ray projects onto the HUD mark at every
        // distance/FOV. Moving the world and hand frusta together also keeps server-side arrows,
        // block selection and entity hits aligned without rewriting rotations or action packets.
        float x = 2f * (width / 2 + translation(width, OffsetCrosshairChallenge.OFFSET_X)) / width - 1f;
        float y = 1f - 2f * (height / 2 + translation(height, OffsetCrosshairChallenge.OFFSET_Y)) / height;
        return projection.translateLocal(x, y, 0);
    }
}
