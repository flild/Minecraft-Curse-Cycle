package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.network.HeavyBodyPayload;
import com.cursecycle.state.CurseCycleState;
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
    public static final SoundEvent PANT = SoundEvent.of(Identifier.of("cursecycle", "heavy_body_pant"));
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new HashMap<>();

    public HeavyBodyChallenge() {
        super(ID, "На массе", "Бегаешь медленнее, прыгаешь ниже и запыхиваешься.",
                "Cкорость наземного передвижения уменьшается до 60%, а начальная вертикальная скорость обычного прыжка — до 60% от значения с учётом остальных эффектов. После 5 секунд непрерывного движения пешком появляется слышимая отдышка; 2 секунды покоя её прекращают. Размер персонажа, голод и здоровье не меняются.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
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
        private int movingTicks, restingTicks, sinceSound;
        private boolean panting;
        public boolean tick(boolean moving) {
            if (moving) {
                restingTicks = 0;
                if (!panting && ++movingTicks >= 100) {
                    panting = true;
                    sinceSound = 0;
                    return true;
                }
            } else {
                movingTicks = 0;
                if (panting && ++restingTicks >= 40) { panting = false; return false; }
            }
            if (panting && ++sinceSound >= 80) { sinceSound = 0; return true; }
            return false;
        }
        public boolean panting() { return panting; }
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
            if (current != null) stopSound(current.player);
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

    /** Runs once per server tick, including when disabled, to release stale sessions. */
    public static void tick(MinecraftServer server) {
        var sessions = SESSIONS.get(server);
        if (sessions != null) {
            sessions.values().removeIf(s -> {
                var current = server.getPlayerManager().getPlayer(s.player.getUuid());
                if (current == null || !s.matches(current) || !active(current)) {
                    stopSound(s.player);
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
            boolean moving = elapsed == 1 && s.input && onFoot(player) && player.isOnGround()
                    && player.currentScreenHandler == player.playerScreenHandler
                    && delta.horizontalLengthSquared() > MOVING_THRESHOLD * MOVING_THRESHOLD;
            boolean wasPanting = s.breathing.panting();
            if (s.breathing.tick(moving)) player.playSoundToPlayer(PANT, SoundCategory.PLAYERS, 0.35F, 1.0F);
            if (wasPanting && !s.breathing.panting()) stopSound(player);
        }
        if (sessions != null && sessions.isEmpty()) SESSIONS.remove(server);
    }

    private static void stopSound(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new StopSoundS2CPacket(PANT.getId(), SoundCategory.PLAYERS));
    }
    public static void forget(ServerPlayerEntity player) {
        var sessions = SESSIONS.get(player.getServer());
        if (sessions == null) return;
        var old = sessions.remove(player.getUuid());
        if (old != null) stopSound(old.player);
        if (sessions.isEmpty()) SESSIONS.remove(player.getServer());
    }
    public static void clear(MinecraftServer server) { SESSIONS.remove(server); }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { forget(player); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) { forget(player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) { forget(oldPlayer); }
}
