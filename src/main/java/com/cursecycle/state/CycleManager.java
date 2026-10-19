package com.cursecycle.state;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.config.CurseCycleConfig;
import com.cursecycle.network.NetworkHandler;
import com.cursecycle.roulette.RouletteSelector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class CycleManager {

    public static void onServerTick(MinecraftServer server, CurseCycleConfig config) {
        if (server.getOverworld() == null) return;

        CurseCycleState state = CurseCycleState.getServerState(server);

        long timeOfDay = server.getOverworld().getTimeOfDay();
        long calculatedDay = (timeOfDay / 24000L) + 1;
        state.setCurrentDay(calculatedDay);
        state.setTotalInGameTicks(timeOfDay);

        long intervalTicks = config.challengeIntervalDays * 24000L;
        long leadTicks = config.noticeLeadTimeDays * 24000L;
        long spinTriggerTick = intervalTicks - leadTicks;

        long currentCycleIndex = timeOfDay / intervalTicks;
        long ticksInCycle = timeOfDay % intervalTicks;

        // 1-day lead notice window for upcoming cycle
        if (ticksInCycle >= spinTriggerTick) {
            long targetSpinCycle = currentCycleIndex + 1;
            if (state.getLastSpinCycle() < targetSpinCycle) {
                RouletteSelector.WheelResult result = RouletteSelector.selectNextChallenge(config, state);
                if (result != null) {
                    String encodedWinner = result.getEncodedWinnerId();
                    state.setNextChallengeId(encodedWinner);
                    state.setLastSpinCycle(targetSpinCycle);
                    state.setSpinTriggeredForCurrentCycle(true);

                    List<String> candidateIds = result.wheelItems.stream().map(Challenge::getId).toList();
                    NetworkHandler.triggerWheelForAll(server, candidateIds, encodedWinner);

                    server.getPlayerManager().broadcast(
                        Text.literal("🎲 [Curse-Cycle] До запуска следующего испытания остался 1 день! Вращаем колесо...").formatted(Formatting.GOLD),
                        false
                    );
                }
            }
        }

        // Cycle activation check
        if (currentCycleIndex > state.getLastActivatedCycle()) {
            if (state.getLastActivatedCycle() == -1 && currentCycleIndex == 0) {
                if (state.getActiveChallengeIds().isEmpty()) {
                    RouletteSelector.WheelResult initialResult = RouletteSelector.selectNextChallenge(config, state);
                    if (initialResult != null) {
                        activateChallenge(server, state, initialResult.getEncodedWinnerId());
                    }
                }
                state.setLastActivatedCycle(0);
            } else {
                String nextId = state.getNextChallengeId();
                if (nextId != null && !nextId.isEmpty()) {
                    activateChallenge(server, state, nextId);
                    state.setNextChallengeId(null);
                } else {
                    RouletteSelector.WheelResult fallbackResult = RouletteSelector.selectNextChallenge(config, state);
                    if (fallbackResult != null) {
                        activateChallenge(server, state, fallbackResult.getEncodedWinnerId());
                    }
                }
                state.setLastActivatedCycle(currentCycleIndex);
                state.setSpinTriggeredForCurrentCycle(false);
            }
        }

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (!players.isEmpty()) {
            // Tick active challenges (Global)
            for (String id : new ArrayList<>(state.getActiveChallengeIds())) {
                Challenge challenge = ChallengeRegistry.get(id);
                if (challenge != null && challenge.isEnabled()) {
                    for (ServerPlayerEntity player : players) {
                        challenge.onTick(server, player);
                    }
                }
            }

            // Tick active challenges (Personal)
            for (ServerPlayerEntity player : players) {
                List<String> personalIds = state.getPersonalActiveChallengeIds(player.getUuid());
                if (!personalIds.isEmpty()) {
                    for (String pId : new ArrayList<>(personalIds)) {
                        Challenge pChallenge = ChallengeRegistry.get(pId);
                        if (pChallenge != null && pChallenge.isEnabled()) {
                            pChallenge.onTick(server, player);
                        }
                    }
                }
            }
        }

        // Periodic state synchronization to clients
        if (timeOfDay % 20 == 0) {
            long ticksUntilNext = intervalTicks - ticksInCycle;
            NetworkHandler.syncToAll(
                server,
                state.getCurrentDay(),
                ticksUntilNext,
                state.getActiveChallengeIds(),
                state.getNextChallengeId(),
                state.getHistory().stream().map(h -> h.challengeId).toList()
            );
        }
    }

    public static void activateChallenge(MinecraftServer server, CurseCycleState state, String challengeId) {
        if (challengeId == null || challengeId.isEmpty()) return;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

        // Stop current active global challenges
        for (String oldId : new ArrayList<>(state.getActiveChallengeIds())) {
            Challenge oldC = ChallengeRegistry.get(oldId);
            if (oldC != null) {
                for (ServerPlayerEntity p : players) {
                    oldC.onStop(server, p);
                }
            }
        }
        state.getActiveChallengeIds().clear();

        List<String> toActivate = ChallengeUtils.resolveToActiveIds(challengeId);
        String historyEntryId = challengeId;

        if (ChallengeUtils.isDoubleChallenge(challengeId) && !challengeId.startsWith(ChallengeUtils.DOUBLE_CHALLENGE_PREFIX)) {
            historyEntryId = ChallengeUtils.encodeDoubleChallenge(toActivate);
        }

        state.getActiveChallengeIds().addAll(toActivate);
        state.addHistory(historyEntryId, state.getCurrentDay());

        for (String id : toActivate) {
            Challenge c = ChallengeRegistry.get(id);
            if (c != null) {
                for (ServerPlayerEntity p : players) {
                    c.onStart(server, p);
                }
            }
        }

        // Broadcast activation message
        if (toActivate.size() > 1) {
            String names = toActivate.stream().map(id -> {
                Challenge c = ChallengeRegistry.get(id);
                return c != null ? c.getName() : id;
            }).reduce((a, b) -> a + " & " + b).orElse("");

            server.getPlayerManager().broadcast(
                Text.literal("🔥 [Curse-Cycle] Активировано специальное испытание: Два испытания одновременно (" + names + ")!").formatted(Formatting.AQUA, Formatting.BOLD),
                false
            );
        } else if (!toActivate.isEmpty()) {
            Challenge challenge = ChallengeRegistry.get(toActivate.get(0));
            if (challenge != null) {
                Formatting color = challenge.getDifficulty().getColor();
                server.getPlayerManager().broadcast(
                    Text.literal("🔥 [Curse-Cycle] Новое испытание активировано: " + challenge.getName() + "!").formatted(color, Formatting.BOLD),
                    false
                );
            }
        }
    }
}
