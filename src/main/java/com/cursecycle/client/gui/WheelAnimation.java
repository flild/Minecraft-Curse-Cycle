package com.cursecycle.client.gui;

/** Unwrapped angles keep interpolation continuous when the wheel passes zero degrees. */
public final class WheelAnimation {
    public static final int DURATION = 100;
    private final int count;
    private final double travel;

    public WheelAnimation(int count, int winnerIndex) {
        this.count = Math.max(0, count);
        double target = count > 0 && winnerIndex >= 0 && winnerIndex < count
                ? normalize(-90.0 - winnerIndex * 360.0 / count) : 0;
        travel = 5 * 360.0 + target;
    }

    public double angle(double ticks) {
        double progress = Math.clamp(ticks / DURATION, 0, 1);
        return travel * (1 - Math.pow(1 - progress, 3));
    }

    public int selected(double angle) {
        if (count == 0) return -1;
        double segment = 360.0 / count;
        return (int) (normalize(-90 - angle + segment / 2) / segment) % count;
    }

    public long boundary(double angle) {
        if (count == 0) return 0;
        double segment = 360.0 / count;
        return (long) Math.floor((-90 - angle + segment / 2) / segment);
    }

    private static double normalize(double angle) {
        return (angle % 360 + 360) % 360;
    }
}
