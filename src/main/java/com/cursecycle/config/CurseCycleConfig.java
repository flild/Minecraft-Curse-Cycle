package com.cursecycle.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.cursecycle.CurseCycleMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public class CurseCycleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("cursecycle.json").toFile();

    public int challengeIntervalDays = 5;
    public int noticeLeadTimeDays = 1;
    public int maxActiveChallenges = 1;
    public boolean allowDuplicatesInHistory = true;
    public boolean enableWheelAnimation = true;
    public boolean enableHudOverlay = true;
    public int hudPositionX = 10;
    public int hudPositionY = 10;
    public String wheelSoundPath = "minecraft:ui.button.click";
    public String wheelFinishSoundPath = "minecraft:entity.player.levelup";

    // Difficulty progression stages: key = min day, value = weights [easy, medium, hard]
    public Map<String, List<Integer>> difficultyWeightsByDay = new LinkedHashMap<>();

    // Toggles for individual challenges: key = challenge ID, value = enabled
    public Map<String, Boolean> challengeToggles = new HashMap<>();

    public CurseCycleConfig() {
        // Default stages:
        // Day 1+: 3 Easy, 2 Medium, 1 Hard
        difficultyWeightsByDay.put("1", List.of(3, 2, 1));
        // Day 20+: 2 Easy, 3 Medium, 2 Hard
        difficultyWeightsByDay.put("20", List.of(2, 3, 2));
        // Day 50+: 1 Easy, 2 Medium, 3 Hard
        difficultyWeightsByDay.put("50", List.of(1, 2, 3));
    }

    public static CurseCycleConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                CurseCycleConfig config = GSON.fromJson(reader, CurseCycleConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                CurseCycleMod.LOGGER.error("Failed to load CurseCycle config, using defaults", e);
            }
        }
        CurseCycleConfig defaultConfig = new CurseCycleConfig();
        defaultConfig.save();
        return defaultConfig;
    }

    public void save() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            CurseCycleMod.LOGGER.error("Failed to save CurseCycle config", e);
        }
    }

    public List<Integer> getWeightsForDay(long currentDay) {
        List<Integer> bestWeights = List.of(3, 2, 1);
        long maxMatchedDay = -1;

        for (Map.Entry<String, List<Integer>> entry : difficultyWeightsByDay.entrySet()) {
            try {
                long minDay = Long.parseLong(entry.getKey());
                if (currentDay >= minDay && minDay > maxMatchedDay) {
                    maxMatchedDay = minDay;
                    bestWeights = entry.getValue();
                }
            } catch (NumberFormatException ignored) {}
        }
        return bestWeights;
    }
}
