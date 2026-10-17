package com.cursecycle.challenge;

import net.minecraft.util.Formatting;

public enum ChallengeDifficulty {
    EASY("Лёгкий", Formatting.GREEN, 1),
    MEDIUM("Средний", Formatting.YELLOW, 2),
    HARD("Сложный", Formatting.RED, 3);

    private final String displayName;
    private final Formatting color;
    private final int level;

    ChallengeDifficulty(String displayName, Formatting color, int level) {
        this.displayName = displayName;
        this.color = color;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Formatting getColor() {
        return color;
    }

    public int getLevel() {
        return level;
    }
}
