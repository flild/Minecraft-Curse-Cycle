package com.cursecycle;

import com.cursecycle.client.ForcedThirdPersonCamera;
import net.minecraft.client.option.Perspective;
import net.minecraft.world.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForcedThirdPersonCameraTest {
    @Test
    void locksEveryInitialPerspectiveAndRestoresItAfterRepeatedTicksAndF5Attempts() {
        for (Perspective original : Perspective.values()) {
            var camera = new ForcedThirdPersonCamera();
            Perspective current = camera.update(Perspective.THIRD_PERSON_BACK, GameMode.SURVIVAL, original);
            assertEquals(Perspective.THIRD_PERSON_BACK, current);
            for (int i = 0; i < 6; i++) {
                current = camera.constrain(Perspective.THIRD_PERSON_BACK, GameMode.SURVIVAL, current, current.next());
                assertEquals(Perspective.THIRD_PERSON_BACK, current);
                current = camera.update(Perspective.THIRD_PERSON_BACK, GameMode.ADVENTURE, current);
            }
            assertEquals(original, camera.update(null, GameMode.SURVIVAL, current));
            assertEquals(Perspective.FIRST_PERSON, camera.constrain(null, GameMode.SURVIVAL,
                    original, Perspective.FIRST_PERSON));
            assertEquals(Perspective.THIRD_PERSON_FRONT, camera.constrain(null, GameMode.SURVIVAL,
                    original, Perspective.THIRD_PERSON_FRONT));
        }
    }

    @Test
    void directSetterBeforeFirstTickCannotLoseTheOriginalOrBrieflyEnableFirstPerson() {
        var camera = new ForcedThirdPersonCamera();
        Perspective current = camera.constrain(Perspective.THIRD_PERSON_BACK, GameMode.ADVENTURE,
                Perspective.THIRD_PERSON_FRONT, Perspective.FIRST_PERSON);
        assertEquals(Perspective.THIRD_PERSON_BACK, current);
        assertEquals(Perspective.THIRD_PERSON_FRONT, camera.update(null, GameMode.ADVENTURE, current));
    }

    @Test
    void exemptModesAllowVanillaCameraButDoNotReplaceThePreChallengeSnapshot() {
        for (GameMode exempt : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
            var camera = new ForcedThirdPersonCamera();
            Perspective current = camera.update(Perspective.THIRD_PERSON_BACK, GameMode.SURVIVAL, Perspective.FIRST_PERSON);
            assertEquals(Perspective.FIRST_PERSON, camera.update(Perspective.THIRD_PERSON_BACK, exempt, current));
            current = camera.constrain(Perspective.THIRD_PERSON_BACK, exempt, Perspective.FIRST_PERSON, Perspective.THIRD_PERSON_FRONT);
            assertEquals(Perspective.THIRD_PERSON_FRONT, current);
            assertEquals(current, camera.update(Perspective.THIRD_PERSON_BACK, exempt, current));
            current = camera.update(Perspective.THIRD_PERSON_BACK, GameMode.ADVENTURE, current);
            assertEquals(Perspective.THIRD_PERSON_BACK, current);
            assertEquals(Perspective.FIRST_PERSON, camera.update(null, exempt, current));
        }
    }

    @Test
    void joiningInAnExemptModeDoesNotChangeCameraAndLocksUponEnteringSurvival() {
        var camera = new ForcedThirdPersonCamera();
        assertEquals(Perspective.THIRD_PERSON_FRONT,
                camera.update(Perspective.THIRD_PERSON_BACK, GameMode.SPECTATOR, Perspective.THIRD_PERSON_FRONT));
        assertEquals(Perspective.THIRD_PERSON_BACK,
                camera.update(Perspective.THIRD_PERSON_BACK, GameMode.SURVIVAL, Perspective.THIRD_PERSON_FRONT));
        assertEquals(Perspective.THIRD_PERSON_FRONT,
                camera.update(null, GameMode.SURVIVAL, Perspective.THIRD_PERSON_BACK));
    }

    @Test
    void missingPlayerDuringJoinRespawnOrDimensionChangePreservesTheSnapshot() {
        var camera = new ForcedThirdPersonCamera();
        assertEquals(Perspective.THIRD_PERSON_FRONT, camera.update(Perspective.THIRD_PERSON_BACK, null, Perspective.THIRD_PERSON_FRONT));
        camera.update(Perspective.THIRD_PERSON_BACK, GameMode.SURVIVAL, Perspective.THIRD_PERSON_FRONT);
        camera.update(Perspective.THIRD_PERSON_BACK, null, Perspective.THIRD_PERSON_BACK);
        assertEquals(Perspective.THIRD_PERSON_BACK,
                camera.update(Perspective.THIRD_PERSON_BACK, GameMode.SURVIVAL, Perspective.FIRST_PERSON));
        assertEquals(Perspective.THIRD_PERSON_FRONT,
                camera.update(null, null, Perspective.THIRD_PERSON_BACK));
    }

    @Test
    void disconnectOrStopWhileOfflineRestoresOnceAndNextConnectionCapturesFreshPerspective() {
        var camera = new ForcedThirdPersonCamera();
        camera.update(Perspective.THIRD_PERSON_BACK, GameMode.SURVIVAL, Perspective.THIRD_PERSON_FRONT);
        assertEquals(Perspective.THIRD_PERSON_FRONT, camera.update(null, null, Perspective.THIRD_PERSON_BACK));
        // Subsequent ticks must not overwrite the user's new setting on an unrelated server.
        assertEquals(Perspective.FIRST_PERSON, camera.update(null, GameMode.SURVIVAL, Perspective.FIRST_PERSON));
        // Reconnect or server restart with the challenge still active.
        assertEquals(Perspective.THIRD_PERSON_BACK,
                camera.update(Perspective.THIRD_PERSON_BACK, GameMode.SURVIVAL, Perspective.FIRST_PERSON));
        assertEquals(Perspective.FIRST_PERSON,
                camera.update(null, GameMode.SURVIVAL, Perspective.THIRD_PERSON_BACK));
    }
}
