package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.command.CurseCycleCommand;
import com.cursecycle.config.CurseCycleConfig;
import com.cursecycle.diagnostics.StartupDiagnostics;
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
        StartupDiagnostics.initialize("common", this::initializeCommon);
    }

    private void initializeCommon(StartupDiagnostics diagnostics) {

        // Load config & register challenges
        diagnostics.stage("loading config");
        config = CurseCycleConfig.load();
        diagnostics.stage("registering challenges");
        ModChallenges.registerAll();
        config.applyChallengeToggles();
        diagnostics.stage("registering gameplay events");
        ChallengeEventManager.registerEvents();
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            com.cursecycle.challenge.impl.BuddingChallenge.died(entity);
            com.cursecycle.challenge.impl.BloodTributeChallenge.died(entity, source);
        });

        // Register payloads & commands
        diagnostics.stage("registering network payloads");
        NetworkHandler.registerPayloads();
        diagnostics.stage("registering lifecycle events and commands");
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                com.cursecycle.challenge.impl.MeteorShowerChallenge::clear);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.cursecycle.challenge.impl.MeteorShowerChallenge.forget(handler.getPlayer()));
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player)
                com.cursecycle.challenge.impl.MeteorShowerChallenge.forget(player);
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> com.cursecycle.challenge.impl.MeteorShowerChallenge.forget(player));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof net.minecraft.entity.LivingEntity living)
                com.cursecycle.challenge.impl.LowGravityChallenge.reconcile(living);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register(
                com.cursecycle.challenge.impl.SkeletonHunterChallenge::loaded);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register(
                com.cursecycle.challenge.impl.SkeletonHunterChallenge::unloaded);
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            com.cursecycle.challenge.impl.SkeletonHunterChallenge.died(entity);
            if (entity instanceof ServerPlayerEntity player)
                com.cursecycle.challenge.impl.SkeletonHunterChallenge.forget(player);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                com.cursecycle.challenge.impl.SkeletonHunterChallenge::clear);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.cursecycle.challenge.impl.SkeletonHunterChallenge.forget(handler.getPlayer()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                com.cursecycle.challenge.impl.HeartbeatChallenge::clear);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.cursecycle.challenge.impl.HeartbeatChallenge.forget(handler.getPlayer()));
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player)
                com.cursecycle.challenge.impl.HeartbeatChallenge.forget(player);
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> {
                    com.cursecycle.challenge.impl.HeartbeatChallenge.forget(player);
                    com.cursecycle.challenge.impl.HeartbeatChallenge.sync(player);
                });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                com.cursecycle.challenge.impl.AutoJumpChallenge::clear);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.cursecycle.challenge.impl.AutoJumpChallenge.forget(handler.getPlayer()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                com.cursecycle.challenge.impl.HeavyBodyChallenge::clear);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.cursecycle.challenge.impl.HeavyBodyChallenge.forget(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(com.cursecycle.challenge.impl.MovementMemoryChallenge::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                com.cursecycle.challenge.impl.MovementMemoryChallenge::clear);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.cursecycle.challenge.impl.MovementMemoryChallenge.forget(handler.getPlayer()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                com.cursecycle.challenge.impl.SimonSaysChallenge::clear);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.cursecycle.challenge.impl.SimonSaysChallenge.forget(handler.getPlayer()));
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player)
                com.cursecycle.challenge.impl.SimonSaysChallenge.forget(player);
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> {
                    com.cursecycle.challenge.impl.SimonSaysChallenge.forget(player);
                    com.cursecycle.challenge.impl.SimonSaysChallenge.sync(player);
                });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CurseCycleCommand.register(dispatcher));

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                com.cursecycle.challenge.impl.BloodTributeChallenge::clear);

        // Player Join Event -> sync state & re-apply active challenges
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            com.cursecycle.challenge.effect.EffectLedger.of(player).reconcile();
            CurseCycleState state = CurseCycleState.getServerState(server);

            long timeOfDay = Math.max(0, server.getOverworld().getTimeOfDay());
            long ticksUntilNext = CycleManager.ticksUntilNext(state, config, timeOfDay);

            NetworkHandler.syncToPlayer(
                player,
                state.getCurrentDay(),
                ticksUntilNext,
                state.getActiveChallengeIds(),
                state.getNextChallengeId(),
                state.getHistory().stream().map(h -> h.challengeId).toList()
            );

            ChallengeEventManager.dispatchToActive(server, player, c -> c.onPlayerJoin(server, player));
        });

        // Player Disconnect Event -> cleanup active challenge effects
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            ChallengeEventManager.dispatchToActive(server, player, c -> c.onPlayerLeave(server, player));
        });

        // A live End return may replace the player object without ending its effects/timers.
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            var server = newPlayer.getServer();
            if (alive) {
                var ledgerNbt = new net.minecraft.nbt.NbtCompound();
                com.cursecycle.challenge.effect.EffectLedger.of(oldPlayer).write(ledgerNbt);
                com.cursecycle.challenge.effect.EffectLedger.of(newPlayer).read(ledgerNbt);
                com.cursecycle.challenge.effect.EffectLedger.of(newPlayer).reconcile();
            }
            var state = CurseCycleState.getServerState(server);
            var ids = new java.util.LinkedHashSet<>(state.getActiveChallengeIds());
            ids.addAll(state.findPersonalActiveChallengeIds(newPlayer.getUuid()));
            for (String id : ids) {
                var challenge = ChallengeRegistry.get(id);
                if (challenge != null && challenge.isEnabled())
                    challenge.onPlayerRespawn(server, oldPlayer, newPlayer, alive);
            }
        });

        // Server Tick Event -> delegating to CycleManager
        ServerTickEvents.END_SERVER_TICK.register(server -> CycleManager.onServerTick(server, config));
        ServerTickEvents.END_SERVER_TICK.register(com.cursecycle.challenge.impl.BloodTributeChallenge::tick);
        ServerTickEvents.END_SERVER_TICK.register(com.cursecycle.challenge.impl.MeteorShowerChallenge::tick);
        ServerTickEvents.END_SERVER_TICK.register(com.cursecycle.challenge.impl.SkeletonHunterChallenge::tick);
        ServerTickEvents.END_SERVER_TICK.register(com.cursecycle.challenge.impl.AutoJumpChallenge::tick);
        ServerTickEvents.END_SERVER_TICK.register(com.cursecycle.challenge.impl.HeavyBodyChallenge::tick);
        ServerTickEvents.END_SERVER_TICK.register(com.cursecycle.challenge.impl.HeartbeatChallenge::tick);
        ServerTickEvents.END_SERVER_TICK.register(com.cursecycle.challenge.impl.SimonSaysChallenge::tick);
        ServerTickEvents.END_SERVER_TICK.register(com.cursecycle.challenge.impl.EndlessRaidChallenge::tick);
    }
}
