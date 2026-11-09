package com.cursecycle.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WheelAnimationTest {
    @Test void everyWinnerStopsAtThePointerCenterIncludingNonDivisorsOf360() {
        for (int count = 1; count <= 64; count++) {
            for (int winner = 0; winner < count; winner++) {
                var animation = new WheelAnimation(count, winner);
                double stop = animation.angle(WheelAnimation.DURATION);
                assertEquals(winner, animation.selected(stop), "count=" + count + ", winner=" + winner);
                double center = ((stop + winner * 360.0 / count) % 360 + 360) % 360;
                assertEquals(270, center, 1e-8);
                assertEquals(stop, animation.angle(WheelAnimation.DURATION + 20), 0);
            }
        }
    }

    @Test void motionIsContinuousMonotonicAndDeceleratesAcrossFullTurns() {
        var animation = new WheelAnimation(7, 4);
        double previous = animation.angle(0);
        double previousStep = Double.POSITIVE_INFINITY;
        for (double t = .125; t <= WheelAnimation.DURATION; t += .125) {
            double current = animation.angle(t);
            double step = current - previous;
            assertTrue(step > 0, "never reverses or wraps at t=" + t);
            assertTrue(step <= previousStep + 1e-9, "gradually slows");
            previous = current;
            previousStep = step;
        }
        assertTrue(previous >= 1800, "at least five full rotations");
        assertEquals(0, animation.angle(-1), 0);
    }

    @Test void theSelectionChangesOnSectorEdgesRatherThanSectorCenters() {
        var animation = new WheelAnimation(6, 0);
        for (int index = 0; index < 6; index++) {
            double centerAngle = 270 - index * 60;
            assertEquals(index, animation.selected(centerAngle - 29.99));
            assertEquals(index, animation.selected(centerAngle + 29.99));
            assertNotEquals(index, animation.selected(centerAngle + 30.01));
        }
    }

    @Test void crossingsRetainFullTurnsSoAnEntireRevolutionStillMakesSound() {
        var animation = new WheelAnimation(6, 0);
        assertEquals(animation.selected(0), animation.selected(360));
        assertEquals(6, Math.abs(animation.boundary(360) - animation.boundary(0)));
        assertEquals(animation.boundary(270), animation.boundary(270.1));
    }

    @Test void emptyStaleAndSingleCandidateListsAreSafe() {
        for (int count : new int[]{-1, 0, 1}) {
            var animation = new WheelAnimation(count, -1);
            assertTrue(Double.isFinite(animation.angle(50)));
            assertEquals(count > 0 ? 0 : -1, animation.selected(animation.angle(100)));
        }
        assertEquals(0, new WheelAnimation(1, 0).selected(1234567.25));
    }
}
