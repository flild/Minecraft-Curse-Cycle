package com.cursecycle.client.gui;

/** Server-supplied remaining ticks, expressed as whole Minecraft days rounded up. */
public final class ChallengeCountdown {
    private static final long TICKS_PER_DAY = 24_000;

    private ChallengeCountdown() {}

    public static long daysRemaining(long ticks) {
        long remaining = Math.max(0, ticks);
        return remaining / TICKS_PER_DAY + (remaining % TICKS_PER_DAY == 0 ? 0 : 1);
    }

    public static String daysKey(long days, String language) {
        String form;
        if (language != null && language.startsWith("ru")) {
            long lastTwo = days % 100;
            long last = days % 10;
            form = lastTwo >= 11 && lastTwo <= 14 ? "many"
                    : last == 1 ? "one" : last >= 2 && last <= 4 ? "few" : "many";
        } else {
            form = days == 1 ? "one" : "many";
        }
        return "text.cursecycle.hud.days." + form;
    }
}
