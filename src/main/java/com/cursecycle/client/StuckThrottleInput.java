package com.cursecycle.client;

import com.cursecycle.challenge.impl.StuckThrottleChallenge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.world.GameMode;

/** Stateless final input component. Vanilla rebuilds it after the challenge ends. */
public final class StuckThrottleInput {
    private StuckThrottleInput() {}

    public static boolean shouldForce(MinecraftClient client, Input input) {
        if (client == null || client.world == null || client.player == null || client.interactionManager == null)
            return false;
        var player = client.player;
        var mode = client.interactionManager.getCurrentGameMode();
        return input == player.input && !player.isRemoved() && player.isAlive()
                && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && ClientDataHolder.isSynced()
                && ClientDataHolder.getActiveChallengeIds().contains(StuckThrottleChallenge.ID);
    }

    public static void apply(Input input, boolean slowDown, float factor) {
        if (!shouldForce(MinecraftClient.getInstance(), input)) return;
        // Apply after inversion, memory and direction blocking. Do not feed forced flags back into memory.
        input.movementForward = slowDown ? factor : 1;
        input.pressingForward = true;
        input.pressingBack = false;
    }
}
