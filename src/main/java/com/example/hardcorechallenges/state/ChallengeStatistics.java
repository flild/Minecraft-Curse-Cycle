package com.example.hardcorechallenges.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

public class ChallengeStatistics {

    private int totalViolations = 0;
    private int deathsByChallenges = 0;
    private final List<String> history = new ArrayList<>(); // Храним строковые представления для простоты (ID + результат)

    public void recordViolation() {
        totalViolations++;
    }

    public void recordDeath() {
        deathsByChallenges++;
    }

    public void addHistory(String entry) {
        history.add(0, entry);
        if (history.size() > 50) {
            history.remove(history.size() - 1);
        }
    }

    public int getTotalViolations() {
        return totalViolations;
    }

    public int getDeathsByChallenges() {
        return deathsByChallenges;
    }

    public List<String> getHistory() {
        return new ArrayList<>(history);
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.putInt("TotalViolations", totalViolations);
        nbt.putInt("DeathsByChallenges", deathsByChallenges);

        NbtList historyList = new NbtList();
        for (String entry : history) {
            NbtCompound entryNbt = new NbtCompound();
            entryNbt.putString("Entry", entry);
            historyList.add(entryNbt);
        }
        nbt.put("History", historyList);
    }

    public void readNbt(NbtCompound nbt) {
        this.totalViolations = nbt.getInt("TotalViolations");
        this.deathsByChallenges = nbt.getInt("DeathsByChallenges");

        this.history.clear();
        if (nbt.contains("History", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("History", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                history.add(list.getCompound(i).getString("Entry"));
            }
        }
    }
}
