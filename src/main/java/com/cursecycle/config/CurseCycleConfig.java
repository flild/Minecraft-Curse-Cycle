package com.cursecycle.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.cursecycle.CurseCycleMod;
import com.cursecycle.challenge.ChallengeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CurseCycleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("cursecycle.json").toFile();
    }

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
        File file = configFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                CurseCycleConfig config = GSON.fromJson(reader, CurseCycleConfig.class);
                if (config != null) {
                    config.validate();
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
            File file = configFile();
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            CurseCycleMod.LOGGER.error("Failed to save CurseCycle config", e);
        }
    }

    public void validate() {
        if (challengeIntervalDays < 1) challengeIntervalDays = 5;
        noticeLeadTimeDays = Math.clamp(noticeLeadTimeDays, 0, challengeIntervalDays);
        maxActiveChallenges = Math.max(1, maxActiveChallenges);
        hudPositionX = Math.max(0, hudPositionX);
        hudPositionY = Math.max(0, hudPositionY);
        if (challengeToggles == null) challengeToggles = new HashMap<>();
        Map<String, List<Integer>> validStages = new LinkedHashMap<>();
        if (difficultyWeightsByDay != null) {
            difficultyWeightsByDay.forEach((day, weights) -> {
                try {
                    if (Long.parseLong(day) >= 1 && validWeights(weights)) {
                        validStages.put(day, List.copyOf(weights));
                    }
                } catch (NumberFormatException ignored) {}
            });
        }
        difficultyWeightsByDay = validStages.isEmpty()
                ? new CurseCycleConfig().difficultyWeightsByDay : validStages;
        if (wheelSoundPath == null || Identifier.tryParse(wheelSoundPath) == null) {
            wheelSoundPath = "minecraft:ui.button.click";
        }
        if (wheelFinishSoundPath == null || Identifier.tryParse(wheelFinishSoundPath) == null) {
            wheelFinishSoundPath = "minecraft:entity.player.levelup";
        }
    }

    private static boolean validWeights(List<Integer> weights) {
        return weights != null && weights.size() == 3
                && weights.stream().allMatch(weight -> weight != null && weight >= 0)
                && weights.stream().anyMatch(weight -> weight > 0);
    }

    public void applyChallengeToggles() {
        ChallengeRegistry.getAll().forEach(challenge ->
                challenge.setEnabled(challengeToggles == null
                        || !Boolean.FALSE.equals(challengeToggles.get(challenge.getId()))));
    }

    public List<Integer> getWeightsForDay(long currentDay) {
        List<Integer> bestWeights = List.of(3, 2, 1);
        long maxMatchedDay = -1;

        if (difficultyWeightsByDay == null) return bestWeights;
        for (Map.Entry<String, List<Integer>> entry : difficultyWeightsByDay.entrySet()) {
            try {
                long minDay = Long.parseLong(entry.getKey());
                if (minDay >= 1 && currentDay >= minDay && minDay > maxMatchedDay && validWeights(entry.getValue())) {
                    maxMatchedDay = minDay;
                    bestWeights = entry.getValue();
                }
            } catch (NumberFormatException ignored) {}
        }
        return bestWeights;
    }
}
