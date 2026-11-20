package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.*;

import java.util.*;

/** Bounded, transient server events; warning/lifecycle pattern follows MeteorShowerChallenge. */
public final class BlackHoleChallenge extends Challenge {
    public static final String ID = "black_hole";
    public static final int MIN_INTERVAL = 800, MAX_INTERVAL = 1200, WARNING = 60, LIFETIME = 160;
    public static final int MAX_HOLES = 4, SEARCH_ATTEMPTS = 16, ENTITY_LIMIT = 128, PULL_PERIOD = 2;
    public static final int DAMAGE_PERIOD = 20, RADIUS = 8, MIN_DISTANCE = 6, MAX_DISTANCE = 10;
    public static final double CORE_RADIUS = 1, IMPULSE = .08, MAX_INWARD_SPEED = .6;
    private static final Map<MinecraftServer, Field> FIELDS = new HashMap<>();

    public BlackHoleChallenge() {
        super(ID, "Ну и дыра",
                "Рядом иногда появляется чёрная дыра.",
                "Рядом время от времени появляется чёрная дыра. На то, чтобы отойти, есть три секунды. Потом она восемь секунд тянет к себе всё вокруг, а в самом центре смерть.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    private static final class Session {
        final ServerPlayerEntity player;
        final ServerWorld world;
        long next;
        Session(ServerPlayerEntity player, long now) {
            this.player = player; world = player.getServerWorld(); next = now + interval(player);
        }
    }
    private static final class Hole {
        final Session session;
        final ServerWorld world;
        final Vec3d center;
        final long issued;
        Hole(Session session, Vec3d center, long issued) {
            this.session = session; world = session.world; this.center = center; this.issued = issued;
        }
    }
    private static final class Field {
        final CurseCycleState state;
        final long activation;
        final int start;
        int lastTick;
        final Map<UUID, Session> sessions = new LinkedHashMap<>();
        final List<Hole> holes = new ArrayList<>();
        Field(MinecraftServer server, CurseCycleState state) {
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
    /** The whole query/collision neighbourhood must already be loaded; never acquire tickets. */
    private static boolean available(ServerWorld world, Vec3d center) {
        int margin = RADIUS + 2;
        var border = world.getWorldBorder();
        if (center.y - CORE_RADIUS < world.getBottomY() || center.y + CORE_RADIUS >= world.getTopY()
                || !border.contains(center.x - margin, center.z - margin)
                || !border.contains(center.x + margin, center.z + margin)) return false;
        for (int cx = MathHelper.floor(center.x - margin) >> 4; cx <= MathHelper.floor(center.x + margin) >> 4; cx++)
            for (int cz = MathHelper.floor(center.z - margin) >> 4; cz <= MathHelper.floor(center.z + margin) >> 4; cz++)
                if (world.getChunkManager().getWorldChunk(cx, cz) == null) return false;
        return true;
    }
    private static void mark(Hole h, boolean warning) {
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            h.world.spawnParticles(warning ? ParticleTypes.SMOKE : ParticleTypes.REVERSE_PORTAL,
                    h.center.x + Math.cos(angle), h.center.y, h.center.z + Math.sin(angle), 1, 0, 0, 0, 0);
        }
        h.world.spawnParticles(ParticleTypes.SQUID_INK, h.center.x, h.center.y, h.center.z, 3, .1, .1, .1, 0);
    }
    private static void issue(Field field, Session session, long now) {
        if (field.holes.size() < MAX_HOLES) for (int attempt = 0; attempt < SEARCH_ATTEMPTS; attempt++) {
            double angle = session.player.getRandom().nextDouble() * Math.PI * 2;
            double radius = MIN_DISTANCE + session.player.getRandom().nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            var center = session.player.getPos().add(Math.cos(angle) * radius, 1, Math.sin(angle) * radius);
            if (!available(session.world, center) || !session.world.isSpaceEmpty(new Box(center, center).expand(CORE_RADIUS))) continue;
            if (field.holes.stream().anyMatch(h -> h.world == session.world && h.center.squaredDistanceTo(center) < 16)) continue;
            var h = new Hole(session, center, now); field.holes.add(h); mark(h, true);
            for (var p : session.world.getPlayers()) if (p.squaredDistanceTo(center) <= 32 * 32)
                p.sendMessage(Text.translatable("text.cursecycle.black_hole.warning"), false);
            session.world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                    SoundCategory.HOSTILE, 1F, .5F);
            break;
        }
        // Missed/saturated searches do not accumulate a later burst.
        session.next = now + interval(session.player);
    }
    private static boolean valid(MinecraftServer server, Field field, Hole h) {
        return FIELDS.get(server) == field && field.state == CurseCycleState.getServerState(server)
                && field.activation == field.state.getActivationSerial() && field.holes.contains(h)
                && field.sessions.get(h.session.player.getUuid()) == h.session
                && active(h.session.player) && h.session.player.getServerWorld() == h.world;
    }
    private static boolean eligible(Entity e) {
        if (e.isRemoved() || !e.isAlive()) return false;
        if (e instanceof ServerPlayerEntity p) return active(p);
        return e instanceof MobEntity || e instanceof ItemEntity;
    }
    private static void pull(Entity e, Vec3d center) {
        var delta = center.subtract(e.getBoundingBox().getCenter());
        double distance = delta.length();
        if (distance < 1.0E-6) return;
        var direction = delta.multiply(1 / distance);
        // Cap only our inward addition, preserving other motion (falls, explosions, external rules).
        double addition = Math.min(IMPULSE, Math.max(0, MAX_INWARD_SPEED - e.getVelocity().dotProduct(direction)));
        if (addition <= 0) return;
        e.addVelocity(direction.multiply(addition));
        e.velocityModified = true; // Vanilla tracker sends velocity to observers AND the affected player.
    }
    private static void affect(MinecraftServer server, Field field, long now) {
        var nearest = new LinkedHashMap<Entity, Hole>();
        for (var h : List.copyOf(field.holes)) {
            long elapsed = now - h.issued;
            if (elapsed < WARNING || elapsed >= WARNING + LIFETIME || !valid(server, field, h)) continue;
            var candidates = new ArrayList<Entity>();
            // Accept all AABB intersections before filtering: even ineligible entities consume the cap.
            h.world.collectEntitiesByType(TypeFilter.instanceOf(Entity.class), new Box(h.center, h.center).expand(RADIUS),
                    e -> true, candidates, ENTITY_LIMIT);
            for (var e : candidates) if (eligible(e)
                    && e.getBoundingBox().getCenter().squaredDistanceTo(h.center) <= RADIUS * RADIUS) {
                var previous = nearest.get(e);
                if (previous == null || e.getBoundingBox().getCenter().squaredDistanceTo(h.center)
                        < e.getBoundingBox().getCenter().squaredDistanceTo(previous.center)) nearest.put(e, h);
            }
        }
        for (var entry : nearest.entrySet()) {
            var e = entry.getKey(); var h = entry.getValue();
            // Damage callbacks can stop/reset the rule, disconnect/kill an owner or teleport a target.
            if (!valid(server, field, h) || !eligible(e) || e.getWorld() != h.world) continue;
            pull(e, h.center);
            if (now % DAMAGE_PERIOD == 0
                    && e.getBoundingBox().getCenter().squaredDistanceTo(h.center) <= CORE_RADIUS * CORE_RADIUS
                    && e instanceof net.minecraft.entity.LivingEntity living)
                living.damage(h.world.getDamageSources().magic(), 4F);
        }
    }

    public static void tick(MinecraftServer server) {
        var state = CurseCycleState.getServerState(server); var field = FIELDS.get(server);
        if (field != null && (field.state != state || field.activation != state.getActivationSerial())) {
            clear(server); field = null;
        }
        var players = server.getPlayerManager().getPlayerList();
        if (field == null) {
            if (players.stream().noneMatch(BlackHoleChallenge::active)) return;
            field = new Field(server, state); FIELDS.put(server, field);
        }
        if (field.lastTick == server.getTicks()) return;
        field.lastTick = server.getTicks(); long now = field.age(server);
        field.sessions.values().removeIf(s -> !active(s.player) || s.player.getServerWorld() != s.world);
        for (var p : players) if (active(p)) {
            var s = field.sessions.get(p.getUuid());
            if (s == null || s.player != p) field.sessions.put(p.getUuid(), new Session(p, now));
        }
        var current = field;
        field.holes.removeIf(h -> !valid(server, current, h) || now - h.issued >= WARNING + LIFETIME
                || !available(h.world, h.center));
        for (var h : field.holes) if ((now - h.issued) % 10 == 0) mark(h, now - h.issued < WARNING);
        if (now % PULL_PERIOD == 0) affect(server, field, now);
        if (FIELDS.get(server) != field || field.state != CurseCycleState.getServerState(server)
                || field.activation != field.state.getActivationSerial()) return;
        for (var s : List.copyOf(field.sessions.values())) if (active(s.player) && now >= s.next) {
            issue(field, s, now);
            field.sessions.remove(s.player.getUuid()); field.sessions.put(s.player.getUuid(), s);
            break; // One search, shared by all players/dimensions each tick.
        }
        if (field.sessions.isEmpty()) clear(server);
    }
    public static void forget(ServerPlayerEntity p) {
        var field = FIELDS.get(p.getServer());
        if (field == null) return;
        var session = field.sessions.get(p.getUuid());
        if (session == null || session.player != p) return;
        field.sessions.remove(p.getUuid()); field.holes.removeIf(h -> h.session == session);
        if (field.sessions.isEmpty()) clear(p.getServer());
    }
    public static void clear(MinecraftServer server) { FIELDS.remove(server); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity p) { forget(p); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        forget(oldPlayer);
    }
}
