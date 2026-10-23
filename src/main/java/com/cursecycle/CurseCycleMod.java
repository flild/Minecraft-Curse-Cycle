package com.cursecycle;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.command.CurseCycleCommand;
import com.cursecycle.config.CurseCycleConfig;
import com.cursecycle.event.ChallengeEventManager;
import com.cursecycle.network.NetworkHandler;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        config.applyChallengeToggles();
        ChallengeEventManager.registerEvents();

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

        // Server Tick Event -> delegating to CycleManager
        ServerTickEvents.END_SERVER_TICK.register(server -> CycleManager.onServerTick(server, config));
    }
}
