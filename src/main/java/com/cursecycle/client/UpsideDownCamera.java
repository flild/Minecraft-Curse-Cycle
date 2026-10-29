package com.cursecycle.client;

import com.cursecycle.challenge.impl.UpsideDownCameraChallenge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameMode;

import java.util.List;

/** Current render context only: no stored matrix, options, player reference or activation state. */
public final class UpsideDownCamera {
    private UpsideDownCamera() {}

    public static boolean affects(boolean synced, List<String> activeIds, GameMode mode, boolean alive) {
        return synced && alive && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && activeIds.contains(UpsideDownCameraChallenge.ID);
    }

    public static boolean shouldApply(MinecraftClient client) {
        return client != null && client.world != null && client.player != null
                && client.interactionManager != null && !client.player.isRemoved()
                && affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(),
                        client.interactionManager.getCurrentGameMode(), client.player.isAlive());
    }
}
