package com.cursecycle.config;

/** Only server-owned timing fields cross the network; client display preferences stay local. */
public record TimingSettings(double intervalDays, double noticeDays, int preparationSeconds) {
    public static final double MIN_INTERVAL_DAYS = 1.0 / 24_000;
    public static final double MAX_INTERVAL_DAYS = Integer.MAX_VALUE;

    public static boolean isValidInterval(double days) {
        return Double.isFinite(days) && days >= MIN_INTERVAL_DAYS && days <= MAX_INTERVAL_DAYS;
    }

    /** Round once at the scheduling boundary; all cycle arithmetic uses whole world ticks. */
    public static long daysToTicks(double days) {
        return Math.round(days * 24_000);
    }

    public static TimingSettings from(CurseCycleConfig config) {
        return new TimingSettings(config.challengeIntervalDays, config.noticeLeadTimeDays,
                config.initialPreparationSeconds);
    }

    public boolean isValid() {
        return isValidInterval(intervalDays) && Double.isFinite(noticeDays)
                && noticeDays >= 0 && noticeDays <= intervalDays && preparationSeconds >= 0;
    }

    public void applyTo(CurseCycleConfig config) {
        config.challengeIntervalDays = intervalDays;
        config.noticeLeadTimeDays = noticeDays;
        config.initialPreparationSeconds = preparationSeconds;
    }
}
