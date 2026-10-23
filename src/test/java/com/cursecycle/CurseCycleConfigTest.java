package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.config.CurseCycleConfig;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CurseCycleConfigTest {
    @Test
    void malformedValuesInValidJsonRecoverToSafeDefaults() {
        var config = new Gson().fromJson("""
                {"challengeIntervalDays":0,"noticeLeadTimeDays":999,
                 "difficultyWeightsByDay":null,"challengeToggles":null,
                 "wheelSoundPath":null,"wheelFinishSoundPath":"INVALID SOUND"}
                """, CurseCycleConfig.class);
        config.validate();
        assertEquals(5, config.challengeIntervalDays);
        assertEquals(5, config.noticeLeadTimeDays);
        assertEquals(List.of(3, 2, 1), config.getWeightsForDay(1));
        assertNotNull(config.challengeToggles);
        assertEquals("minecraft:ui.button.click", config.wheelSoundPath);
        assertEquals("minecraft:entity.player.levelup", config.wheelFinishSoundPath);
    }

    @Test
    void invalidWeightStagesDoNotOverrideValidStages() {
        var config = new CurseCycleConfig();
        config.difficultyWeightsByDay = new LinkedHashMap<>();
        config.difficultyWeightsByDay.put("1", List.of(1, 0, 0));
        config.difficultyWeightsByDay.put("2", List.of(1));
        config.difficultyWeightsByDay.put("3", List.of(0, 0, 0));
        config.difficultyWeightsByDay.put("4", List.of(-1, 2, 1));
        config.difficultyWeightsByDay.put("5", Arrays.asList(1, null, 1));
        config.difficultyWeightsByDay.put("not-a-day", List.of(1, 1, 1));
        assertEquals(List.of(1, 0, 0), config.getWeightsForDay(100));
        config.validate();
        assertEquals(Map.of("1", List.of(1, 0, 0)), config.difficultyWeightsByDay);
    }

    @Test
    void emptyStagesRestoreProgressionAndNegativeNoticeIsClamped() {
        var config = new CurseCycleConfig();
        config.difficultyWeightsByDay.clear();
        config.noticeLeadTimeDays = -1;
        config.validate();
        assertEquals(0, config.noticeLeadTimeDays);
        assertEquals(List.of(2, 3, 2), config.getWeightsForDay(20));
        assertEquals(List.of(1, 2, 3), config.getWeightsForDay(50));
    }

    @Test
    void togglesApplyToTheRegistryAndCanBeReenabled() {
        ChallengeRegistry.clear();
        ModChallenges.registerAll();
        var config = new CurseCycleConfig();
        config.challengeToggles.put("no_sprint", false);
        config.challengeToggles.put("no_jumping", false);
        config.applyChallengeToggles();
        assertFalse(ChallengeRegistry.get("no_sprint").isEnabled());
        assertFalse(ChallengeRegistry.get("no_jumping").isEnabled());
        assertTrue(ChallengeRegistry.get("test_easy").isEnabled());
        config.challengeToggles.clear();
        config.applyChallengeToggles();
        assertTrue(ChallengeRegistry.get("no_sprint").isEnabled());
    }
}
