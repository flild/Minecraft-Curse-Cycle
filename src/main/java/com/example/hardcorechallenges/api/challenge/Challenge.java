package com.example.hardcorechallenges.api.challenge;

import com.example.hardcorechallenges.api.violation.ChallengePenalty;
import com.example.hardcorechallenges.api.violation.ChallengeViolation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.Vec3d;

public interface Challenge {
    ChallengeDefinition getDefinition();

    void onActivate(ChallengeContext context);
    void onDeactivate(ChallengeContext context);

    void tick(ChallengeContext context);

    default void onDamage(ServerPlayerEntity player, DamageSource source, float amount) {}
    default void onMove(ServerPlayerEntity player, Vec3d from, Vec3d to) {}
    default void onJump(ServerPlayerEntity player) {}
    default void onSprintStart(ServerPlayerEntity player) {}
    default void onSprintStop(ServerPlayerEntity player) {}

    default void onViolation(ChallengeViolation violation) {
        // Дефолтная реализация может просто получать менеджер и применять наказание,
        // но здесь нам нужен доступ к менеджеру, поэтому мы оставим метод для переопределения
        // или вызовем глобальный менеджер.
        com.example.hardcorechallenges.manager.ChallengeManager.getInstance().handleViolation(violation, getPenalty(violation));
    }

    default ChallengePenalty getPenalty(ChallengeViolation violation) {
        return new com.example.hardcorechallenges.api.violation.WarningPenalty();
    }

    ChallengeState getState();
    void setState(ChallengeState state);

    void writeNbt(NbtCompound nbt);
    void readNbt(NbtCompound nbt);
}
