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
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.*;
import net.minecraft.entity.LivingEntity;
import com.cursecycle.network.MeteorShowerStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.*;

/** One bounded server queue, independent of per-player onTick dispatch and daylight time. */
public final class MeteorShowerChallenge extends Challenge {
    public static final String ID = "meteor_shower";
    public static final int MIN_INTERVAL = 240, MAX_INTERVAL = 360, WARNING = 60, FIRST_WAVE = 80;
    public static final int MIN_SHOWER = 5, MAX_SHOWER = 10, MAX_METEORS = 40, SEARCH_ATTEMPTS = 64;
    public static final int MAX_RADIUS = 12, HEIGHT = 24, LOADED_MARGIN = 16, ERUPTION_TICKS = 30;
    public static final double COLUMN_RADIUS = 1.8, COLUMN_HEIGHT = 8, MIN_SPACING = 3.25;
    public static final double SPEED = 1.5;
    public static final float POWER = 2.5F;
    private static final Map<MinecraftServer, Storm> STORMS = new HashMap<>();

    public MeteorShowerChallenge() {
        super(ID, "Метеослужба предупреждала",
                "Метеоры и огненные гейзеры",
                "Волны из 5–10 метеоров и гейзеров. Под землёй огонь прорывается из пола; отмеченные зоны взрываются через три секунды.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    private static final class Session {
        final ServerPlayerEntity player;
        final ServerWorld world;
        long next;
        Session(ServerPlayerEntity player, long now) {
            this.player = player;
            world = player.getServerWorld();
            next = now + FIRST_WAVE;
        }
    }
    private static final class Meteor {
        final Session session;
        final UUID owner;
        final ServerWorld world;
        final Vec3d target;
        final long issued;
        int id;
        boolean geyser;
        double height;
        long impactedAt = -1;
        Vec3d position;
        Meteor(Session s, Vec3d target, long now) {
            session = s;
            owner = s.player.getUuid(); world = s.world; this.target = target; issued = now;
            height = ceiling(world, target);
            geyser = !world.isSkyVisible(BlockPos.ofFloored(target)) || height < HEIGHT - .1;
            position = geyser ? target : target.add(0, HEIGHT, 0);
        }
    }
    private static final class Storm {
        final CurseCycleState state;
        final long activation;
        final int start;
        int lastTick, nextId;
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
            m.world.spawnParticles(ParticleTypes.FLAME, m.target.x + Math.cos(a) * COLUMN_RADIUS,
                    m.target.y + .12, m.target.z + Math.sin(a) * COLUMN_RADIUS, 1, 0, 0, 0, 0);
        }
    }
    /** Local floor in the player's reachable cavity: never substitute the roof of their cave. */
    private static Vec3d floor(Session s, double x, double z) {
        var from = s.player.getPos().add(0, .75, 0);
        var at = new Vec3d(x, from.y, z);
        if (trace(s.world, from, at).getType() != HitResult.Type.MISS) return null;
        var hit = trace(s.world, at, at.add(0, -12, 0));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getSide() != net.minecraft.util.math.Direction.UP) return null;
        return hit.getPos();
    }
    /** Space above the marked floor, clipped to a solid ceiling; fluids do not form a ceiling. */
    private static double ceiling(ServerWorld world, Vec3d target) {
        var from = target.add(0, .05, 0);
        var hit = world.raycast(new RaycastContext(from, target.add(0, HEIGHT, 0), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
        return Math.max(0, hit.getPos().y - from.y);
    }
    private static void issue(Storm storm, Session s, long now) {
        // Reserve a whole wave. Crowded servers never turn a 5–10 wave into single stray strikes.
        if (MAX_METEORS - storm.meteors.size() < MAX_SHOWER) { s.next = now + 20; return; }
        int wanted = MIN_SHOWER + s.player.getRandom().nextInt(MAX_SHOWER - MIN_SHOWER + 1);
        var selected = new ArrayList<Meteor>(wanted);
        for (int attempt = 0; attempt < SEARCH_ATTEMPTS && selected.size() < wanted; attempt++) {
            double angle = s.player.getRandom().nextDouble() * Math.PI * 2;
            double radius = attempt == 0 ? .25 : 3 + s.player.getRandom().nextDouble() * (MAX_RADIUS - 3);
            double x = s.player.getX() + Math.cos(angle) * radius;
            double z = s.player.getZ() + Math.sin(angle) * radius;
            if (!available(s.world, x, z)) continue;
            var target = floor(s, x, z);
            if (target == null || ceiling(s.world, target) < 1.5) continue;
            if (storm.meteors.stream().anyMatch(m -> m.world == s.world && m.target.squaredDistanceTo(target) < MIN_SPACING * MIN_SPACING)
                    || selected.stream().anyMatch(m -> m.target.squaredDistanceTo(target) < MIN_SPACING * MIN_SPACING)) continue;
            var m = new Meteor(s, target, now + selected.size() * 3L);
            // Open terrain alternates falling meteors and rising geysers. Caves always erupt locally.
            m.geyser |= selected.size() % 2 == 0;
            if (m.geyser) { m.height = Math.min(COLUMN_HEIGHT, m.height); m.position = m.target; }
            m.id = ++storm.nextId; selected.add(m);
        }
        if (selected.size() < MIN_SHOWER) { s.next = now + 40; return; }
        storm.meteors.addAll(selected);
        s.next = now + interval(s.player);
        selected.forEach(MeteorShowerChallenge::mark);
        s.player.sendMessage(Text.translatable("text.cursecycle.meteor_shower.warning"), false);
        s.world.playSound(null, s.player.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                SoundCategory.HOSTILE, .8F, .55F);
    }
    private static boolean surfacePresent(Meteor m) {
        var hit = trace(m.world, m.target.add(0, .2, 0), m.target.add(0, -.2, 0));
        return hit.getType() == HitResult.Type.BLOCK && hit.getPos().squaredDistanceTo(m.target) < .01;
    }
    private static void eruptDamage(Meteor m) {
        m.height = Math.min(m.height, Math.min(COLUMN_HEIGHT, ceiling(m.world, m.target)));
        var box = new Box(m.target.x - COLUMN_RADIUS, m.target.y, m.target.z - COLUMN_RADIUS,
                m.target.x + COLUMN_RADIUS, m.target.y + m.height, m.target.z + COLUMN_RADIUS);
        var candidates = new ArrayList<LivingEntity>(32);
        m.world.collectEntitiesByType(TypeFilter.instanceOf(LivingEntity.class), box,
                e -> e.isAlive() && !e.isSpectator(), candidates, 32);
        for (var entity : candidates) {
            var current = STORMS.get(m.world.getServer());
            if (current == null || current.sessions.get(m.owner) != m.session || !active(m.session.player)) return;
            if (entity instanceof net.minecraft.entity.player.PlayerEntity p && p.isCreative()) continue;
            if (MathHelper.square(entity.getX() - m.target.x) + MathHelper.square(entity.getZ() - m.target.z) > COLUMN_RADIUS * COLUMN_RADIUS) continue;
            if (trace(m.world, m.target.add(0, .2, 0), entity.getPos().add(0, .3, 0)).getType() != HitResult.Type.MISS) continue;
            if (entity.damage(m.world.getDamageSources().inFire(), 6)) {
                entity.setOnFireFor(3);
                var velocity = entity.getVelocity();
                entity.setVelocity(velocity.x, Math.max(velocity.y, .65), velocity.z); entity.velocityModified = true;
            }
        }
    }
    private static void impact(Meteor m, Vec3d point, long now) {
        // Commit before callbacks: damage can stop the challenge and remove this very queue.
        m.position = point; m.impactedAt = now;
        m.world.createExplosion(null, null, null, point.x, point.y, point.z, POWER, false, World.ExplosionSourceType.TNT,
                ParticleTypes.FLAME, ParticleTypes.LARGE_SMOKE, SoundEvents.ENTITY_GENERIC_EXPLODE);
    }
    private static boolean advance(Meteor m, long now) {
        if (!available(m.world, m.target.x, m.target.z)) return true;
        if (m.impactedAt >= 0) {
            long age = now - m.impactedAt;
            if (age >= (m.geyser ? ERUPTION_TICKS : 20)) return true;
            if (m.geyser && age > 0 && age % 10 == 0) eruptDamage(m);
            return false;
        }
        if (now - m.issued < WARNING) {
            if (now >= m.issued && (now - m.issued) % 10 == 0) mark(m);
            return false;
        }
        if (now - m.issued > WARNING + HEIGHT * 2) return true;
        if (m.geyser) {
            if (!surfacePresent(m)) return true;
            m.height = Math.min(COLUMN_HEIGHT, ceiling(m.world, m.target));
            if (m.height < .5) return true;
            impact(m, m.target.add(0, .1, 0), now); return false;
        }
        Vec3d next = new Vec3d(m.position.x, Math.max(m.target.y, m.position.y - SPEED), m.position.z);
        var hit = trace(m.world, m.position, next.add(0, -1.0E-6, 0));
        if (hit.getType() == HitResult.Type.BLOCK) { impact(m, hit.getPos(), now); return false; }
        if (next.y <= m.target.y) return true;
        m.position = next;
        // Compact vanilla fallback; the main visual uses downloaded animation and custom geometry.
        m.world.spawnParticles(ParticleTypes.FLAME, next.x, next.y, next.z, 3, .15, .2, .15, .01);
        return false;
    }
    private static void sync(MinecraftServer server, Storm storm, long now) {
        for (var p : server.getPlayerManager().getPlayerList()) {
            if (!ServerPlayNetworking.canSend(p, MeteorShowerStatePayload.ID)) continue;
            var strikes = storm.meteors.stream().filter(m -> m.world == p.getServerWorld() && p.squaredDistanceTo(m.target) <= 128 * 128)
                    .map(m -> new MeteorShowerStatePayload.Strike(m.id, m.target, m.position, m.geyser,
                            (int) (now - m.issued), m.impactedAt < 0 ? -1 : (int) (now - m.impactedAt), (float) m.height)).toList();
            ServerPlayNetworking.send(p, new MeteorShowerStatePayload(p.getServerWorld().getRegistryKey().getValue().toString(), strikes));
        }
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
        if (STORMS.get(server) != storm) return;
        // At most one bounded search (64 candidates) per server tick, for all dimensions/players.
        for (var s : List.copyOf(storm.sessions.values())) if (now >= s.next) {
            issue(storm, s, now);
            // Rotate due owners so saturation does not always favour the first connected player.
            storm.sessions.remove(s.player.getUuid()); storm.sessions.put(s.player.getUuid(), s);
            break;
        }
        if (storm.sessions.isEmpty()) clear(server);
        else if (now % 2 == 0) sync(server, storm, now);
    }
    public static void forget(ServerPlayerEntity p) {
        var storm = STORMS.get(p.getServer());
        if (storm == null) return;
        var s = storm.sessions.get(p.getUuid());
        if (s == null || s.player != p) return;
        storm.sessions.remove(p.getUuid()); storm.meteors.removeIf(m -> m.owner.equals(p.getUuid()));
        if (storm.sessions.isEmpty()) clear(p.getServer());
        else sync(p.getServer(), storm, storm.age(p.getServer()));
    }
    public static void clear(MinecraftServer server) {
        var storm = STORMS.remove(server);
        if (storm != null) { storm.meteors.clear(); sync(server, storm, 0); }
    }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity p) { forget(p); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        forget(oldPlayer);
    }
}
