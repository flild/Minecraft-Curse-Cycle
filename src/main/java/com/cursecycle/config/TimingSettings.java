package com.cursecycle.config;

/** Only server-owned timing fields cross the network; client display preferences stay local. */
public record TimingSettings(int intervalDays, int noticeDays, int preparationSeconds) {
    public static TimingSettings from(CurseCycleConfig config) {
        return new TimingSettings(config.challengeIntervalDays, config.noticeLeadTimeDays,
                config.initialPreparationSeconds);
    }

    public boolean isValid() {
        return intervalDays >= 1 && noticeDays >= 0 && noticeDays <= intervalDays && preparationSeconds >= 0;
    }

    public void applyTo(CurseCycleConfig config) {
        config.challengeIntervalDays = intervalDays;
        config.noticeLeadTimeDays = noticeDays;
        config.initialPreparationSeconds = preparationSeconds;
    }
}
