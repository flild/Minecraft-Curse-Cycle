package com.cursecycle.client;

import com.cursecycle.challenge.impl.HeavyBodyChallenge;
import com.cursecycle.network.HeavyBodyPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import java.util.function.Consumer;

/** Observes final input (including movement_memory and blocked_direction); never changes movement. */
public final class HeavyBodyInput {
    private HeavyBodyInput() {}
    private static final Intent INTENT = new Intent();
    public static final class Intent {
        private long activation = -1;
        private Object player, world;
        private Boolean previous;
        public void context(long serial) {
            if (activation != serial) { reset(); activation = serial; }
        }
        public void reset() { activation = -1; player = world = null; previous = null; }
        public void report(Object currentPlayer, Object currentWorld, boolean moving, Consumer<HeavyBodyPayload> send) {
            if (activation < 0) return;
            if (player != currentPlayer || world != currentWorld) {
                player = currentPlayer; world = currentWorld; previous = null;
            }
            if (previous == null || previous != moving) {
                previous = moving;
                send.accept(new HeavyBodyPayload(activation, moving));
            }
        }
    }
    public static void receive(HeavyBodyPayload payload) {
        if (ClientDataHolder.getActiveChallengeIds().contains(HeavyBodyChallenge.ID)) INTENT.context(payload.activation());
    }
    public static boolean moving(MinecraftClient client) {
        var p = client.player;
        return p != null && client.world != null && client.interactionManager != null && client.currentScreen == null
                && HeavyBodyChallenge.active(p) && HeavyBodyChallenge.onFoot(p)
                && (p.input.movementForward != 0 || p.input.movementSideways != 0);
    }
    public static void tick(MinecraftClient client) {
        if (!ClientDataHolder.isSynced() || !ClientDataHolder.getActiveChallengeIds().contains(HeavyBodyChallenge.ID)) { reset(); return; }
        INTENT.report(client.player, client.world, moving(client), HeavyBodyInput::send);
    }
    private static void send(HeavyBodyPayload payload) {
        if (ClientPlayNetworking.canSend(HeavyBodyPayload.ID)) ClientPlayNetworking.send(payload);
    }
    public static void reset() { INTENT.reset(); }
}
