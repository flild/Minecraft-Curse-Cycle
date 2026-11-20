package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.network.BlackHoleStatePayload;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.*;

import java.util.*;

/** Persistent hunters with bounded world work. All physics/destruction is server-authoritative. */
public final class BlackHoleChallenge extends Challenge {
    public static final String ID = "black_hole";
    public static final int WARNING = 60, MAX_HOLES = 4, SEARCH_ATTEMPTS = 16, ENTITY_LIMIT = 128;
    public static final int PULL_PERIOD = 2, DAMAGE_PERIOD = 20, SYNC_PERIOD = 5;
    public static final int RADIUS = 24, BLOCK_RADIUS = 10, MIN_DISTANCE = 28, MAX_DISTANCE = 32;
    public static final int RELOCATE_DISTANCE = 60, RETRY = 40, BLOCK_PERIOD = 4;
    public static final int BLOCK_READ_LIMIT = 96, BLOCK_LIMIT = 3;
    public static final double CORE_RADIUS = 2, FOLLOW_SPEED = .085, MAX_INWARD_SPEED = .9;
    private static final Map<MinecraftServer, Field> FIELDS = new HashMap<>();

    public BlackHoleChallenge() {
        super(ID, "Пылесос реальности", "Чёрная дыра преследует тебя и поглощает окружение.",
                "Чёрная дыра медленно преследует тебя. В радиусе 24 блоков она затягивает игроков, мобов и вещи, "
                        + "а в десяти блоках вырывает блоки из окружения. В ядре — сильный урон. "
                        + "Если убежать дальше 60 блоков, дыра переместится следом. На её появление есть три секунды.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    private static final class Session {
        final ServerPlayerEntity player;
        final ServerWorld world;
        long next;
        Session(ServerPlayerEntity player, long now) {
            this.player = player; world = player.getServerWorld(); next = now;
        }
    }
    private static final class Hole {
        final int id;
        final Session session;
        final ServerWorld world;
        Vec3d center;
        long issued;
        Hole(int id, Session session, Vec3d center, long issued) {
            this.id = id; this.session = session; world = session.world; this.center = center; this.issued = issued;
        }
    }
    private record Debris(ServerWorld world, BlackHoleStatePayload.Fragment fragment) {}
    private static final class Field {
        final CurseCycleState state;
        final long activation;
        final int start;
        int lastTick, nextId = 1;
        final Map<UUID, Session> sessions = new LinkedHashMap<>();
        final List<Hole> holes = new ArrayList<>();
        final List<Debris> debris = new ArrayList<>();
        Field(MinecraftServer server, CurseCycleState state) {
            this.state = state; activation = state.getActivationSerial();
            start = server.getTicks(); lastTick = start - 1;
        }
        long age(MinecraftServer server) { return Integer.toUnsignedLong(server.getTicks() - start); }
    }
    private static boolean active(ServerPlayerEntity p) {
        return !p.isRemoved() && ChallengeActivity.active(p, ID)
                && p.getServer().getPlayerManager().getPlayer(p.getUuid()) == p;
    }
    /** Never load chunks or acquire tickets, including during relocation and terrain sampling. */
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
    private static void announce(Hole h) {
        for (var p : h.world.getPlayers()) if (p.squaredDistanceTo(h.center) <= 96 * 96)
            p.sendMessage(Text.translatable("text.cursecycle.black_hole.warning"), false);
        h.world.playSound(null, BlockPos.ofFloored(h.center), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                SoundCategory.HOSTILE, 2F, .5F);
    }
    /** One bounded spawn/relocation search per server tick shared by every dimension. */
    private static boolean issue(Field field, Session session, long now) {
        var existing = field.holes.stream().filter(h -> h.session == session).findFirst().orElse(null);
        session.next = now + RETRY;
        if (existing == null && field.holes.size() >= MAX_HOLES) return false;
        for (int attempt = 0; attempt < SEARCH_ATTEMPTS; attempt++) {
            double angle = session.player.getRandom().nextDouble() * Math.PI * 2;
            double radius = MIN_DISTANCE + session.player.getRandom().nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            var center = session.player.getPos().add(Math.cos(angle) * radius, 2, Math.sin(angle) * radius);
            if (!available(session.world, center) || !session.world.getBlockState(BlockPos.ofFloored(center)).isAir()) continue;
            boolean overlaps = false;
            for (var other : field.holes) if (other != existing && other.world == session.world
                    && other.center.squaredDistanceTo(center) < 12 * 12) { overlaps = true; break; }
            if (overlaps) continue;
            if (existing == null) {
                existing = new Hole(field.nextId++, session, center, now); field.holes.add(existing);
            } else {
                int relocatedId = existing.id;
                field.debris.removeIf(d -> d.fragment.holeId() == relocatedId);
                existing.center = center; existing.issued = now;
            }
            announce(existing);
            return true;
        }
        return false;
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
        double depth = Math.max(0, 1 - distance / RADIUS);
        double force = .012 + .19 * depth * depth;
        double addition = Math.min(force, Math.max(0, MAX_INWARD_SPEED - e.getVelocity().dotProduct(direction)));
        if (addition <= 0) return;
        var tangent = new Vec3d(-direction.z, 0, direction.x).multiply(addition * .22);
        // Close mobs/items overcome gravity and visibly rise into the disk; players keep ordinary movement.
        double lift = e instanceof ServerPlayerEntity ? 0 : .22 * Math.max(0, 1 - distance / 12);
        e.addVelocity(direction.multiply(addition).add(tangent).add(0, lift, 0));
        e.velocityModified = true;
    }
    private static void affect(MinecraftServer server, Field field, long now) {
        var nearest = new LinkedHashMap<Entity, Hole>();
        for (var h : List.copyOf(field.holes)) {
            if (now - h.issued < WARNING || !valid(server, field, h)
                    || !available(h.world, h.center) || h.center.distanceTo(h.session.player.getPos().add(0, 2, 0)) > RELOCATE_DISTANCE) continue;
            var candidates = new ArrayList<Entity>();
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
            if (!valid(server, field, h) || !eligible(e) || e.getWorld() != h.world) continue;
            pull(e, h.center);
            if (e.getBoundingBox().getCenter().squaredDistanceTo(h.center) <= CORE_RADIUS * CORE_RADIUS) {
                if (e instanceof ItemEntity) e.discard();
                else if (now % DAMAGE_PERIOD == 0 && e instanceof LivingEntity living)
                    living.damage(h.world.getDamageSources().magic(), 8F);
            }
        }
    }
    private static boolean erodible(ServerWorld world, BlockPos pos) {
        return erodible(world, pos, world.getBlockState(pos));
    }
    private static boolean erodible(ServerWorld world, BlockPos pos, net.minecraft.block.BlockState block) {
        return !block.isAir() && block.getFluidState().isEmpty() && !block.hasBlockEntity()
                && block.getHardness(world, pos) >= 0 && block.getBlock().getBlastResistance() < 1200;
    }
    /** Rays peel the first surface. At most 90 sampled blocks and 3 removals per hole/pass. */
    private static void erode(Field field, Hole h) {
        int removed = 0;
        var random = h.session.player.getRandom();
        for (int ray = 0; ray < BLOCK_READ_LIMIT / BLOCK_RADIUS && removed < BLOCK_LIMIT; ray++) {
            double y = random.nextDouble() * 2 - 1, angle = random.nextDouble() * Math.PI * 2;
            double flat = Math.sqrt(1 - y * y);
            var direction = new Vec3d(flat * Math.cos(angle), y, flat * Math.sin(angle));
            for (int distance = 1; distance <= BLOCK_RADIUS; distance++) {
                var pos = BlockPos.ofFloored(h.center.add(direction.multiply(distance)));
                if (pos.getY() < h.world.getBottomY() || pos.getY() >= h.world.getTopY()) break;
                if (Vec3d.ofCenter(pos).squaredDistanceTo(h.center) > BLOCK_RADIUS * BLOCK_RADIUS) continue;
                var state = h.world.getBlockState(pos);
                if (state.isAir()) continue;
                if (erodible(h.world, pos, state) && random.nextDouble() < 1 / (1 + state.getHardness(h.world, pos) * 2)) {
                    // No falling entities/drops: clients animate a bounded fragment of the consumed block.
                    if (h.world.removeBlock(pos, false)) {
                        field.debris.add(new Debris(h.world, new BlackHoleStatePayload.Fragment(h.id, pos, Block.getRawIdFromState(state))));
                        removed++;
                    }
                }
                break;
            }
        }
    }
    private static void sync(MinecraftServer server, Field field, long now) {
        for (var p : server.getPlayerManager().getPlayerList()) {
            if (!ServerPlayNetworking.canSend(p, BlackHoleStatePayload.ID)) continue;
            var holes = field.holes.stream().filter(h -> h.world == p.getServerWorld() && p.squaredDistanceTo(h.center) <= 128 * 128)
                    .map(h -> new BlackHoleStatePayload.Hole(h.id, h.center, (int) Math.max(0, WARNING - (now - h.issued)))).toList();
            var fragments = field.debris.stream().filter(d -> d.world == p.getServerWorld()
                    && p.squaredDistanceTo(Vec3d.ofCenter(d.fragment.pos())) <= 128 * 128).map(Debris::fragment).toList();
            ServerPlayNetworking.send(p, new BlackHoleStatePayload(p.getServerWorld().getRegistryKey().getValue().toString(), holes, fragments));
        }
        field.debris.clear();
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
        boolean changed = field.holes.removeIf(h -> !valid(server, current, h));
        boolean searched = false;
        for (var h : List.copyOf(field.holes)) {
            if (!valid(server, field, h)) continue;
            var target = h.session.player.getPos().add(0, 2, 0);
            double distance = h.center.distanceTo(target);
            if (distance > RELOCATE_DISTANCE || !available(h.world, h.center)) {
                if (!searched && now >= h.session.next) { searched = true; changed |= issue(field, h.session, now); }
                continue;
            }
            if (now - h.issued < WARNING) continue;
            var next = h.center.add(target.subtract(h.center).normalize().multiply(Math.min(FOLLOW_SPEED, distance)));
            if (available(h.world, next)) h.center = next;
            if (now % BLOCK_PERIOD == 0) erode(field, h);
            if (now % 40 == 0) h.world.playSound(null, BlockPos.ofFloored(h.center),
                    SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.HOSTILE, 1.5F, .5F);
        }
        if (now % PULL_PERIOD == 0) affect(server, field, now);
        if (FIELDS.get(server) != field || field.state != CurseCycleState.getServerState(server)
                || field.activation != field.state.getActivationSerial()) return;
        if (!searched) for (var s : List.copyOf(field.sessions.values())) {
            if (active(s.player) && now >= s.next && field.holes.stream().noneMatch(h -> h.session == s)) {
                changed |= issue(field, s, now);
                field.sessions.remove(s.player.getUuid()); field.sessions.put(s.player.getUuid(), s);
                break;
            }
        }
        if (field.sessions.isEmpty()) clear(server);
        else if (changed || now % SYNC_PERIOD == 0) sync(server, field, now);
    }
    public static void forget(ServerPlayerEntity p) {
        var field = FIELDS.get(p.getServer());
        if (field == null) return;
        var session = field.sessions.get(p.getUuid());
        if (session == null || session.player != p) return;
        field.sessions.remove(p.getUuid()); field.holes.removeIf(h -> h.session == session);
        if (field.sessions.isEmpty()) clear(p.getServer());
        else sync(p.getServer(), field, field.age(p.getServer()));
    }
    public static void clear(MinecraftServer server) {
        var field = FIELDS.remove(server);
        if (field == null) return;
        field.holes.clear(); field.debris.clear(); sync(server, field, 0);
    }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity p) { forget(p); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        forget(oldPlayer);
    }
}
