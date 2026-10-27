package com.cursecycle.client;

import com.cursecycle.challenge.impl.BlockedDirectionChallenge;
import com.cursecycle.challenge.impl.BlockedDirectionChallenge.Direction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.world.GameMode;

import java.util.List;

public final class BlockedDirectionInput {
    private BlockedDirectionInput() {}

    public static boolean affects(boolean synced, List<String> ids, Direction direction, GameMode mode,
                                  boolean alive, boolean riding, boolean fallFlying) {
        return synced && direction != null && alive && !riding && !fallFlying
                && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && ids.contains(BlockedDirectionChallenge.ID);
    }

    public static boolean shouldBlock(MinecraftClient client, Input input) {
        return client != null && client.world != null && client.player != null
                && client.interactionManager != null && client.player.input == input && !client.player.isRemoved()
                && affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(),
                        ClientDataHolder.getBlockedDirection(), client.interactionManager.getCurrentGameMode(),
                        client.player.isAlive(), client.player.hasVehicle(), client.player.isFallFlying());
    }

    public static void apply(Input input) {
        if (shouldBlock(MinecraftClient.getInstance(), input)) block(input, ClientDataHolder.getBlockedDirection());
    }

    /** Clamp the resulting signed component, without converting opposing keys into new movement. */
    public static void block(Input input, Direction direction) {
        if (direction == null) return;
        switch (direction) {
            case LEFT -> { input.movementSideways = Math.min(0, input.movementSideways); input.pressingLeft = false; }
            case RIGHT -> { input.movementSideways = Math.max(0, input.movementSideways); input.pressingRight = false; }
            case FORWARD -> { input.movementForward = Math.min(0, input.movementForward); input.pressingForward = false; }
        }
    }

    public static String feedback(String challengeId) {
        var direction = ClientDataHolder.getBlockedDirection();
        return BlockedDirectionChallenge.ID.equals(challengeId) && direction != null ? direction.feedback() : "";
    }
}
