package com.cursecycle.client;

import com.cursecycle.challenge.impl.MovementMemoryChallenge;
import com.cursecycle.network.MovementMemoryPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.Arrays;
import java.util.function.Consumer;

public final class MovementMemoryInput {
    private MovementMemoryInput() {}
    private static final Memory MEMORY = new Memory();
    private static Object player, world, inputIdentity;
    private static GameMode mode;

    /** Testable input state machine; no velocity, key binding, jump or sneak state is retained. */
    public static final class Memory {
        private long activation = -1, sequence;
        private final boolean[] pressed = new boolean[MovementMemoryPayload.DIRECTION_COUNT];
        private final boolean[] held = new boolean[MovementMemoryPayload.DIRECTION_COUNT];
        private final long[] requests = new long[MovementMemoryPayload.DIRECTION_COUNT];

        public void context(long serial) {
            if (activation != serial) { clear(); activation = serial; }
        }
        public void clear() {
            Arrays.fill(pressed, false);
            Arrays.fill(held, false);
            Arrays.fill(requests, -1);
        }
        public void reset() { clear(); activation = -1; }
        public void cancel(Consumer<MovementMemoryPayload> send) {
            for (int direction = 0; direction < held.length; direction++) {
                if (held[direction] && activation >= 0)
                    send.accept(new MovementMemoryPayload(activation, ++sequence, direction, false));
            }
            clear();
        }
        public void receive(MovementMemoryPayload message) {
            if (message.sequence() == -1) { context(message.activation()); return; }
            int direction = message.direction();
            if (direction >= 0 && direction < held.length && message.activation() == activation
                    && message.sequence() == requests[direction] && !message.holding())
                held[direction] = false;
        }
        public void apply(Input input, float scale, Consumer<MovementMemoryPayload> send) {
            if (activation < 0) return;
            // Read the four flags before combining axes: W+S or A+D must retain both keys independently.
            boolean[] current = {input.pressingForward, input.pressingBack, input.pressingLeft, input.pressingRight};
            for (int direction = 0; direction < held.length; direction++) {
                if (current[direction]) {
                    if (held[direction]) {
                        held[direction] = false;
                        requests[direction] = ++sequence;
                        send.accept(new MovementMemoryPayload(activation, sequence, direction, false));
                    }
                } else if (pressed[direction]) {
                    held[direction] = true;
                    requests[direction] = ++sequence;
                    send.accept(new MovementMemoryPayload(activation, sequence, direction, true));
                }
                pressed[direction] = current[direction];
                current[direction] |= held[direction];
            }
            input.pressingForward = current[0];
            input.pressingBack = current[1];
            input.pressingLeft = current[2];
            input.pressingRight = current[3];
            input.movementForward = ((current[0] ? 1 : 0) - (current[1] ? 1 : 0)) * scale;
            input.movementSideways = ((current[2] ? 1 : 0) - (current[3] ? 1 : 0)) * scale;
        }
        public boolean holding() {
            for (boolean direction : held) if (direction) return true;
            return false;
        }
        public boolean rememberingForward() { return held[MovementMemoryPayload.FORWARD]; }
    }

    public static boolean affects(boolean synced, List<String> ids, GameMode mode, boolean alive,
                                  boolean gui, boolean liquid, boolean climbing, boolean flying, boolean riding) {
        return synced && alive && !gui && !liquid && !climbing && !flying && !riding
                && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && ids.contains(MovementMemoryChallenge.ID);
    }

    public static boolean shouldRemember(MinecraftClient client, Input input) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null) return false;
        var current = client.player;
        var currentMode = client.interactionManager.getCurrentGameMode();
        if (player != current || world != client.world || inputIdentity != current.input || mode != currentMode) {
            cancel();
            player = current; world = client.world; inputIdentity = current.input; mode = currentMode;
        }
        return input == current.input && !current.isRemoved()
                && affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(), currentMode,
                current.isAlive(), client.currentScreen != null, current.isTouchingWater() || current.isInLava(),
                current.isClimbing(), current.isFallFlying() || current.getAbilities().flying, current.hasVehicle());
    }

    public static void apply(Input input, boolean slowDown, float factor) {
        var client = MinecraftClient.getInstance();
        if (!shouldRemember(client, input)) { cancel(); return; }
        MEMORY.apply(input, slowDown ? factor : 1, MovementMemoryInput::send);
        // Vanilla normally ends sprint on released forward input. Synthetic movement must not retain that latch.
        if (MEMORY.rememberingForward() && client != null && client.player != null && !client.options.sprintKey.isPressed())
            client.player.setSprinting(false);
    }

    public static void validate(MinecraftClient client) {
        if (client.player == null || !shouldRemember(client, client.player.input)) cancel();
    }
    private static void send(MovementMemoryPayload message) {
        if (ClientPlayNetworking.canSend(MovementMemoryPayload.ID)) ClientPlayNetworking.send(message);
    }
    public static void receive(MovementMemoryPayload message) {
        if (ClientDataHolder.getActiveChallengeIds().contains(MovementMemoryChallenge.ID)) MEMORY.receive(message);
    }
    public static void cancel() { MEMORY.cancel(MovementMemoryInput::send); }
    public static void reset() { MEMORY.reset(); player = world = inputIdentity = null; mode = null; }
}
