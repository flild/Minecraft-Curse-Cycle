package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;

import java.util.function.IntUnaryOperator;

public final class BlockedDirectionChallenge extends Challenge {
    public static final String ID = "blocked_direction";

    public enum Direction {
        LEFT("left", "влево"), RIGHT("right", "вправо"), FORWARD("forward", "вперёд");

        private final String id;
        private final String label;
        Direction(String id, String label) { this.id = id; this.label = label; }
        public String id() { return id; }
        public String feedback() { return "Нельзя: " + label; }
        public static Direction fromId(String id) {
            for (var direction : values()) if (direction.id.equals(id)) return direction;
            return null;
        }
    }

    // Технические нюансы; не включать в описание для игрока.
    // При активации выбирается одно общее направление и блокируется соответствующая составляющая собственного
    // движения. Назад идти можно всегда; поворот камеры не ограничивается.
    public BlockedDirectionChallenge() {
        super(ID, "Туда нельзя",
                "Одно из направлений движения больше не работает.",
                "Вперёд, влево или вправо — одно направление случайно отключается до конца испытания.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }

    public static Direction reconcile(MinecraftServer server) {
        return reconcile(CurseCycleState.getServerState(server), server.getOverworld().random::nextInt);
    }

    /** World-owned choice, independent of online players and their lifecycle callbacks. */
    public static Direction reconcile(CurseCycleState state, IntUnaryOperator randomIndex) {
        var challenge = ChallengeRegistry.get(ID);
        boolean active = challenge != null && challenge.isEnabled()
                && (state.getActiveChallengeIds().contains(ID)
                || state.getPersonalActiveChallenges().values().stream().anyMatch(ids -> ids.contains(ID)));
        var data = state.getChallengeData();
        if (!active) {
            if (data.contains(ID)) { data.remove(ID); state.markDirty(); }
            return null;
        }
        var saved = data.getCompound(ID);
        var direction = Direction.fromId(saved.getString("direction"));
        if (direction != null && saved.getLong("activationSerial") == state.getActivationSerial()) return direction;

        direction = Direction.values()[randomIndex.applyAsInt(Direction.values().length)];
        var choice = new NbtCompound();
        choice.putLong("activationSerial", state.getActivationSerial());
        choice.putString("direction", direction.id());
        data.put(ID, choice);
        state.markDirty();
        return direction;
    }
}
