package com.cursecycle;

import com.cursecycle.challenge.impl.ForcedThirdPersonFrontChallenge;
import com.cursecycle.challenge.impl.ForcedThirdPersonRpgChallenge;
import com.cursecycle.client.ForcedThirdPersonCamera;
import net.minecraft.client.option.Perspective;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForcedThirdPersonFrontCameraTest {
    private static final Perspective FRONT = Perspective.THIRD_PERSON_FRONT;

    @Test
    void everyInitialViewIsRestoredAfterRepeatedTicksF5AndDirectSetterAttempts() {
        for (Perspective original : Perspective.values()) {
            var camera = new ForcedThirdPersonCamera();
            Perspective current = camera.update(FRONT, GameMode.SURVIVAL, original);
            assertEquals(FRONT, current);
            for (int i = 0; i < 6; i++) {
                for (Perspective requested : Perspective.values()) {
                    current = camera.constrain(FRONT, GameMode.SURVIVAL, current, requested);
                    assertEquals(FRONT, current);
                }
                current = camera.constrain(FRONT, GameMode.ADVENTURE, current, current.next());
                assertEquals(FRONT, camera.update(FRONT, GameMode.ADVENTURE, current));
            }
            assertEquals(original, camera.update(null, GameMode.SURVIVAL, current));
            for (Perspective requested : Perspective.values()) {
                assertEquals(requested, camera.constrain(null, GameMode.SURVIVAL, original, requested));
            }
        }
    }

    @Test
    void directSetterBeforeFirstTickCapturesTheOriginalView() {
        var camera = new ForcedThirdPersonCamera();
        assertEquals(FRONT, camera.constrain(FRONT, GameMode.ADVENTURE,
                Perspective.THIRD_PERSON_BACK, Perspective.FIRST_PERSON));
        assertEquals(Perspective.THIRD_PERSON_BACK, camera.update(null, GameMode.ADVENTURE, FRONT));
    }

    @Test
    void creativeAndSpectatorStayFreeWithoutLosingTheOriginalView() {
        for (GameMode exempt : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
            var camera = new ForcedThirdPersonCamera();
            assertEquals(Perspective.THIRD_PERSON_BACK,
                    camera.update(FRONT, exempt, Perspective.THIRD_PERSON_BACK));
            assertEquals(FRONT, camera.update(FRONT, GameMode.SURVIVAL, Perspective.FIRST_PERSON));
            assertEquals(Perspective.FIRST_PERSON, camera.update(FRONT, exempt, FRONT));
            assertEquals(Perspective.THIRD_PERSON_BACK, camera.constrain(FRONT, exempt,
                    Perspective.FIRST_PERSON, Perspective.THIRD_PERSON_BACK));
            assertEquals(Perspective.THIRD_PERSON_BACK,
                    camera.update(FRONT, exempt, Perspective.THIRD_PERSON_BACK));
            assertEquals(FRONT, camera.update(FRONT, GameMode.ADVENTURE, Perspective.THIRD_PERSON_BACK));
            assertEquals(Perspective.FIRST_PERSON, camera.update(null, exempt, FRONT));
        }
    }

    @Test
    void respawnDimensionChangeDisconnectAndReconnectPreserveOrResetTheSnapshotAsNeeded() {
        var camera = new ForcedThirdPersonCamera();
        assertEquals(Perspective.THIRD_PERSON_BACK, camera.update(FRONT, null, Perspective.THIRD_PERSON_BACK));
        assertEquals(FRONT, camera.update(FRONT, GameMode.SURVIVAL, Perspective.THIRD_PERSON_BACK));
        assertEquals(FRONT, camera.update(FRONT, null, FRONT));
        assertEquals(FRONT, camera.update(FRONT, GameMode.SURVIVAL, Perspective.FIRST_PERSON));
        assertEquals(Perspective.THIRD_PERSON_BACK, camera.update(null, null, FRONT));
        assertEquals(Perspective.FIRST_PERSON, camera.update(null, null, Perspective.FIRST_PERSON));
        assertEquals(FRONT, camera.update(FRONT, GameMode.SURVIVAL, Perspective.FIRST_PERSON));
        assertEquals(Perspective.FIRST_PERSON, camera.update(null, GameMode.SURVIVAL, FRONT));
    }

    @Test
    void simultaneousCameraChallengesHaveStablePriorityAndShareOneOriginalSnapshot() {
        String front = ForcedThirdPersonFrontChallenge.ID;
        String back = ForcedThirdPersonRpgChallenge.ID;
        assertNull(ForcedThirdPersonCamera.resolvePerspective(List.of("no_sprint")));
        assertEquals(FRONT, ForcedThirdPersonCamera.resolvePerspective(List.of(front, "no_sprint")));
        assertEquals(FRONT, ForcedThirdPersonCamera.resolvePerspective(List.of(front, back)));
        assertEquals(FRONT, ForcedThirdPersonCamera.resolvePerspective(List.of(back, front)));
        assertEquals(Perspective.THIRD_PERSON_BACK, ForcedThirdPersonCamera.resolvePerspective(List.of(back)));

        var camera = new ForcedThirdPersonCamera();
        Perspective current = Perspective.FIRST_PERSON;
        for (Perspective forced : new Perspective[]{Perspective.THIRD_PERSON_BACK, FRONT,
                Perspective.THIRD_PERSON_BACK, FRONT}) {
            current = camera.update(forced, GameMode.SURVIVAL, current);
            assertEquals(forced, current);
        }
        assertEquals(Perspective.FIRST_PERSON, camera.update(null, GameMode.SURVIVAL, current));
    }
}
