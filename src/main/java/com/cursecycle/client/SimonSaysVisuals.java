package com.cursecycle.client;

import com.cursecycle.challenge.impl.SimonSaysChallenge;
import com.cursecycle.network.SimonSaysStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameMode;

/** Short capped prediction smooths the bar; the server alone decides success and timeout. */
public final class SimonSaysVisuals {
    private static SimonSaysStatePayload state;
    private static Object player, world;
    private static int elapsed;
    private SimonSaysVisuals() {}

    private static boolean active(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null
                || !client.player.isAlive() || client.player.isRemoved() || !ClientDataHolder.isSynced()
                || !ClientDataHolder.getActiveChallengeIds().contains(SimonSaysChallenge.ID)) return false;
        var mode = client.interactionManager.getCurrentGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }
    public static void receive(MinecraftClient client, SimonSaysStatePayload payload) {
        if (payload.phase() == SimonSaysStatePayload.HIDDEN || !active(client)) { reset(); return; }
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
    public static SimonSaysStatePayload state() { return state; }
    public static int remaining() { return Math.max(0, state.remaining() - elapsed); }
    public static void reset() { state = null; player = world = null; elapsed = 0; }
}
