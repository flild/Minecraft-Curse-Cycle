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

        // Register payloads & commands
        NetworkHandler.registerPayloads();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CurseCycleCommand.register(dispatcher));

        // Player Join Event -> sync state
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            CurseCycleState state = CurseCycleState.getServerState(server);

            long intervalTicks = config.challengeIntervalDays * 24000L;
            long ticksInCycle = state.getTotalInGameTicks() % intervalTicks;
            long ticksUntilNext = intervalTicks - ticksInCycle;

            NetworkHandler.syncToPlayer(
                player,
                state.getCurrentDay(),
                ticksUntilNext,
                state.getActiveChallengeIds(),
                state.getNextChallengeId(),
                state.getHistory().stream().map(h -> h.challengeId).toList()
            );
        });

        // Server Tick Event -> cycle logic
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        if (server.getOverworld() == null) return;

        CurseCycleState state = CurseCycleState.getServerState(server);

        // Update day and tick counters from world time
        long timeOfDay = server.getOverworld().getTimeOfDay();
        long calculatedDay = (timeOfDay / 24000L) + 1;
        state.setCurrentDay(calculatedDay);

        long currentTotalTicks = state.getTotalInGameTicks() + 1;
        state.setTotalInGameTicks(currentTotalTicks);

        long intervalTicks = config.challengeIntervalDays * 24000L;
        long leadTicks = config.noticeLeadTimeDays * 24000L;
        long spinTriggerTick = intervalTicks - leadTicks;

        long cyclePosition = currentTotalTicks % intervalTicks;

        // Check if 1 day before activation -> trigger spin if not already done
        if (cyclePosition >= spinTriggerTick && !state.isSpinTriggeredForCurrentCycle()) {
            RouletteSelector.WheelResult result = RouletteSelector.selectNextChallenge(config, state);
            if (result != null) {
                state.setNextChallengeId(result.winner.getId());
                state.setSpinTriggeredForCurrentCycle(true);

                List<String> candidateIds = result.wheelItems.stream().map(Challenge::getId).toList();
                NetworkHandler.triggerWheelForAll(server, candidateIds, result.winner.getId());

                // Broadcast announcement
                server.getPlayerManager().broadcast(
                    Text.literal("🎲 [Curse-Cycle] До запуска следующего испытания остался 1 день! Вращаем колесо...").formatted(Formatting.GOLD),
                    false
                );
            }
        }

        // Check cycle boundary (every 5 days) -> activate pre-selected challenge
        if (cyclePosition == 0 && currentTotalTicks > 0) {
            String nextId = state.getNextChallengeId();
            if (nextId != null && !nextId.isEmpty()) {
                activateChallenge(server, state, nextId);
                state.setNextChallengeId(null);
            }
            state.setSpinTriggeredForCurrentCycle(false);
        }

        // Tick active challenges
        for (String id : state.getActiveChallengeIds()) {
            Challenge challenge = ChallengeRegistry.get(id);
            if (challenge != null && challenge.isEnabled()) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    challenge.onTick(server, player);
                }
            }
        }

        // Periodically sync (every 20 ticks = 1 second)
        if (currentTotalTicks % 20 == 0) {
            long ticksUntilNext = intervalTicks - cyclePosition;
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

    private void activateChallenge(MinecraftServer server, CurseCycleState state, String challengeId) {
        Challenge challenge = ChallengeRegistry.get(challengeId);
        if (challenge == null) return;

        // Manage max active challenges (default = 1, replacing previous)
        while (state.getActiveChallengeIds().size() >= config.maxActiveChallenges && !state.getActiveChallengeIds().isEmpty()) {
            String oldId = state.getActiveChallengeIds().remove(0);
            Challenge oldC = ChallengeRegistry.get(oldId);
            if (oldC != null) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    oldC.onStop(server, p);
                }
            }
        }

        state.getActiveChallengeIds().add(challengeId);
        state.addHistory(challengeId, state.getCurrentDay());

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            challenge.onStart(server, p);
        }

        // Broadcast activation message
        Formatting color = challenge.getDifficulty().getColor();
        server.getPlayerManager().broadcast(
            Text.literal("🔥 [Curse-Cycle] Новое испытание активировано: " + challenge.getName() + "!").formatted(color, Formatting.BOLD),
            false
        );
    }
}
