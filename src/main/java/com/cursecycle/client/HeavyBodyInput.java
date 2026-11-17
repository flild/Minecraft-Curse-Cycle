package com.cursecycle.client;

import com.cursecycle.challenge.impl.HeavyBodyChallenge;
import com.cursecycle.network.HeavyBodyPayload;
import com.cursecycle.network.HeavyBodyStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import java.util.function.Consumer;

/** Reports effective movement and receives server-owned stamina; never changes directional input. */
public final class HeavyBodyInput {
    private HeavyBodyInput() {}
    private static final Intent INTENT = new Intent();
    private static final Stamina STAMINA = new Stamina();
    public static final class Stamina {
        private long activation = -1;
        private int value = HeavyBodyChallenge.MAX_STAMINA, recovery;
        private float darkness;
        public void context(long serial) {
            if (activation != serial) { clearSample(); activation = serial; }
        }
        public boolean receive(HeavyBodyStatePayload payload) {
            if (activation < 0 || payload.activation() != activation) return false;
            value = Math.clamp(payload.stamina(), 0, HeavyBodyChallenge.MAX_STAMINA);
            recovery = Math.clamp(payload.recoveryTicks(), 0, HeavyBodyChallenge.RECOVERY_TICKS);
            return true;
        }
        public void tick() {
            darkness = exhausted() ? Math.min(.6F, darkness + .15F) : Math.max(0, darkness - .15F);
        }
        public boolean exhausted() { return recovery > 0; }
        public int value() { return value; }
        public float darkness() { return darkness; }
        public boolean visible() { return value < HeavyBodyChallenge.MAX_STAMINA || exhausted(); }
        public void clearSample() { value = HeavyBodyChallenge.MAX_STAMINA; recovery = 0; darkness = 0; }
        public void reset() { clearSample(); activation = -1; }
    }
    private static Object statePlayer, stateWorld;
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
        if (ClientDataHolder.getActiveChallengeIds().contains(HeavyBodyChallenge.ID)) {
            INTENT.context(payload.activation());
            STAMINA.context(payload.activation());
        }
    }
    public static void receive(MinecraftClient client, HeavyBodyStatePayload payload) {
        if (client.player != null && client.world != null && HeavyBodyChallenge.active(client.player)
                && STAMINA.receive(payload)) {
            statePlayer = client.player;
            stateWorld = client.world;
            if (STAMINA.exhausted()) client.player.setSprinting(false);
        }
    }
    public static boolean valid(MinecraftClient client) {
        return client != null && client.player != null && client.world != null
                && client.player == statePlayer && client.world == stateWorld && HeavyBodyChallenge.active(client.player);
    }
    public static boolean exhausted(net.minecraft.entity.player.PlayerEntity player) {
        var client = MinecraftClient.getInstance();
        return valid(client) && client.player == player && STAMINA.exhausted();
    }
    public static Stamina stamina() { return STAMINA; }
    public static boolean moving(MinecraftClient client) {
        var p = client.player;
        return p != null && client.world != null && client.interactionManager != null && client.currentScreen == null
                && HeavyBodyChallenge.active(p) && HeavyBodyChallenge.onFoot(p)
                && (p.input.movementForward != 0 || p.input.movementSideways != 0);
    }
    public static void tick(MinecraftClient client) {
        if (!ClientDataHolder.isSynced() || !ClientDataHolder.getActiveChallengeIds().contains(HeavyBodyChallenge.ID)) { reset(); return; }
        INTENT.report(client.player, client.world, moving(client), HeavyBodyInput::send);
        if (!valid(client)) { STAMINA.clearSample(); statePlayer = stateWorld = null; }
        else if (!client.isPaused()) STAMINA.tick();
    }
    private static void send(HeavyBodyPayload payload) {
        if (ClientPlayNetworking.canSend(HeavyBodyPayload.ID)) ClientPlayNetworking.send(payload);
    }
    public static void reset() { INTENT.reset(); STAMINA.reset(); statePlayer = stateWorld = null; }
}
