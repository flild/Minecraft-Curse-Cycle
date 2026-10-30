package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.network.HeartbeatStatePayload;
import com.cursecycle.network.HeartbeatPressPayload;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HeartbeatChallenge extends Challenge {
    public static final String ID = "heartbeat";
    public static final int INTERVAL = 20, WINDOW = 5, GRACE = 100;
    public static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of("cursecycle", ID));
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new HashMap<>();
    private static long nextSession;

    public HeartbeatChallenge() {
        super(ID, "Сердце на ручном приводе", "Нажимай B в ритм: пропуск удара наносит урон.",
                "Сердце работает на ручном приводе: нажимай назначенную клавишу удара B раз в секунду в отмеченный момент. После 5 секунд подготовки каждый пропущенный удар наносит 2 единицы урона. Допуск — 5 серверных тиков в каждую сторону; ранние и лишние нажатия просто не засчитываются.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    /** Server tick clock, independent of daylight/time commands. No catch-up damage queue. */
    public static final class Rhythm {
        private long target = GRACE + INTERVAL;
        private boolean accepted;
        public long target() { return target; }
        public boolean accepted() { return accepted; }
        public boolean press(long age) {
            if (accepted || age < target - WINDOW || age > target + WINDOW) return false;
            accepted = true;
            return true;
        }
        public boolean advance(long age) {
            if (age <= target + WINDOW) return false;
            boolean missed = !accepted;
            // Only one request, even if a caller skipped many windows. Never replay old misses.
            target += ((age - target - WINDOW - 1) / INTERVAL + 1) * INTERVAL;
            accepted = false;
            return missed;
        }
    }

    private static final class Session {
        final ServerPlayerEntity player;
        final Object world;
        final long activation, token = ++nextSession;
        final int start;
        final Rhythm rhythm = new Rhythm();
        int lastTick;
        Session(ServerPlayerEntity player) {
            this.player = player;
            world = player.getWorld();
            activation = serial(player);
            start = lastTick = player.getServer().getTicks();
        }
        boolean matches(ServerPlayerEntity current) {
            return current == player && current.networkHandler.player == current
                    && current.getWorld() == world && serial(current) == activation;
        }
        long age() { return Integer.toUnsignedLong(player.getServer().getTicks() - start); }
    }

    private static long serial(ServerPlayerEntity player) {
        return CurseCycleState.getServerState(player.getServer()).getActivationSerial();
    }
    private static boolean active(ServerPlayerEntity player) {
        return !player.isRemoved() && ChallengeActivity.active(player, ID)
                && player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player;
    }
    private static Session session(ServerPlayerEntity player) {
        var sessions = SESSIONS.computeIfAbsent(player.getServer(), ignored -> new HashMap<>());
        var s = sessions.get(player.getUuid());
        if (s == null || !s.matches(player)) {
            s = new Session(player);
            sessions.put(player.getUuid(), s);
        }
        return s;
    }
    private static void send(Session s) {
        ServerPlayNetworking.send(s.player, new HeartbeatStatePayload(s.activation, s.token,
                s.age(), s.rhythm.target(), s.rhythm.accepted()));
    }
    /** May also be called by normal state sync; never resets an existing phase. */
    public static void sync(ServerPlayerEntity player) {
        if (active(player)) send(session(player));
        else forget(player);
    }
    /** Fabric dispatches play receivers on the server thread. No client timestamp or success flag. */
    public static void receive(ServerPlayerEntity player, HeartbeatPressPayload payload) {
        if (!active(player)) return;
        var sessions = SESSIONS.get(player.getServer());
        var s = sessions == null ? null : sessions.get(player.getUuid());
        if (s == null || !s.matches(player) || s.activation != payload.activation() || s.token != payload.session()) return;
        if (s.rhythm.press(s.age())) send(s);
    }
    /** Always registered, so disabled/removed IDs also release sessions and client feedback. */
    public static void tick(MinecraftServer server) {
        var sessions = SESSIONS.get(server);
        if (sessions != null) {
            for (var s : java.util.List.copyOf(sessions.values())) {
                if (!active(s.player) || !s.matches(s.player)) forget(s.player);
            }
        }
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (!active(player)) continue;
            var s = session(player);
            int now = server.getTicks();
            if (s.lastTick == now) continue;
            s.lastTick = now;
            long previousTarget = s.rhythm.target();
            if (s.rhythm.advance(s.age())) {
                var source = new DamageSource(player.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE_TYPE));
                player.damage(source, 2.0F);
            }
            // Applied damage may kill or teleport this player through another challenge.
            if (!active(player) || !s.matches(player)) { forget(player); continue; }
            if (previousTarget != s.rhythm.target() || s.age() % 5 == 0) send(s);
        }
    }
    public static void forget(ServerPlayerEntity player) {
        var sessions = SESSIONS.get(player.getServer());
        if (sessions == null) return;
        var old = sessions.get(player.getUuid());
        if (old == null || old.player != player) return;
        sessions.remove(player.getUuid());
        if (sessions.isEmpty()) SESSIONS.remove(player.getServer());
        ServerPlayNetworking.send(player, new HeartbeatStatePayload(old.activation, 0, 0, 0, false));
    }
    public static void clear(MinecraftServer server) { SESSIONS.remove(server); }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { sync(player); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) { forget(player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        forget(oldPlayer);
        sync(newPlayer);
    }
}
