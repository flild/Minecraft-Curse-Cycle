package com.cursecycle.state;

import com.cursecycle.CurseCycleMod;
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
import java.util.function.Consumer;

public class CycleManager {

    public static void onServerTick(MinecraftServer server, CurseCycleConfig config) {
        if (server.getOverworld() == null) return;

        CurseCycleState state = CurseCycleState.getServerState(server);
        updateSchedule(server, config);

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

        // Server ticks keep advancing even when the daylight cycle is disabled.
        if (server.getTicks() % 20 == 0) {
            syncState(server, state, config);
        }
    }

    public static void updateSchedule(MinecraftServer server, CurseCycleConfig config) {
        CurseCycleState state = CurseCycleState.getServerState(server);
        updateCycle(state, config, server.getOverworld().getTimeOfDay(),
                id -> activateChallenge(server, state, id),
                result -> {
                    List<String> candidateIds = result.wheelItems.stream().map(Challenge::getId).toList();
                    NetworkHandler.triggerWheelForAll(server, candidateIds, result.getEncodedWinnerId());
                    server.getPlayerManager().broadcast(
                            Text.literal("🎲 [Curse-Cycle] Следующее испытание выбрано! Вращаем колесо...")
                                    .formatted(Formatting.GOLD), false);
                });
    }

    // Keep scheduling separate from player effects and network I/O so time jumps can be tested.
    static void updateCycle(CurseCycleState state, CurseCycleConfig config, long timeOfDay,
                            Consumer<String> activate, Consumer<RouletteSelector.WheelResult> announce) {
        timeOfDay = Math.max(0, timeOfDay);
        state.setCurrentDay(timeOfDay / 24000L + 1);
        state.setTotalInGameTicks(timeOfDay);
        long intervalTicks = config.challengeIntervalDays * 24000L;
        long currentCycle = timeOfDay / intervalTicks;
        long ticksInCycle = timeOfDay % intervalTicks;

        // Saved selections can reference challenges disabled since the last server session.
        boolean removed = state.getActiveChallengeIds().removeIf(id -> !isEnabled(id));
        for (List<String> personalIds : state.getPersonalActiveChallenges().values()) {
            if (personalIds.removeIf(id -> !isEnabled(id))) state.markDirty();
        }
        if (removed) {
            state.markDirty();
            if (state.getActiveChallengeIds().isEmpty()) state.setLastActivatedCycle(-1);
        }

        // Consume the existing announcement before preparing the following cycle's draw.
        if (currentCycle > state.getLastActivatedCycle()) {
            if (state.getLastActivatedCycle() != -1 || state.getActiveChallengeIds().isEmpty()) {
                String nextId = state.getNextChallengeId();
                if (!isSelectable(nextId)) {
                    RouletteSelector.WheelResult result = RouletteSelector.selectNextChallenge(config, state);
                    nextId = result == null ? null : result.getEncodedWinnerId();
                }
                if (nextId != null) activate.accept(nextId);
                state.setNextChallengeId(null);
            }
            state.setLastActivatedCycle(currentCycle);
            state.setSpinTriggeredForCurrentCycle(false);
        }

        long spinTriggerTick = intervalTicks - config.noticeLeadTimeDays * 24000L;
        if (ticksInCycle >= spinTriggerTick && state.getLastSpinCycle() < currentCycle + 1) {
            RouletteSelector.WheelResult result = selectUpcomingChallenge(state, config, timeOfDay);
            if (result != null) announce.accept(result);
        }
    }

    private static boolean isEnabled(String id) {
        Challenge challenge = ChallengeRegistry.get(id);
        return challenge != null && challenge.isEnabled();
    }

    private static boolean isSelectable(String id) {
        if (id == null || !isEnabled(ChallengeUtils.getBaseChallengeId(id))) return false;
        if (!id.startsWith(ChallengeUtils.DOUBLE_CHALLENGE_PREFIX)) return true;
        List<String> subIds = ChallengeUtils.parseSubChallengeIds(id);
        return !subIds.isEmpty() && subIds.stream().allMatch(CycleManager::isEnabled);
    }

    public static RouletteSelector.WheelResult selectUpcomingChallenge(CurseCycleState state,
                                                                       CurseCycleConfig config, long timeOfDay) {
        RouletteSelector.WheelResult result = RouletteSelector.selectNextChallenge(config, state);
        if (result != null) {
            state.setNextChallengeId(result.getEncodedWinnerId());
            state.setLastSpinCycle(Math.max(0, timeOfDay) / (config.challengeIntervalDays * 24000L) + 1);
            state.setSpinTriggeredForCurrentCycle(true);
        }
        return result;
    }

    public static void syncState(MinecraftServer server, CurseCycleState state, CurseCycleConfig config) {
        long timeOfDay = Math.max(0, server.getOverworld().getTimeOfDay());
        long intervalTicks = config.challengeIntervalDays * 24000L;
        NetworkHandler.syncToAll(server, timeOfDay / 24000L + 1, intervalTicks - timeOfDay % intervalTicks,
                state.getActiveChallengeIds(), state.getNextChallengeId(),
                state.getHistory().stream().map(h -> h.challengeId).toList());
    }

    public static void activateChallenge(MinecraftServer server, CurseCycleState state, String challengeId) {
        if (challengeId == null || challengeId.isEmpty()) return;

        List<String> toActivate = ChallengeUtils.resolveToActiveIds(challengeId).stream()
                .filter(CycleManager::isEnabled).toList();
        if (toActivate.isEmpty()) return;

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
        // Camera restrictions must reach clients immediately, including scheduled activations.
        syncState(server, state, CurseCycleMod.getConfig());
    }
}
