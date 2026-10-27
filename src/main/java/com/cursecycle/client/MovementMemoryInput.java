package com.cursecycle.client;

import com.cursecycle.challenge.impl.MovementMemoryChallenge;
import com.cursecycle.network.MovementMemoryPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.function.Consumer;

public final class MovementMemoryInput {
    private MovementMemoryInput() {}
    private static final Memory MEMORY = new Memory();
    private static Object player, world, inputIdentity;
    private static GameMode mode;

    /** Testable input state machine; no velocity, key binding, jump or sneak state is retained. */
    public static final class Memory {
        private long activation = -1, sequence;
        private float forward, sideways;
        private boolean moving, holding;

        public void context(long serial) {
            if (activation != serial) { clear(); activation = serial; }
        }
        public void clear() { forward = sideways = 0; moving = holding = false; sequence++; }
        public void reset() { clear(); activation = -1; }
        public void cancel(Consumer<MovementMemoryPayload> send) {
            boolean pending = holding;
            clear();
            if (pending && activation >= 0) send.accept(new MovementMemoryPayload(activation, sequence, false));
        }
        public void receive(MovementMemoryPayload message) {
            if (message.sequence() == -1) { context(message.activation()); return; }
            if (message.activation() == activation && message.sequence() == sequence && holding && !message.holding())
                clear();
        }
        public void apply(Input input, float scale, Consumer<MovementMemoryPayload> send) {
            if (activation < 0) return;
            if (input.movementForward != 0 || input.movementSideways != 0) {
                if (holding) cancel(send);
                forward = Math.signum(input.movementForward);
                sideways = Math.signum(input.movementSideways);
                moving = true;
                return;
            }
            if (moving) {
                moving = false;
                holding = true;
                send.accept(new MovementMemoryPayload(activation, ++sequence, true));
            }
            if (!holding) return;
            input.movementForward = forward * scale;
            input.movementSideways = sideways * scale;
            input.pressingForward = forward > 0;
            input.pressingBack = forward < 0;
            input.pressingLeft = sideways > 0;
            input.pressingRight = sideways < 0;
        }
        public boolean holding() { return holding; }
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
        if (MEMORY.holding() && client != null && client.player != null && !client.options.sprintKey.isPressed())
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
