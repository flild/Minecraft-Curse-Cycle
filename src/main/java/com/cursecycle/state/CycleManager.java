package com.cursecycle.state;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.challenge.impl.BlockedDirectionChallenge;
import com.cursecycle.config.CurseCycleConfig;
import com.cursecycle.network.NetworkHandler;
import com.cursecycle.roulette.RouletteSelector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.Consumer;

public class CycleManager {

    public static void onServerTick(MinecraftServer server, CurseCycleConfig config) {
        if (server.getOverworld() == null) return;

        CurseCycleState state = CurseCycleState.getServerState(server);
        updateSchedule(server, config);

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        var globalIds = players.isEmpty() ? java.util.Set.<String>of()
                : new java.util.LinkedHashSet<>(state.getActiveChallengeIds());
        for (String id : globalIds) {
            Challenge challenge = ChallengeRegistry.get(id);
            if (challenge != null && challenge.isEnabled()) {
                for (ServerPlayerEntity player : players) challenge.onTick(server, player);
            }
        }
        for (ServerPlayerEntity player : players) {
            var personalIds = state.findPersonalActiveChallengeIds(player.getUuid());
            if (personalIds.isEmpty()) continue;
            for (String id : new java.util.LinkedHashSet<>(personalIds)) {
                if (globalIds.contains(id)) continue;
                Challenge challenge = ChallengeRegistry.get(id);
                if (challenge != null && challenge.isEnabled()) challenge.onTick(server, player);
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
        BlockedDirectionChallenge.reconcile(server);
    }

    // Keep scheduling separate from player effects and network I/O so time jumps can be tested.
    static void updateCycle(CurseCycleState state, CurseCycleConfig config, long timeOfDay,
                            Consumer<String> activate, Consumer<RouletteSelector.WheelResult> announce) {
        timeOfDay = Math.max(0, timeOfDay);
        state.setCurrentDay(timeOfDay / 24000L + 1);
        state.setTotalInGameTicks(timeOfDay);
        long intervalTicks = config.challengeIntervalDays * 24000L;
        long cycleTicks = cycleTicks(state, timeOfDay);
        long currentCycle = cycleTicks / intervalTicks;
        long ticksInCycle = cycleTicks % intervalTicks;

        // Saved selections can reference challenges disabled since the last server session.
        boolean removed = state.getActiveChallengeIds().removeIf(id -> !isEnabled(id));
        for (var it = state.getPersonalActiveChallenges().values().iterator(); it.hasNext();) {
            var personalIds = it.next();
            if (personalIds.removeIf(id -> !isEnabled(id))) state.markDirty();
            // Old read-side lookups and removed assignments may leave empty UUID records.
            if (personalIds.isEmpty()) { it.remove(); state.markDirty(); }
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
        return !subIds.isEmpty() && subIds.stream().noneMatch(ChallengeUtils::isDoubleChallenge)
                && subIds.stream().allMatch(CycleManager::isEnabled);
    }

    public static RouletteSelector.WheelResult selectUpcomingChallenge(CurseCycleState state,
                                                                       CurseCycleConfig config, long timeOfDay) {
        RouletteSelector.WheelResult result = RouletteSelector.selectNextChallenge(config, state);
        if (result != null) {
            state.setNextChallengeId(result.getEncodedWinnerId());
            state.setLastSpinCycle(cycleTicks(state, timeOfDay) / (config.challengeIntervalDays * 24000L) + 1);
            state.setSpinTriggeredForCurrentCycle(true);
        }
        return result;
    }

    public static void syncState(MinecraftServer server, CurseCycleState state, CurseCycleConfig config) {
        long timeOfDay = Math.max(0, server.getOverworld().getTimeOfDay());
        NetworkHandler.syncToAll(server, timeOfDay / 24000L + 1, ticksUntilNext(state, config, timeOfDay),
                state.getActiveChallengeIds(), state.getNextChallengeId(),
                state.getHistory().stream().map(h -> h.challengeId).toList());
    }

    private static long cycleTicks(CurseCycleState state, long timeOfDay) {
        return Math.max(0, Math.max(0, timeOfDay) - state.getCycleOriginTicks());
    }

    public static long ticksUntilNext(CurseCycleState state, CurseCycleConfig config, long timeOfDay) {
        long intervalTicks = config.challengeIntervalDays * 24000L;
        return intervalTicks - cycleTicks(state, timeOfDay) % intervalTicks;
    }

    /** Resolve and validate before stopping anything; failures leave the current cycle intact. */
    public static boolean activateManually(MinecraftServer server, CurseCycleState state, String challengeId) {
        if (!isSelectable(challengeId)) return false;
        String resolved = challengeId;
        if (ChallengeUtils.isDoubleChallenge(challengeId)
                && !challengeId.startsWith(ChallengeUtils.DOUBLE_CHALLENGE_PREFIX)) {
            List<String> children = ChallengeUtils.resolveToActiveIds(challengeId);
            if (children.isEmpty()) return false;
            resolved = ChallengeUtils.encodeDoubleChallenge(children);
        }
        if (!isSelectable(resolved)) return false;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            var oldIds = new java.util.LinkedHashSet<>(state.getActiveChallengeIds());
            oldIds.addAll(state.findPersonalActiveChallengeIds(player.getUuid()));
            for (String oldId : oldIds) {
                Challenge old = ChallengeRegistry.get(oldId);
                if (old != null) old.onStop(server, player);
            }
        }
        state.getActiveChallengeIds().clear();
        state.getPersonalActiveChallenges().clear();
        state.restartCycleAt(server.getOverworld().getTimeOfDay());
        // Direct activation also counts as a draw without emptying the remaining bag.
        String baseId = ChallengeUtils.getBaseChallengeId(resolved);
        state.getCurrentPool().removeIf(baseId::equals);
        state.setLastDrawnChallengeId(baseId);
        activateChallenge(server, state, resolved);
        return true;
    }

    public static void activateChallenge(MinecraftServer server, CurseCycleState state, String challengeId) {
        if (challengeId == null || challengeId.isEmpty()) return;

        List<String> toActivate = ChallengeUtils.resolveToActiveIds(challengeId).stream()
                .filter(CycleManager::isEnabled).distinct().toList();
        if (toActivate.isEmpty()) return;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

        // Stop current active global challenges
        for (String oldId : new java.util.LinkedHashSet<>(state.getActiveChallengeIds())) {
            Challenge oldC = ChallengeRegistry.get(oldId);
            if (oldC != null) {
                for (ServerPlayerEntity p : players) {
                    oldC.onStop(server, p);
                }
            }
        }
        state.getActiveChallengeIds().clear();
        state.beginActivation();

        String historyEntryId = challengeId;

        if (ChallengeUtils.isDoubleChallenge(challengeId) && !challengeId.startsWith(ChallengeUtils.DOUBLE_CHALLENGE_PREFIX)) {
            historyEntryId = ChallengeUtils.encodeDoubleChallenge(toActivate);
        }

        state.getActiveChallengeIds().addAll(toActivate);
        BlockedDirectionChallenge.reconcile(server);
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
