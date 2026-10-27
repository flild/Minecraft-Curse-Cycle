package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.network.MovementMemoryPayload;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MovementMemoryChallenge extends Challenge {
    public static final String ID = "movement_memory";
    public static final int MEMORY_TICKS = 20;
    // Session input is deliberately absent from NBT. SERVER_STOPPED and disconnect release references.
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new HashMap<>();

    private static final class Session {
        final ServerPlayerEntity player;
        final Object world;
        final GameMode mode;
        final long activation;
        long sequence = -1;
        int startedAt;
        boolean holding;
        Session(ServerPlayerEntity player, long activation) {
            this.player = player;
            world = player.getWorld();
            mode = player.interactionManager.getGameMode();
            this.activation = activation;
        }
        boolean matches(ServerPlayerEntity current, long serial) {
            return player == current && world == current.getWorld() && activation == serial
                    && mode == current.interactionManager.getGameMode();
        }
    }

    public MovementMemoryChallenge() {
        super(ID, "Ноги помнят", "После отпускания движения ноги продолжают идти ещё секунду.",
                "После перехода горизонтального ввода движения в ноль персонаж ещё 20 тиков продолжает последнее направление как будто клавиша всё ещё нажата. Новый ненулевой ввод сразу заменяет запомненный. Это действует на собственное наземное движение; вода, полёт, транспорт и открытые меню используют обычное управление.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    public static boolean eligible(ServerPlayerEntity player) {
        return ChallengeActivity.active(player, ID) && !player.isTouchingWater() && !player.isInLava()
                && !player.isClimbing() && !player.isFallFlying() && !player.getAbilities().flying
                && !player.hasVehicle() && player.currentScreenHandler == player.playerScreenHandler;
    }

    /** Only input transitions produce packets; repeated requests cannot extend the same interval. */
    public static void receive(ServerPlayerEntity player, MovementMemoryPayload request) {
        var server = player.getServer();
        long serial = CurseCycleState.getServerState(server).getActivationSerial();
        if (request.activation() != serial || request.sequence() < 0) return;
        var sessions = SESSIONS.computeIfAbsent(server, ignored -> new HashMap<>());
        var session = sessions.get(player.getUuid());
        if (session == null || !session.matches(player, serial)) {
            session = new Session(player, serial);
            sessions.put(player.getUuid(), session);
        }
        if (request.sequence() <= session.sequence) return;
        session.sequence = request.sequence();
        session.holding = request.holding() && eligible(player);
        session.startedAt = server.getTicks();
        send(session);
    }

    public static void tick(MinecraftServer server) {
        var sessions = SESSIONS.get(server);
        if (sessions == null) return;
        long serial = CurseCycleState.getServerState(server).getActivationSerial();
        sessions.values().removeIf(session -> {
            var player = server.getPlayerManager().getPlayer(session.player.getUuid());
            if (player == null || !session.matches(player, serial) || !eligible(player)) {
                if (session.holding && player == session.player) { session.holding = false; send(session); }
                return true;
            }
            if (session.holding && server.getTicks() - session.startedAt >= MEMORY_TICKS) {
                session.holding = false;
                send(session);
            }
            return false;
        });
        if (sessions.isEmpty()) SESSIONS.remove(server);
    }

    private static void send(Session session) {
        ServerPlayNetworking.send(session.player,
                new MovementMemoryPayload(session.activation, session.sequence, session.holding));
    }

    public static void forget(ServerPlayerEntity player) {
        var sessions = SESSIONS.get(player.getServer());
        if (sessions == null) return;
        var removed = sessions.remove(player.getUuid());
        if (removed != null && removed.holding) { removed.holding = false; send(removed); }
        if (sessions.isEmpty()) SESSIONS.remove(player.getServer());
    }

    public static void clear(MinecraftServer server) { SESSIONS.remove(server); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) { forget(player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) { forget(oldPlayer); }
}
