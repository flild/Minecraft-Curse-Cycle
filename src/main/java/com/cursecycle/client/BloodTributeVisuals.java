package com.cursecycle.client;

import com.cursecycle.challenge.impl.BloodTributeChallenge;
import com.cursecycle.network.BloodTributeStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameMode;

/** Prediction is capped at the five-tick sample interval; damage stays server-authoritative. */
public final class BloodTributeVisuals {
    private static BloodTributeStatePayload state;
    private static Object player, world;
    private static int elapsed;
    private BloodTributeVisuals() {}

    private static boolean active(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null
                || !client.player.isAlive() || client.player.isRemoved() || !ClientDataHolder.isSynced()
                || !ClientDataHolder.getActiveChallengeIds().contains(BloodTributeChallenge.ID)) return false;
        var mode = client.interactionManager.getCurrentGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }
    public static void receive(MinecraftClient client, BloodTributeStatePayload payload) {
        if (!payload.active() || !active(client)) { reset(); return; }
        state = payload;
        player = client.player;
        world = client.world;
        elapsed = 0;
    }
    public static boolean valid(MinecraftClient client) {
        return state != null && active(client) && player == client.player && world == client.world;
    }
    public static void tick(MinecraftClient client) {
        if (!valid(client)) { reset(); return; }
        if (!client.isPaused()) elapsed = Math.min(5, elapsed + 1);
    }
    public static BloodTributeStatePayload state() { return state; }
    public static int remaining() { return Math.max(0, state.remaining() - elapsed); }
    public static void reset() { state = null; player = world = null; elapsed = 0; }
}
