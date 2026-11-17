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
import java.util.Arrays;

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
        final long[] sequence = new long[MovementMemoryPayload.DIRECTION_COUNT];
        final int[] startedAt = new int[MovementMemoryPayload.DIRECTION_COUNT];
        final boolean[] holding = new boolean[MovementMemoryPayload.DIRECTION_COUNT];
        Session(ServerPlayerEntity player, long activation) {
            this.player = player;
            world = player.getWorld();
            mode = player.interactionManager.getGameMode();
            this.activation = activation;
            Arrays.fill(sequence, -1);
        }
        boolean matches(ServerPlayerEntity current, long serial) {
            return player == current && world == current.getWorld() && activation == serial
                    && mode == current.interactionManager.getGameMode();
        }
    }

    // Технические нюансы; не включать в описание для игрока.
    // Каждая отпущенная клавиша движения удерживается независимо 20 тиков. Другие клавиши не отменяют
    // её интервал; повторное нажатие обновляет только её память. Вода, полёт, транспорт и открытые
    // меню используют штатное управление.
    public MovementMemoryChallenge() {
        super(ID, "Ноги помнят",
                "Ты двигаешься по инерции, ещё какое-то время.",
                "Каждая клавиша движения продолжает действовать секунду после отпускания, даже при смене направления.",
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
        int direction = request.direction();
        if (request.activation() != serial || request.sequence() < 0
                || direction < 0 || direction >= MovementMemoryPayload.DIRECTION_COUNT) return;
        var sessions = SESSIONS.computeIfAbsent(server, ignored -> new HashMap<>());
        var session = sessions.get(player.getUuid());
        if (session == null || !session.matches(player, serial)) {
            session = new Session(player, serial);
            sessions.put(player.getUuid(), session);
        }
        if (request.sequence() <= session.sequence[direction]) return;
        session.sequence[direction] = request.sequence();
        boolean holding = request.holding() && eligible(player);
        // A newer sequence identifies the reply, not a new input transition. Only an actual
        // state change starts/cancels an interval; repeated holds cannot slide its deadline.
        if (session.holding[direction] != holding) {
            session.holding[direction] = holding;
            if (holding) session.startedAt[direction] = server.getTicks();
            send(session, direction);
        } else if (!holding && request.holding()) {
            // Reject a client's predicted hold when server eligibility disagrees.
            send(session, direction);
        }
    }

    public static void tick(MinecraftServer server) {
        var sessions = SESSIONS.get(server);
        if (sessions == null) return;
        long serial = CurseCycleState.getServerState(server).getActivationSerial();
        sessions.values().removeIf(session -> {
            var player = server.getPlayerManager().getPlayer(session.player.getUuid());
            if (player == null || !session.matches(player, serial) || !eligible(player)) {
                if (player == session.player) release(session);
                return true;
            }
            for (int direction = 0; direction < session.holding.length; direction++) {
                if (session.holding[direction] && server.getTicks() - session.startedAt[direction] >= MEMORY_TICKS) {
                    session.holding[direction] = false;
                    send(session, direction);
                }
            }
            return false;
        });
        if (sessions.isEmpty()) SESSIONS.remove(server);
    }

    private static void send(Session session, int direction) {
        ServerPlayNetworking.send(session.player,
                new MovementMemoryPayload(session.activation, session.sequence[direction], direction, session.holding[direction]));
    }

    private static void release(Session session) {
        for (int direction = 0; direction < session.holding.length; direction++) {
            if (session.holding[direction]) {
                session.holding[direction] = false;
                send(session, direction);
            }
        }
    }

    public static void forget(ServerPlayerEntity player) {
        var sessions = SESSIONS.get(player.getServer());
        if (sessions == null) return;
        var removed = sessions.remove(player.getUuid());
        if (removed != null) release(removed);
        if (sessions.isEmpty()) SESSIONS.remove(player.getServer());
    }

    public static void clear(MinecraftServer server) { SESSIONS.remove(server); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) { forget(player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) { forget(oldPlayer); }
}
