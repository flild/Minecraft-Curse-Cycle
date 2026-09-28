package com.example.hardcorechallenges.config;

import com.example.hardcorechallenges.api.challenge.GameMode;

public class HardcoreChallengesConfig {
    public boolean enabled = true;
    public GameMode mode = GameMode.CLASSIC;
    public int defaultDurationTicks = 12000;
    public int switchFrequencyDays = 1;
    public int maxActiveChallenges = 2;
    public boolean allowRepeats = false;

    public boolean hudEnabled = true;
    public int hudPositionX = 10;
    public int hudPositionY = 10;
    public boolean notificationsEnabled = true;

    public int rouletteVariantsCount = 8;
    public int rouletteSelectionCount = 1;

    public boolean debugMode = false;
}
