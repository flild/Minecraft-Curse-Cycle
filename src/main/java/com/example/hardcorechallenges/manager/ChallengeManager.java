package com.example.hardcorechallenges.manager;

import com.example.hardcorechallenges.api.challenge.Challenge;
import com.example.hardcorechallenges.api.challenge.ChallengeContext;
import com.example.hardcorechallenges.api.challenge.ChallengeState;
import com.example.hardcorechallenges.api.registry.ChallengeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class ChallengeManager {
    private final com.example.hardcorechallenges.state.ChallengeStatistics statistics = new com.example.hardcorechallenges.state.ChallengeStatistics();

    public com.example.hardcorechallenges.state.ChallengeStatistics getStatistics() { return statistics; }

    private com.example.hardcorechallenges.api.challenge.GameMode gameMode = com.example.hardcorechallenges.api.challenge.GameMode.CLASSIC;

    public com.example.hardcorechallenges.api.challenge.GameMode getGameMode() { return gameMode; }
    public void setGameMode(com.example.hardcorechallenges.api.challenge.GameMode mode) { this.gameMode = mode; }

    public void handleViolation(com.example.hardcorechallenges.api.violation.ChallengeViolation violation, com.example.hardcorechallenges.api.violation.ChallengePenalty penalty) {
        if (penalty != null) {
            penalty.apply(violation, this);
            if (violation.player() != null) {
                com.example.hardcorechallenges.network.NetworkHandler.sendToPlayer(violation.player(), new com.example.hardcorechallenges.network.NotificationPayload("Challenge Violation", violation.reason(), 1));
            }
            statistics.recordViolation();
        }
    }

    private static final ChallengeManager INSTANCE = new ChallengeManager();

    private final List<Challenge> activeChallenges = new ArrayList<>();
    private boolean paused = false;
    private long totalTicks = 0;

    private ChallengeManager() {
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // Регистрация глобальных слушателей событий
        com.example.hardcorechallenges.api.event.ChallengeEvents.ON_DAMAGE.register((player, source, amount) -> {
            if (paused) return;
            for (Challenge challenge : activeChallenges) {
                if (challenge.getState() == ChallengeState.ACTIVE) {
                    challenge.onDamage(player, source, amount);
                }
            }
        });
        com.example.hardcorechallenges.api.event.ChallengeEvents.ON_MOVE.register((player, from, to) -> {
            if (paused) return;
            for (Challenge challenge : activeChallenges) {
                if (challenge.getState() == ChallengeState.ACTIVE) {
                    challenge.onMove(player, from, to);
                }
            }
        });
        com.example.hardcorechallenges.api.event.ChallengeEvents.ON_JUMP.register(player -> {
            if (paused) return;
            for (Challenge challenge : activeChallenges) {
                if (challenge.getState() == ChallengeState.ACTIVE) {
                    challenge.onJump(player);
                }
            }
        });
        com.example.hardcorechallenges.api.event.ChallengeEvents.ON_SPRINT.register(new com.example.hardcorechallenges.api.event.ChallengeEvents.Sprint() {
            @Override
            public void onSprintStart(net.minecraft.server.network.ServerPlayerEntity player) {
                if (paused) return;
                for (Challenge challenge : activeChallenges) {
                    if (challenge.getState() == ChallengeState.ACTIVE) {
                        challenge.onSprintStart(player);
                    }
                }
            }

            @Override
            public void onSprintStop(net.minecraft.server.network.ServerPlayerEntity player) {
                if (paused) return;
                for (Challenge challenge : activeChallenges) {
                    if (challenge.getState() == ChallengeState.ACTIVE) {
                        challenge.onSprintStop(player);
                    }
                }
            }
        });
    }

    public static ChallengeManager getInstance() {
        return INSTANCE;
    }

    public void startChallenge(Identifier id, MinecraftServer server) {
        Challenge challenge = ChallengeRegistry.createChallenge(id);
        if (challenge != null) {
            ChallengeContext context = new ChallengeContext(server, null);
            challenge.onActivate(context);
            challenge.setState(ChallengeState.ACTIVE);
            activeChallenges.add(challenge);
            statistics.addHistory("Started: " + challenge.getDefinition().name());
        }
    }

    public void stopChallenge(Identifier id, MinecraftServer server) {
        activeChallenges.removeIf(challenge -> {
            if (challenge.getDefinition().id().equals(id)) {
                challenge.onDeactivate(new ChallengeContext(server, null));
                challenge.setState(ChallengeState.INACTIVE);
            statistics.addHistory("Stopped: " + challenge.getDefinition().name());
                return true;
            }
            return false;
        });
    }

    public void stopAll(MinecraftServer server) {
        ChallengeContext context = new ChallengeContext(server, null);
        for (Challenge challenge : activeChallenges) {
            challenge.onDeactivate(context);
            challenge.setState(ChallengeState.INACTIVE);
            statistics.addHistory("Stopped: " + challenge.getDefinition().name());
        }
        activeChallenges.clear();
    }

    public void pause() {
        this.paused = true;
    }

    public void resume() {
        this.paused = false;
    }

    public boolean isPaused() {
        return paused;
    }

    private void onServerTick(MinecraftServer server) {
        if (paused) return;

        totalTicks++;
        ChallengeContext context = new ChallengeContext(server, null);

        for (Challenge challenge : activeChallenges) {
            if (challenge.getState() == ChallengeState.ACTIVE) {
                challenge.tick(context);
            }
        }
    }

    public List<Challenge> getActiveChallenges() {
        return new ArrayList<>(activeChallenges);
    }

    public long getTotalTicks() {
        return totalTicks;
    }

    public void setTotalTicks(long totalTicks) {
        this.totalTicks = totalTicks;
    }

    public void restoreChallenge(Challenge challenge) {
        activeChallenges.add(challenge);
            statistics.addHistory("Started: " + challenge.getDefinition().name());
    }

    public void clearChallenges() {
        activeChallenges.clear();
    }

    public int getPassedDays() {
        return (int) (totalTicks / 24000L);
    }
}
