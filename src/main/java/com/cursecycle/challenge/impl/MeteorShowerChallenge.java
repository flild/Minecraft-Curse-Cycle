package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.block.ShapeContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.*;

/** One bounded server queue, independent of per-player onTick dispatch and daylight time. */
public final class MeteorShowerChallenge extends Challenge {
    public static final String ID = "meteor_shower";
    public static final int MIN_INTERVAL = 600, MAX_INTERVAL = 900, WARNING = 60;
    public static final int SHOWER_SIZE = 3, MAX_METEORS = 12, SEARCH_ATTEMPTS = 24;
    public static final int MIN_RADIUS = 4, MAX_RADIUS = 12, HEIGHT = 24, LOADED_MARGIN = 16;
    public static final double SPEED = 1.5;
    public static final float POWER = 2.5F;
    private static final Map<MinecraftServer, Storm> STORMS = new HashMap<>();

    public MeteorShowerChallenge() {
        super(ID, "Возможны осадки",
                "С неба летят метеоры",
                "С неба летят метеоры, которые взрываются при падении.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    private static final class Session {
        final ServerPlayerEntity player;
        final ServerWorld world;
        long next;
        Session(ServerPlayerEntity player, long now) {
            this.player = player;
            world = player.getServerWorld();
            next = now + interval(player);
        }
    }
    private static final class Meteor {
        final Session session;
        final UUID owner;
        final ServerWorld world;
        final Vec3d target;
        final long issued;
        Vec3d position;
        Meteor(Session s, Vec3d target, long now) {
            session = s;
            owner = s.player.getUuid(); world = s.world; this.target = target; issued = now;
            position = target.add(0, Math.min(HEIGHT, world.getTopY() - 1 - target.y), 0);
        }
    }
    private static final class Storm {
        final CurseCycleState state;
        final long activation;
        final int start;
        int lastTick;
        final Map<UUID, Session> sessions = new LinkedHashMap<>();
        final List<Meteor> meteors = new ArrayList<>();
        Storm(MinecraftServer server, CurseCycleState state) {
            this.state = state; activation = state.getActivationSerial();
            start = server.getTicks(); lastTick = start - 1;
        }
        long age(MinecraftServer server) { return Integer.toUnsignedLong(server.getTicks() - start); }
    }
    private static int interval(ServerPlayerEntity p) {
        return MIN_INTERVAL + p.getRandom().nextInt(MAX_INTERVAL - MIN_INTERVAL + 1);
    }
    private static boolean active(ServerPlayerEntity p) {
        return !p.isRemoved() && ChallengeActivity.active(p, ID)
                && p.getServer().getPlayerManager().getPlayer(p.getUuid()) == p;
    }

    /** Never request a chunk: check the entire conservative explosion/exposure neighbourhood first. */
    private static boolean available(ServerWorld world, double x, double z) {
        var border = world.getWorldBorder();
        if (!border.contains(x - LOADED_MARGIN, z - LOADED_MARGIN)
                || !border.contains(x + LOADED_MARGIN, z + LOADED_MARGIN)) return false;
        for (int cx = MathHelper.floor(x - LOADED_MARGIN) >> 4; cx <= MathHelper.floor(x + LOADED_MARGIN) >> 4; cx++)
            for (int cz = MathHelper.floor(z - LOADED_MARGIN) >> 4; cz <= MathHelper.floor(z + LOADED_MARGIN) >> 4; cz++)
                if (!world.getChunkManager().isChunkLoaded(cx, cz)) return false;
        return true;
    }
    private static net.minecraft.util.hit.BlockHitResult trace(ServerWorld world, Vec3d from, Vec3d to) {
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.ANY, ShapeContext.absent()));
    }
    private static void mark(Meteor m) {
        // A ring, rather than one random particle, fixes the centre before flight begins.
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            m.world.spawnParticles(ParticleTypes.FLAME, m.target.x + Math.cos(a) * 1.5,
                    m.target.y + .12, m.target.z + Math.sin(a) * 1.5, 1, 0, 0, 0, 0);
        }
    }
    private static void issue(Storm storm, Session s, long now) {
        int added = 0;
        for (int attempt = 0; attempt < SEARCH_ATTEMPTS && added < SHOWER_SIZE
                && storm.meteors.size() < MAX_METEORS; attempt++) {
            double angle = s.player.getRandom().nextDouble() * Math.PI * 2;
            double radius = MIN_RADIUS + s.player.getRandom().nextDouble() * (MAX_RADIUS - MIN_RADIUS);
            double x = s.player.getX() + Math.cos(angle) * radius;
            double z = s.player.getZ() + Math.sin(angle) * radius;
            if (!available(s.world, x, z)) continue;
            int top = s.world.getTopY(Heightmap.Type.WORLD_SURFACE, MathHelper.floor(x), MathHelper.floor(z));
            if (top <= s.world.getBottomY() || top + 2 >= s.world.getTopY()) continue;
            Vec3d from = new Vec3d(x, Math.min(top + HEIGHT, s.world.getTopY() - 1), z);
            var hit = trace(s.world, from, new Vec3d(x, Math.max(s.world.getBottomY(), top - 64), z));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            Vec3d target = hit.getPos();
            if (storm.meteors.stream().anyMatch(m -> m.world == s.world && m.target.squaredDistanceTo(target) < 16)) continue;
            var m = new Meteor(s, target, now);
            storm.meteors.add(m); added++;
            mark(m);
        }
        // Saturation never creates a backlog or a burst of missed showers.
        s.next = now + interval(s.player);
        if (added > 0) {
            s.player.sendMessage(Text.translatable("text.cursecycle.meteor_shower.warning"), false);
            s.world.playSound(null, s.player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                    SoundCategory.HOSTILE, 1F, .6F);
        }
    }
    private static boolean advance(Meteor m, long now) {
        if (!available(m.world, m.target.x, m.target.z)) return true;
        if (now - m.issued < WARNING) {
            if ((now - m.issued) % 10 == 0) mark(m);
            return false;
        }
        if (now - m.issued > WARNING + HEIGHT * 2) return true;
        Vec3d next = new Vec3d(m.position.x, Math.max(m.target.y, m.position.y - SPEED), m.position.z);
        // Voxel raycasts exclude an exact endpoint; include the surface by a numerical epsilon.
        var hit = trace(m.world, m.position, next.add(0, -1.0E-6, 0));
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3d point = hit.getPos();
            // TNT respects resistance, exposure and tntExplosionDropDecay, independent of mobGriefing.
            m.world.createExplosion(null, point.x, point.y, point.z, POWER, false, World.ExplosionSourceType.TNT);
            return true;
        }
        // The marked surface was removed: cancel rather than striking an unannounced lower floor.
        if (next.y <= m.target.y) return true;
        m.position = next;
        m.world.spawnParticles(ParticleTypes.FLAME, next.x, next.y, next.z, 8, .18, .25, .18, .015);
        m.world.spawnParticles(ParticleTypes.LARGE_SMOKE, next.x, next.y + .4, next.z, 3, .1, .3, .1, 0);
        return false;
    }

    public static void tick(MinecraftServer server) {
        var state = CurseCycleState.getServerState(server);
        var storm = STORMS.get(server);
        if (storm != null && (storm.state != state || storm.activation != state.getActivationSerial())) {
            clear(server); storm = null;
        }
        var players = server.getPlayerManager().getPlayerList();
        if (storm == null) {
            if (players.stream().noneMatch(MeteorShowerChallenge::active)) return;
            storm = new Storm(server, state); STORMS.put(server, storm);
        }
        if (storm.lastTick == server.getTicks()) return;
        storm.lastTick = server.getTicks();
        long now = storm.age(server);
        storm.sessions.values().removeIf(s -> !active(s.player) || s.player.getServerWorld() != s.world);
        for (var p : players) if (active(p)) {
            var s = storm.sessions.get(p.getUuid());
            if (s == null || s.player != p) storm.sessions.put(p.getUuid(), new Session(p, now));
        }
        // Damage callbacks may kill/disconnect/teleport an owner and synchronously forget their queue.
        for (var m : List.copyOf(storm.meteors)) {
            if (STORMS.get(server) != storm) return;
            if (!storm.meteors.contains(m)) continue;
            if (storm.sessions.get(m.owner) != m.session || advance(m, now)) storm.meteors.remove(m);
        }
        // At most one bounded search (24 candidates) per server tick, for all dimensions/players.
        for (var s : List.copyOf(storm.sessions.values())) if (now >= s.next) {
            issue(storm, s, now);
            // Rotate due owners so saturation does not always favour the first connected player.
            storm.sessions.remove(s.player.getUuid()); storm.sessions.put(s.player.getUuid(), s);
            break;
        }
        if (storm.sessions.isEmpty()) clear(server);
    }
    public static void forget(ServerPlayerEntity p) {
        var storm = STORMS.get(p.getServer());
        if (storm == null) return;
        var s = storm.sessions.get(p.getUuid());
        if (s == null || s.player != p) return;
        storm.sessions.remove(p.getUuid()); storm.meteors.removeIf(m -> m.owner.equals(p.getUuid()));
        if (storm.sessions.isEmpty()) clear(p.getServer());
    }
    public static void clear(MinecraftServer server) { STORMS.remove(server); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity p) { forget(p); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        forget(oldPlayer);
    }
}
