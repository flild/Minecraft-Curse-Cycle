package com.cursecycle.client;

import com.cursecycle.challenge.impl.ForcedThirdPersonRpgChallenge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.world.GameMode;

@Environment(EnvType.CLIENT)
public final class ForcedThirdPersonCamera {
    private static final ForcedThirdPersonCamera INSTANCE = new ForcedThirdPersonCamera();

    // One local player's connection. Never stored in the shared Challenge singleton or options.txt.
    private Perspective originalPerspective;
    private boolean locked;

    public Perspective update(boolean active, GameMode mode, Perspective current) {
        if (!active) {
            Perspective restored = originalPerspective == null ? current : originalPerspective;
            originalPerspective = null;
            locked = false;
            return restored;
        }
        // Player/interaction manager can be absent briefly during join, respawn or world transfer.
        if (mode == null) return current;

        if (ForcedThirdPersonRpgChallenge.affectsGameMode(mode)) {
            if (originalPerspective == null) originalPerspective = current;
            locked = true;
            return Perspective.THIRD_PERSON_BACK;
        }
        if (locked) {
            locked = false;
            return originalPerspective;
        }
        return current;
    }

    public Perspective constrain(boolean active, GameMode mode, Perspective current, Perspective requested) {
        if (active && ForcedThirdPersonRpgChallenge.affectsGameMode(mode)) {
            // Capture before the setter changes anything, even if it runs before our tick hook.
            return update(true, mode, current);
        }
        return requested;
    }

    private static boolean isActive() {
        return ClientDataHolder.isSynced()
                && ClientDataHolder.getActiveChallengeIds().contains(ForcedThirdPersonRpgChallenge.ID);
    }

    private static GameMode gameMode(MinecraftClient client) {
        return client.player == null || client.interactionManager == null
                ? null : client.interactionManager.getCurrentGameMode();
    }

    public static Perspective constrain(MinecraftClient client, Perspective requested) {
        return INSTANCE.constrain(isActive(), gameMode(client), client.options.getPerspective(), requested);
    }

    public static void apply(MinecraftClient client) {
        Perspective current = client.options.getPerspective();
        Perspective next = INSTANCE.update(isActive(), gameMode(client), current);
        if (next != current) {
            client.options.setPerspective(next);
            // Match the renderer refresh performed by vanilla when processing F5.
            client.gameRenderer.onCameraEntitySet(next.isFirstPerson() ? client.getCameraEntity() : null);
            client.worldRenderer.scheduleTerrainUpdate();
        }
    }
}
