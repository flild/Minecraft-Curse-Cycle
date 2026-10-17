package com.cursecycle;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.command.CurseCycleCommand;
import com.cursecycle.config.CurseCycleConfig;
import com.cursecycle.network.NetworkHandler;
import com.cursecycle.roulette.RouletteSelector;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CurseCycleMod implements ModInitializer {
    public static final String MOD_ID = "cursecycle";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static CurseCycleConfig config;

    public static CurseCycleConfig getConfig() {
        if (config == null) {
            config = CurseCycleConfig.load();
        }
        return config;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Curse-Cycle Mod...");

        // Load config & register challenges
        config = CurseCycleConfig.load();
        ModChallenges.registerAll();
        com.cursecycle.event.ChallengeEventManager.registerEvents();

        // Register payloads & commands
        NetworkHandler.registerPayloads();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CurseCycleCommand.register(dispatcher));

        // Player Join Event -> sync state & re-apply active challenges
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            CurseCycleState state = CurseCycleState.getServerState(server);

            long intervalTicks = config.challengeIntervalDays * 24000L;
            long timeOfDay = server.getOverworld().getTimeOfDay();
            long ticksInCycle = timeOfDay % intervalTicks;
            long ticksUntilNext = intervalTicks - ticksInCycle;

            NetworkHandler.syncToPlayer(
                player,
                state.getCurrentDay(),
                ticksUntilNext,
                state.getActiveChallengeIds(),
                state.getNextChallengeId(),
                state.getHistory().stream().map(h -> h.challengeId).toList()
            );

            for (String id : state.getActiveChallengeIds()) {
                Challenge c = ChallengeRegistry.get(id);
                if (c != null && c.isEnabled()) {
                    c.onPlayerJoin(server, player);
                }
            }

            List<String> personalIds = state.getPersonalActiveChallengeIds(player.getUuid());
            for (String pId : personalIds) {
                Challenge pc = ChallengeRegistry.get(pId);
                if (pc != null && pc.isEnabled()) {
                    pc.onPlayerJoin(server, player);
                }
            }
        });

        // Player Disconnect Event -> cleanup active challenge effects
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            CurseCycleState state = CurseCycleState.getServerState(server);

            for (String id : state.getActiveChallengeIds()) {
                Challenge c = ChallengeRegistry.get(id);
                if (c != null && c.isEnabled()) {
                    c.onPlayerLeave(server, player);
                }
            }

            List<String> personalIds = state.getPersonalActiveChallengeIds(player.getUuid());
            for (String pId : personalIds) {
                Challenge pc = ChallengeRegistry.get(pId);
                if (pc != null && pc.isEnabled()) {
                    pc.onPlayerLeave(server, player);
                }
            }
        });

        // Server Tick Event -> cycle logic
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
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

        // Check if in 1-day lead notice window for upcoming cycle
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

        // Check cycle activation
        if (currentCycleIndex > state.getLastActivatedCycle()) {
            if (state.getLastActivatedCycle() == -1 && currentCycleIndex == 0) {
                // Fresh world initialization
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

        // Tick active challenges (Global)
        for (String id : new ArrayList<>(state.getActiveChallengeIds())) {
            Challenge challenge = ChallengeRegistry.get(id);
            if (challenge != null && challenge.isEnabled()) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    challenge.onTick(server, player);
                }
            }
        }

        // Tick active challenges (Personal)
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            List<String> personalIds = state.getPersonalActiveChallengeIds(player.getUuid());
            for (String pId : new ArrayList<>(personalIds)) {
                Challenge pChallenge = ChallengeRegistry.get(pId);
                if (pChallenge != null && pChallenge.isEnabled()) {
                    pChallenge.onTick(server, player);
                }
            }
        }

        // Sync every 20 ticks
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

        // Stop current active global challenges
        for (String oldId : new ArrayList<>(state.getActiveChallengeIds())) {
            Challenge oldC = ChallengeRegistry.get(oldId);
            if (oldC != null) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    oldC.onStop(server, p);
                }
            }
        }
        state.getActiveChallengeIds().clear();

        List<String> toActivate = new ArrayList<>();
        String historyEntryId = challengeId;

        if (challengeId.startsWith("double_challenge:")) {
            String[] parts = challengeId.substring("double_challenge:".length()).split(",");
            for (String sub : parts) {
                if (!sub.isEmpty()) toActivate.add(sub);
            }
        } else if ("double_challenge".equals(challengeId)) {
            List<Challenge> reg = ChallengeRegistry.getAll().stream()
                    .filter(Challenge::isEnabled)
                    .filter(c -> !"double_challenge".equals(c.getId()))
                    .toList();
            List<Challenge> copy = new ArrayList<>(reg);
            Collections.shuffle(copy);
            if (copy.size() >= 2) {
                toActivate.add(copy.get(0).getId());
                toActivate.add(copy.get(1).getId());
            } else if (!copy.isEmpty()) {
                toActivate.add(copy.get(0).getId());
            }
            historyEntryId = "double_challenge:" + String.join(",", toActivate);
        } else {
            toActivate.add(challengeId);
        }

        state.getActiveChallengeIds().addAll(toActivate);
        state.addHistory(historyEntryId, state.getCurrentDay());

        for (String id : toActivate) {
            Challenge c = ChallengeRegistry.get(id);
            if (c != null) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
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
