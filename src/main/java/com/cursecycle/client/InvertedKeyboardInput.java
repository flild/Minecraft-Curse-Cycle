package com.cursecycle.client;

import com.cursecycle.challenge.impl.InvertedKeyboardChallenge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.world.GameMode;

import java.util.List;

/** Transforms freshly sampled movement, without retaining a player or changing key bindings. */
public final class InvertedKeyboardInput {
    private InvertedKeyboardInput() {}

    public static boolean affects(boolean synced, List<String> activeIds, GameMode mode, boolean alive) {
        return synced && alive && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && activeIds.contains(InvertedKeyboardChallenge.ID);
    }

    public static boolean shouldInvert(MinecraftClient client, Input input) {
        return client != null && client.world != null && client.player != null
                && client.interactionManager != null && client.player.input == input
                && !client.player.isRemoved()
                && affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(),
                        client.interactionManager.getCurrentGameMode(), client.player.isAlive());
    }

    /** Called once at KeyboardInput.tick RETURN, before downstream movement restrictions. */
    public static void apply(Input input) {
        if (!shouldInvert(MinecraftClient.getInstance(), input)) return;
        invert(input);
    }

    public static void invert(Input input) {
        input.movementForward = -input.movementForward;
        input.movementSideways = -input.movementSideways;
        // Boats consume these flags rather than the float components.
        boolean forward = input.pressingForward;
        input.pressingForward = input.pressingBack;
        input.pressingBack = forward;
        boolean left = input.pressingLeft;
        input.pressingLeft = input.pressingRight;
        input.pressingRight = left;
    }
}
