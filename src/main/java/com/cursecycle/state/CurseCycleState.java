package com.cursecycle.state;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.*;

public class CurseCycleState extends PersistentState {
    public static final String STATE_KEY = "cursecycle_state";

    private long totalInGameTicks = 0;
    private long currentDay = 1;
    private final List<String> activeChallengeIds = new ArrayList<>();
    private String nextChallengeId = null;
    private boolean spinTriggeredForCurrentCycle = false;
    private long lastActivatedCycle = -1;
    private long lastSpinCycle = -1;
    private final Map<UUID, List<String>> personalActiveChallenges = new HashMap<>();
    private final List<HistoryEntry> history = new ArrayList<>();
    private final List<String> currentPool = new ArrayList<>();
    private String lastDrawnChallengeId = null;

    public static class HistoryEntry {
        public final String challengeId;
        public final long day;
        public final long timestamp;

        public HistoryEntry(String challengeId, long day, long timestamp) {
            this.challengeId = challengeId;
            this.day = day;
            this.timestamp = timestamp;
        }

        public NbtCompound writeNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("challengeId", challengeId);
            nbt.putLong("day", day);
            nbt.putLong("timestamp", timestamp);
            return nbt;
        }

        public static HistoryEntry readNbt(NbtCompound nbt) {
            return new HistoryEntry(
                nbt.getString("challengeId"),
                nbt.getLong("day"),
                nbt.getLong("timestamp")
            );
        }
    }

    public CurseCycleState() {}

    public void reset() {
        totalInGameTicks = 0;
        currentDay = 1;
        activeChallengeIds.clear();
        nextChallengeId = null;
        spinTriggeredForCurrentCycle = false;
        lastActivatedCycle = -1;
        lastSpinCycle = -1;
        personalActiveChallenges.clear();
        history.clear();
        currentPool.clear();
        lastDrawnChallengeId = null;
        markDirty();
    }

    public static CurseCycleState getServerState(MinecraftServer server) {
        PersistentStateManager stateManager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        CurseCycleState state = stateManager.get(new Type<>(
            CurseCycleState::new,
            CurseCycleState::createFromNbt,
            null
        ), STATE_KEY);
        if (state == null) {
            state = new CurseCycleState();
            stateManager.set(STATE_KEY, state);
        }
        return state;
    }

    public static CurseCycleState createFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        CurseCycleState state = new CurseCycleState();
        state.totalInGameTicks = tag.getLong("totalInGameTicks");
        state.currentDay = tag.getLong("currentDay");
        if (state.currentDay < 1) state.currentDay = 1;

        state.activeChallengeIds.clear();
        NbtList activeList = tag.getList("activeChallengeIds", NbtElement.STRING_TYPE);
        for (int i = 0; i < activeList.size(); i++) {
            state.activeChallengeIds.add(activeList.getString(i));
        }

        if (tag.contains("nextChallengeId")) {
            state.nextChallengeId = tag.getString("nextChallengeId");
            if (state.nextChallengeId.isEmpty()) state.nextChallengeId = null;
        }

        state.spinTriggeredForCurrentCycle = tag.getBoolean("spinTriggeredForCurrentCycle");
        if (tag.contains("lastActivatedCycle")) {
            state.lastActivatedCycle = tag.getLong("lastActivatedCycle");
        }
        if (tag.contains("lastSpinCycle")) {
            state.lastSpinCycle = tag.getLong("lastSpinCycle");
        }

        state.personalActiveChallenges.clear();
        if (tag.contains("personalActiveChallenges")) {
            NbtCompound personalTag = tag.getCompound("personalActiveChallenges");
            for (String uuidStr : personalTag.getKeys()) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    NbtList list = personalTag.getList(uuidStr, NbtElement.STRING_TYPE);
                    List<String> pList = new ArrayList<>();
                    for (int i = 0; i < list.size(); i++) {
                        pList.add(list.getString(i));
                    }
                    state.personalActiveChallenges.put(uuid, pList);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        state.history.clear();
        NbtList historyList = tag.getList("history", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < historyList.size(); i++) {
            state.history.add(HistoryEntry.readNbt(historyList.getCompound(i)));
        }

        state.currentPool.clear();
        NbtList poolList = tag.getList("currentPool", NbtElement.STRING_TYPE);
        for (int i = 0; i < poolList.size(); i++) {
            state.currentPool.add(poolList.getString(i));
        }

        if (tag.contains("lastDrawnChallengeId")) {
            state.lastDrawnChallengeId = tag.getString("lastDrawnChallengeId");
            if (state.lastDrawnChallengeId.isEmpty()) state.lastDrawnChallengeId = null;
        }

        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putLong("totalInGameTicks", totalInGameTicks);
        tag.putLong("currentDay", currentDay);

        NbtList activeList = new NbtList();
        for (String id : activeChallengeIds) {
            activeList.add(NbtString.of(id));
        }
        tag.put("activeChallengeIds", activeList);

        if (nextChallengeId != null) {
            tag.putString("nextChallengeId", nextChallengeId);
        }

        tag.putBoolean("spinTriggeredForCurrentCycle", spinTriggeredForCurrentCycle);
        tag.putLong("lastActivatedCycle", lastActivatedCycle);
        tag.putLong("lastSpinCycle", lastSpinCycle);

        NbtCompound personalTag = new NbtCompound();
        for (Map.Entry<UUID, List<String>> entry : personalActiveChallenges.entrySet()) {
            NbtList list = new NbtList();
            for (String id : entry.getValue()) {
                list.add(NbtString.of(id));
            }
            personalTag.put(entry.getKey().toString(), list);
        }
        tag.put("personalActiveChallenges", personalTag);

        NbtList historyList = new NbtList();
        for (HistoryEntry entry : history) {
            historyList.add(entry.writeNbt());
        }
        tag.put("history", historyList);

        NbtList poolList = new NbtList();
        for (String id : currentPool) {
            poolList.add(NbtString.of(id));
        }
        tag.put("currentPool", poolList);

        if (lastDrawnChallengeId != null) {
            tag.putString("lastDrawnChallengeId", lastDrawnChallengeId);
        }

        return tag;
    }

    // Getters and setters
    public long getTotalInGameTicks() { return totalInGameTicks; }
    public void setTotalInGameTicks(long totalInGameTicks) { this.totalInGameTicks = totalInGameTicks; markDirty(); }

    public long getCurrentDay() { return currentDay; }
    public void setCurrentDay(long currentDay) { this.currentDay = currentDay; markDirty(); }

    public List<String> getActiveChallengeIds() { return activeChallengeIds; }

    public String getNextChallengeId() { return nextChallengeId; }
    public void setNextChallengeId(String nextChallengeId) { this.nextChallengeId = nextChallengeId; markDirty(); }

    public boolean isSpinTriggeredForCurrentCycle() { return spinTriggeredForCurrentCycle; }
    public void setSpinTriggeredForCurrentCycle(boolean spinTriggeredForCurrentCycle) { this.spinTriggeredForCurrentCycle = spinTriggeredForCurrentCycle; markDirty(); }

    public long getLastActivatedCycle() { return lastActivatedCycle; }
    public void setLastActivatedCycle(long lastActivatedCycle) { this.lastActivatedCycle = lastActivatedCycle; markDirty(); }

    public long getLastSpinCycle() { return lastSpinCycle; }
    public void setLastSpinCycle(long lastSpinCycle) { this.lastSpinCycle = lastSpinCycle; markDirty(); }

    public Map<UUID, List<String>> getPersonalActiveChallenges() { return personalActiveChallenges; }

    public List<String> getPersonalActiveChallengeIds(UUID uuid) {
        return personalActiveChallenges.computeIfAbsent(uuid, k -> new ArrayList<>());
    }

    public List<HistoryEntry> getHistory() { return history; }
    public void addHistory(String challengeId, long day) {
        history.add(new HistoryEntry(challengeId, day, System.currentTimeMillis()));
        markDirty();
    }

    public List<String> getCurrentPool() { return currentPool; }
    public String getLastDrawnChallengeId() { return lastDrawnChallengeId; }
    public void setLastDrawnChallengeId(String lastDrawnChallengeId) { this.lastDrawnChallengeId = lastDrawnChallengeId; markDirty(); }
}
