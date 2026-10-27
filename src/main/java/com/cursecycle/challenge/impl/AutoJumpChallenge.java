package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.network.AutoJumpPayload;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AutoJumpChallenge extends Challenge {
    public static final String ID = "auto_jump";
    public static final int INTERVAL = 10;
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new HashMap<>();

    public AutoJumpChallenge() {
        super(ID, "Попрыгунчик", "Персонаж сам прыгает при каждом приземлении.",
                "Персонаж автоматически выполняет обычный прыжок, когда стоит на земле и может прыгнуть. Автоматические попытки разделены минимум 10 тиками. Приседание не отключает прыжки; в воде, лаве, на лестнице, в транспорте и при полёте автоматические прыжки не выполняются. Высота, голод и урон от падения остаются обычными с учётом других эффектов.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }

    public static boolean active(ServerPlayerEntity player) {
        return !player.isRemoved() && ChallengeActivity.active(player, ID);
    }

    /** Ephemeral server ticks, deliberately reset on a new object/login or activation. */
    public static final class Clock {
        private Integer attempt, success;
        public boolean ready(int now) { return attempt == null || now - attempt >= INTERVAL; }
        public void attempted(int now) { attempt = now; }
        public void jumped(int now) { attempt = success = now; }
        public boolean jumpedThisTick(int now) { return success != null && success == now; }
    }

    private record Session(ServerPlayerEntity player, long activation, Clock clock) {
        boolean matches(ServerPlayerEntity current) {
            return player == current && activation == CurseCycleState.getServerState(current.getServer()).getActivationSerial();
        }
    }

    private static Session session(ServerPlayerEntity player) {
        var sessions = SESSIONS.computeIfAbsent(player.getServer(), ignored -> new HashMap<>());
        var s = sessions.get(player.getUuid());
        if (s == null || !s.matches(player)) {
            s = new Session(player, CurseCycleState.getServerState(player.getServer()).getActivationSerial(), new Clock());
            sessions.put(player.getUuid(), s);
        }
        return s;
    }

    public static boolean duplicateJump(ServerPlayerEntity player) {
        return active(player) && session(player).clock.jumpedThisTick(player.getServer().getTicks());
    }

    /** Called only after LivingEntity.jump actually installed its ordinary impulse. */
    public static void jumped(ServerPlayerEntity player) {
        if (active(player)) session(player).clock.jumped(player.getServer().getTicks());
    }

    public static void tick(MinecraftServer server) {
        var sessions = SESSIONS.get(server);
        if (sessions != null) {
            sessions.values().removeIf(s -> !s.matches(server.getPlayerManager().getPlayer(s.player.getUuid()))
                    || !active(s.player));
            if (sessions.isEmpty()) SESSIONS.remove(server);
        }
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (!active(player)) continue;
            var clock = session(player).clock;
            int now = server.getTicks();
            if (!clock.ready(now) || !player.isOnGround() || !HeavyBodyChallenge.onFoot(player)) continue;
            clock.attempted(now);
            var before = player.getVelocity();
            player.jump();
            if (clock.jumpedThisTick(now)) {
                // Server movement packets must not interpret this takeoff as a second manual jump.
                player.setOnGround(false);
                var after = player.getVelocity();
                ServerPlayNetworking.send(player, new AutoJumpPayload(player.getId(),
                        new net.minecraft.util.math.Vec3d(after.x - before.x, after.y, after.z - before.z)));
            }
        }
    }

    public static void forget(ServerPlayerEntity player) {
        var sessions = SESSIONS.get(player.getServer());
        if (sessions == null) return;
        sessions.remove(player.getUuid());
        if (sessions.isEmpty()) SESSIONS.remove(player.getServer());
    }
    public static void clear(MinecraftServer server) { SESSIONS.remove(server); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) { forget(player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) { forget(oldPlayer); }
}
