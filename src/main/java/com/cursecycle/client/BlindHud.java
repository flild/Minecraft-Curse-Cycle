package com.cursecycle.client;

import com.cursecycle.challenge.impl.BlindHudChallenge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameMode;

import java.util.List;

/** A current-frame filter, with no snapshots of resources, settings or player objects. */
public final class BlindHud {
    private BlindHud() {}

    public static boolean affects(boolean synced, List<String> ids, GameMode mode, boolean alive) {
        return synced && alive && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && ids.contains(BlindHudChallenge.ID);
    }

    public static boolean shouldApply(MinecraftClient client) {
        return client != null && client.world != null && client.player != null
                && client.interactionManager != null && !client.player.isRemoved()
                && affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(),
                        client.interactionManager.getCurrentGameMode(), client.player.isAlive());
    }
}
