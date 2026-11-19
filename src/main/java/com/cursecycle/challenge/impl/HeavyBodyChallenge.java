package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.network.HeavyBodyPayload;
import com.cursecycle.network.HeavyBodyStatePayload;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HeavyBodyChallenge extends Challenge {
    public static final String ID = "heavy_body";
    public static final float MULTIPLIER = 0.6F;
    public static final double MOVING_THRESHOLD = 0.02;
    public static final int MAX_STAMINA = 50, RECOVERY_TICKS = 40;
    public static final int REGEN_DELAY_TICKS = 20;
    public static final SoundEvent PANT = SoundEvent.of(Identifier.of("cursecycle", "heavy_body_pant"));
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new HashMap<>();

    // Технические нюансы; не включать в описание для игрока.
    // Скорость наземного движения и начальная вертикальная скорость обычного прыжка составляют 60% от значения
    // с учётом других эффектов. Спринт расходует 50 единиц за 50 тиков; после 20 тиков без расхода отдых возвращает 2 за тик.
    // При истощении спринт блокируется на 40 тиков: 20 тиков паузы, затем восстановление до полного запаса.
    public HeavyBodyChallenge() {
        super(ID, "На массе",
                "Бегаешь медленнее, прыгаешь ниже и запыхиваешься.",
                "Бегаешь медленнее и прыгаешь ниже. Спринт расходует выносливость. Когда она заканчивается, "
                        + "на две секунды сбивается дыхание, темнеет в глазах и нельзя бежать. "
                        + "Выносливость начинает восстанавливаться через секунду после остановки спринта.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    public static boolean active(PlayerEntity player) {
        if (!player.isAlive() || player.isRemoved() || player.isCreative() || player.isSpectator()) return false;
        if (player.getWorld().isClient)
            return ClientDataHolder.isSynced() && ClientDataHolder.getActiveChallengeIds().contains(ID);
        return ChallengeActivity.active(player, ID);
    }

    public static boolean onFoot(PlayerEntity player) {
        return !player.hasVehicle() && !player.getAbilities().flying && !player.isFallFlying()
                && !player.isTouchingWater() && !player.isInLava() && !player.isClimbing();
    }

    public static float scale(PlayerEntity player, float original) {
        return active(player) && onFoot(player) && player.isOnGround() ? original * MULTIPLIER : original;
    }

    /** Tick state only; no player attributes, inventory, or timer data are persisted. */
    public static final class Breathing {
        private int stamina = MAX_STAMINA, recoveryTicks, regenDelay;
        public boolean tick(boolean sprinting) {
            if (recoveryTicks > 0) {
                --recoveryTicks;
                if (regenDelay > 0) --regenDelay;
                stamina = Math.max(0, MAX_STAMINA * (RECOVERY_TICKS - recoveryTicks - REGEN_DELAY_TICKS)
                        / (RECOVERY_TICKS - REGEN_DELAY_TICKS));
                return false;
            }
            if (sprinting) {
                regenDelay = REGEN_DELAY_TICKS;
                if (--stamina == 0) { recoveryTicks = RECOVERY_TICKS; return true; }
            } else if (regenDelay > 0) --regenDelay;
            else stamina = Math.min(MAX_STAMINA, stamina + 2);
            return false;
        }
        public boolean panting() { return recoveryTicks > 0; }
        public int stamina() { return stamina; }
        public int recoveryTicks() { return recoveryTicks; }
    }

    public static boolean cannotSprint(PlayerEntity player) {
        if (!active(player) || !onFoot(player)) return false;
        if (player.getWorld().isClient) return com.cursecycle.client.HeavyBodyInput.exhausted(player);
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return false;
        var sessions = SESSIONS.get(player.getServer());
        var s = sessions == null ? null : sessions.get(player.getUuid());
        return s != null && s.matches(serverPlayer) && s.breathing.panting();
    }

    private static final class Session {
        final ServerPlayerEntity player;
        final Object world;
        final long activation;
        final Breathing breathing = new Breathing();
        Vec3d position;
        int lastTick;
        boolean input;
        Session(ServerPlayerEntity player) {
            this.player = player;
            world = player.getWorld();
            activation = CurseCycleState.getServerState(player.getServer()).getActivationSerial();
            position = player.getPos();
            lastTick = player.getServer().getTicks();
        }
        boolean matches(ServerPlayerEntity current) {
            return current == player && current.getWorld() == world
                    && activation == CurseCycleState.getServerState(current.getServer()).getActivationSerial();
        }
    }

    private static Session session(ServerPlayerEntity player) {
        var sessions = SESSIONS.computeIfAbsent(player.getServer(), ignored -> new HashMap<>());
        var current = sessions.get(player.getUuid());
        if (current == null || !current.matches(player)) {
            if (current != null) release(current);
            current = new Session(player);
            sessions.put(player.getUuid(), current);
        }
        return current;
    }

    public static void receive(ServerPlayerEntity player, HeavyBodyPayload payload) {
        if (!active(player) || payload.activation() != CurseCycleState.getServerState(player.getServer()).getActivationSerial()) return;
        session(player).input = payload.moving() && onFoot(player)
                && player.currentScreenHandler == player.playerScreenHandler;
    }

    private static void send(Session s) {
        ServerPlayNetworking.send(s.player, new HeavyBodyStatePayload(s.activation,
                s.breathing.stamina(), s.breathing.recoveryTicks()));
    }
    /** Normal challenge sync must preserve the current stamina and cooldown. */
    public static void sync(ServerPlayerEntity player) {
        if (!active(player)) return;
        var sessions = SESSIONS.get(player.getServer());
        var s = sessions == null ? null : sessions.get(player.getUuid());
        if (s != null && s.matches(player)) send(s);
        else ServerPlayNetworking.send(player, new HeavyBodyStatePayload(
                CurseCycleState.getServerState(player.getServer()).getActivationSerial(), MAX_STAMINA, 0));
    }

    /** Runs once per server tick, including when disabled, to release stale sessions. */
    public static void tick(MinecraftServer server) {
        var sessions = SESSIONS.get(server);
        if (sessions != null) {
            sessions.values().removeIf(s -> {
                var current = server.getPlayerManager().getPlayer(s.player.getUuid());
                if (current == null || !s.matches(current) || !active(current)) {
                    release(s);
                    return true;
                }
                return false;
            });
        }
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (!active(player)) continue;
            var s = session(player);
            int now = server.getTicks();
            if (s.lastTick == now) continue;
            int elapsed = now - s.lastTick;
            s.lastTick = now;
            Vec3d delta = player.getPos().subtract(s.position);
            s.position = player.getPos();
            boolean moving = elapsed == 1 && s.input && onFoot(player)
                    && player.currentScreenHandler == player.playerScreenHandler
                    && delta.horizontalLengthSquared() > MOVING_THRESHOLD * MOVING_THRESHOLD;
            boolean wasPanting = s.breathing.panting();
            int oldStamina = s.breathing.stamina();
            if (s.breathing.tick(moving && player.isSprinting()))
                player.playSoundToPlayer(PANT, SoundCategory.PLAYERS, 0.35F, 1.0F);
            if (s.breathing.panting()) player.setSprinting(false);
            if (wasPanting && !s.breathing.panting()) stopSound(player);
            if (wasPanting != s.breathing.panting() || oldStamina != s.breathing.stamina()
                    && (now % 5 == 0 || s.breathing.stamina() == MAX_STAMINA)) send(s);
        }
        if (sessions != null && sessions.isEmpty()) SESSIONS.remove(server);
    }

    private static void stopSound(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new StopSoundS2CPacket(PANT.getId(), SoundCategory.PLAYERS));
    }
    private static void release(Session s) {
        stopSound(s.player);
        ServerPlayNetworking.send(s.player, new HeavyBodyStatePayload(s.activation, MAX_STAMINA, 0));
    }
    public static void forget(ServerPlayerEntity player) {
        var sessions = SESSIONS.get(player.getServer());
        if (sessions == null) return;
        var old = sessions.remove(player.getUuid());
        if (old != null) release(old);
        if (sessions.isEmpty()) SESSIONS.remove(player.getServer());
    }
    public static void clear(MinecraftServer server) { SESSIONS.remove(server); }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { forget(player); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) { forget(player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) { forget(oldPlayer); }
}
