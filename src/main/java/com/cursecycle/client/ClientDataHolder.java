package com.cursecycle.client;

import com.cursecycle.challenge.impl.BlockedDirectionChallenge;
import com.cursecycle.challenge.impl.BlockedDirectionChallenge.Direction;
import java.util.ArrayList;
import java.util.List;

public class ClientDataHolder {
    private static long currentDay = 1;
    private static long ticksUntilNext = 0;
    private static final List<String> activeChallengeIds = new ArrayList<>();
    private static String nextChallengeId = "";
    private static final List<String> historyChallengeIds = new ArrayList<>();
    private static boolean synced = false;
    private static Direction blockedDirection;
    private static java.util.Set<String> eatenFood = java.util.Set.of();
    public static void updateFoodHistory(List<String> ids) { eatenFood = java.util.Set.copyOf(ids); }
    public static boolean hasEatenFood(String id) { return eatenFood.contains(id); }

    public static synchronized void reset() {
        MovementMemoryInput.reset();
        currentDay = 1;
        ticksUntilNext = 0;
        activeChallengeIds.clear();
        nextChallengeId = "";
        historyChallengeIds.clear();
        synced = false;
        blockedDirection = null;
        eatenFood = java.util.Set.of();
    }

    public static synchronized void updateState(long day, long ticks, List<String> activeIds, String nextId, List<String> history) {
        updateState(day, ticks, activeIds, nextId, history, "");
    }

    public static synchronized void updateState(long day, long ticks, List<String> activeIds, String nextId, List<String> history, String directionId) {
        currentDay = day;
        ticksUntilNext = ticks;
        activeChallengeIds.clear();
        activeChallengeIds.addAll(activeIds);
        if (!activeIds.contains(com.cursecycle.challenge.impl.MovementMemoryChallenge.ID)) MovementMemoryInput.reset();
        nextChallengeId = nextId;
        historyChallengeIds.clear();
        historyChallengeIds.addAll(history);
        if (!activeChallengeIds.contains("one_time_menu")) eatenFood = java.util.Set.of();
        blockedDirection = activeChallengeIds.contains(BlockedDirectionChallenge.ID) ? Direction.fromId(directionId) : null;
        synced = true;
    }

    public static boolean isSynced() { return synced; }
    public static Direction getBlockedDirection() { return blockedDirection; }
    public static long getCurrentDay() { return currentDay; }
    public static long getTicksUntilNext() { return ticksUntilNext; }
    public static List<String> getActiveChallengeIds() { return activeChallengeIds; }
    public static String getNextChallengeId() { return nextChallengeId; }
    public static List<String> getHistoryChallengeIds() { return historyChallengeIds; }
}
