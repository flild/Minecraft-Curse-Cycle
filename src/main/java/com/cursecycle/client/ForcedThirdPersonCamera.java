package com.cursecycle.client;

import com.cursecycle.challenge.impl.ForcedThirdPersonFrontChallenge;
import com.cursecycle.challenge.impl.ForcedThirdPersonRpgChallenge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.world.GameMode;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class ForcedThirdPersonCamera {
    private static final ForcedThirdPersonCamera INSTANCE = new ForcedThirdPersonCamera();

    // One local player's connection. Never stored in the shared Challenge singleton or options.txt.
    private Perspective originalPerspective;
    private boolean locked;

    public Perspective update(Perspective forced, GameMode mode, Perspective current) {
        if (forced == null) {
            Perspective restored = originalPerspective == null ? current : originalPerspective;
            originalPerspective = null;
            locked = false;
            return restored;
        }
        // Player/interaction manager can be absent briefly during join, respawn or world transfer.
        if (mode == null) return current;

        if (affectsGameMode(forced, mode)) {
            if (originalPerspective == null) originalPerspective = current;
            locked = true;
            return forced;
        }
        if (locked) {
            locked = false;
            return originalPerspective;
        }
        return current;
    }

    public Perspective constrain(Perspective forced, GameMode mode, Perspective current, Perspective requested) {
        if (forced != null && affectsGameMode(forced, mode)) {
            // Capture before the setter changes anything, even if it runs before our tick hook.
            return update(forced, mode, current);
        }
        return requested;
    }

    private static boolean affectsGameMode(Perspective forced, GameMode mode) {
        return forced == Perspective.THIRD_PERSON_FRONT
                ? ForcedThirdPersonFrontChallenge.affectsGameMode(mode)
                : ForcedThirdPersonRpgChallenge.affectsGameMode(mode);
    }

    public static Perspective resolvePerspective(List<String> activeIds) {
        // Both camera challenges can be drawn together. Resolve once, without competing setters.
        if (activeIds.contains(ForcedThirdPersonFrontChallenge.ID)) return Perspective.THIRD_PERSON_FRONT;
        if (activeIds.contains(ForcedThirdPersonRpgChallenge.ID)) return Perspective.THIRD_PERSON_BACK;
        return null;
    }

    private static Perspective forcedPerspective() {
        return ClientDataHolder.isSynced() ? resolvePerspective(ClientDataHolder.getActiveChallengeIds()) : null;
    }

    private static GameMode gameMode(MinecraftClient client) {
        return client.player == null || client.interactionManager == null
                ? null : client.interactionManager.getCurrentGameMode();
    }

    public static Perspective constrain(MinecraftClient client, Perspective requested) {
        return INSTANCE.constrain(forcedPerspective(), gameMode(client), client.options.getPerspective(), requested);
    }

    public static void apply(MinecraftClient client) {
        Perspective current = client.options.getPerspective();
        Perspective next = INSTANCE.update(forcedPerspective(), gameMode(client), current);
        if (next != current) {
            client.options.setPerspective(next);
            // Match the renderer refresh performed by vanilla when processing F5.
            client.gameRenderer.onCameraEntitySet(next.isFirstPerson() ? client.getCameraEntity() : null);
            client.worldRenderer.scheduleTerrainUpdate();
        }
    }
}
