package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.block.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.chunk.WorldChunk;
import java.util.*;

/** Ephemeral, bounded ownership of original fire only. Spread is entirely vanilla. */
public final class FireTrailChallenge extends Challenge {
    public static final String ID = "fire_trail";
    public static final int LIFETIME = 100, INTERVAL = 4, MAX_FIRES = 256, PLACEMENT_BUDGET = 16, LOADED_MARGIN = 24;
    public static final int DELAY = 10;
    private static final int MAX_PENDING = (DELAY + INTERVAL - 1) / INTERVAL;
    private static final Map<MinecraftServer, Trail> TRAILS = new HashMap<>();
    public FireTrailChallenge() {
        super(ID, "Призрачный гонщик",
                "Огненная поступь за тобой.",
                "Огонь появляется через полсекунды после шага, если ты отошёл, и гаснет через 5 секунд.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
    private static final class Session {
        final ServerPlayerEntity player;
        final ServerWorld world;
        final GameMode mode;
        Vec3d position;
        int sampled;
        final Deque<Pending> pending = new ArrayDeque<>();
        Session(ServerPlayerEntity p, int now) {
            player = p; world = p.getServerWorld(); mode = p.interactionManager.getGameMode();
            position = p.getPos(); sampled = now;
        }
    }
    private record Site(ServerWorld world, BlockPos pos) {}
    private record Pending(BlockPos pos, int sampled) {}
    private record Fire(Session owner, int placed) {}
    private static final class Trail {
        final CurseCycleState state;
        final long activation;
        int lastTick, cursor;
        final Map<UUID, Session> sessions = new LinkedHashMap<>();
        final Map<Site, Fire> fires = new LinkedHashMap<>();
        Trail(CurseCycleState state, int now) {
            this.state = state; activation = state.getActivationSerial(); lastTick = now - 1;
        }
    }
    private static boolean active(ServerPlayerEntity p) {
        return !p.isRemoved() && ChallengeActivity.active(p, ID)
                && p.getServer().getPlayerManager().getPlayer(p.getUuid()) == p;
    }
    /** Vanilla fire may search for a portal frame (up to 21 blocks). Check that area first. */
    private static boolean available(ServerWorld world, BlockPos pos) {
        if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos.add(-LOADED_MARGIN, 0, -LOADED_MARGIN))
                || !world.getWorldBorder().contains(pos.add(LOADED_MARGIN, 0, LOADED_MARGIN))) return false;
        for (int x = (pos.getX() - LOADED_MARGIN) >> 4; x <= (pos.getX() + LOADED_MARGIN) >> 4; x++)
            for (int z = (pos.getZ() - LOADED_MARGIN) >> 4; z <= (pos.getZ() + LOADED_MARGIN) >> 4; z++)
                if (!world.getChunkManager().isChunkLoaded(x, z)) return false;
        return true;
    }
    private static void schedule(Session s, int now) {
        var p = s.player;
        var pos = BlockPos.ofFloored(p.getX(), p.getY() + 1.0E-6, p.getZ());
        if (p.hasVehicle() || p.isTouchingWater() || p.isInLava() || !available(s.world, pos)
                || !s.world.getBlockState(pos).isAir() || FragileFloorChallenge.supports(p).isEmpty()) return;
        if (s.pending.size() < MAX_PENDING) s.pending.addLast(new Pending(pos.toImmutable(), now));
    }
    private static void place(Trail t, Session s, BlockPos pos, int now) {
        // A stopped player, a slow step across an edge, or a return must never ignite their feet.
        if (s.player.getBoundingBox().intersects(new Box(pos)) || !available(s.world, pos)
                || !s.world.getBlockState(pos).isAir()) return;
        var fireState = Blocks.FIRE.getDefaultState();
        if (!fireState.canPlaceAt(s.world, pos)) return;
        fireState = AbstractFireBlock.getState(s.world, pos);
        // Regular fire, including on soul sand; vanilla placement callbacks still apply.
        if (!fireState.isOf(Blocks.FIRE)) fireState = Blocks.FIRE.getDefaultState();
        var site = new Site(s.world, pos.toImmutable());
        var fire = new Fire(s, now);
        // Install before neighbour callbacks: nested replacements must invalidate ownership too.
        t.fires.put(site, fire);
        if (!s.world.setBlockState(pos, fireState, Block.NOTIFY_ALL)
                || !s.world.getBlockState(pos).isOf(Blocks.FIRE)) t.fires.remove(site, fire);
    }
    /** Observe the real section mutation BEFORE recursive block/neighbour callbacks. */
    public static void replaced(WorldChunk chunk, BlockPos pos, BlockState oldState, BlockState newState) {
        if (!(chunk.getWorld() instanceof ServerWorld world) || !oldState.isOf(Blocks.FIRE)
                || newState.isOf(Blocks.FIRE)) return; // Vanilla age/attachment updates retain identity.
        var t = TRAILS.get(world.getServer());
        if (t != null) t.fires.remove(new Site(world, pos));
    }
    private static void extinguish(Site site) {
        var chunk = site.world.getChunkManager().getWorldChunk(site.pos.getX() >> 4, site.pos.getZ() >> 4);
        // Non-loading lookup. Unload hook normally cleans before this becomes unavailable.
        if (chunk != null && chunk.getBlockState(site.pos).isOf(Blocks.FIRE))
            site.world.setBlockState(site.pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }
    private static void remove(Trail t, java.util.function.BiPredicate<Site, Fire> predicate) {
        for (var entry : List.copyOf(t.fires.entrySet())) {
            if (predicate.test(entry.getKey(), entry.getValue()) && t.fires.remove(entry.getKey(), entry.getValue()))
                extinguish(entry.getKey());
        }
    }
    /** setLoadedToWorld(false) precedes vanilla chunk save in 1.21.1. Use that chunk directly. */
    public static void unloading(WorldChunk chunk) {
        if (!(chunk.getWorld() instanceof ServerWorld world)) return;
        var t = TRAILS.get(world.getServer());
        if (t == null) return;
        for (var s : t.sessions.values()) if (s.world == world)
            s.pending.removeIf(pending -> new ChunkPos(pending.pos).equals(chunk.getPos()));
        for (var site : List.copyOf(t.fires.keySet())) {
            if (site.world == world && new ChunkPos(site.pos).equals(chunk.getPos())) {
                t.fires.remove(site);
                // No world/chunk lookup or neighbour query while unloading. Marks the chunk dirty.
                if (chunk.getBlockState(site.pos).isOf(Blocks.FIRE))
                    chunk.setBlockState(site.pos, Blocks.AIR.getDefaultState(), false);
            }
        }
    }
    public static void tick(MinecraftServer server) {
        var state = CurseCycleState.getServerState(server);
        var t = TRAILS.get(server);
        if (t != null && (t.state != state || t.activation != state.getActivationSerial())) {
            clear(server); t = null;
        }
        if (t == null) {
            if (server.getPlayerManager().getPlayerList().stream().noneMatch(FireTrailChallenge::active)) return;
            t = new Trail(state, server.getTicks()); TRAILS.put(server, t);
        }
        int now = server.getTicks();
        if (t.lastTick == now) return;
        t.lastTick = now;
        for (var s : List.copyOf(t.sessions.values())) {
            if (!active(s.player) || s.player.getServerWorld() != s.world
                    || s.player.interactionManager.getGameMode() != s.mode) forget(s.player);
        }
        remove(t, (site, fire) -> now - fire.placed >= LIFETIME);
        for (var p : server.getPlayerManager().getPlayerList()) if (active(p))
            t.sessions.computeIfAbsent(p.getUuid(), ignored -> new Session(p, now));
        var sessions = List.copyOf(t.sessions.values());
        int budget = PLACEMENT_BUDGET;
        for (int i = 0; i < sessions.size(); i++) {
            var s = sessions.get(Math.floorMod(t.cursor + i, sessions.size()));
            var position = s.player.getPos();
            var displacement = position.subtract(s.position);
            // Drop delayed work on teleports even between movement samples.
            if (displacement.lengthSquared() > 16) {
                s.position = position; s.sampled = now; s.pending.clear(); continue;
            }
            while (!s.pending.isEmpty() && now - s.pending.peekFirst().sampled >= DELAY) {
                var pending = s.pending.removeFirst();
                // Due entries are consumed even under saturation; no catch-up burst later.
                if (budget > 0 && t.fires.size() < MAX_FIRES) {
                    budget--; place(t, s, pending.pos, now);
                }
            }
            if (now - s.sampled < INTERVAL) continue;
            s.sampled = now;
            // Accumulate small steps too: sneaking must not bypass the trail's distance threshold.
            if (displacement.horizontalLengthSquared() < .04) continue;
            s.position = position;
            if (t.fires.size() < MAX_FIRES) schedule(s, now);
        }
        t.cursor = sessions.isEmpty() ? 0 : Math.floorMod(t.cursor + PLACEMENT_BUDGET, sessions.size());
        if (t.sessions.isEmpty() && t.fires.isEmpty()) TRAILS.remove(server);
    }
    public static void forget(ServerPlayerEntity p) {
        var t = TRAILS.get(p.getServer());
        if (t == null) return;
        var s = t.sessions.get(p.getUuid());
        if (s == null || s.player != p) return;
        t.sessions.remove(p.getUuid());
        remove(t, (site, fire) -> fire.owner == s);
    }
    public static void clear(MinecraftServer server) {
        var t = TRAILS.remove(server);
        if (t != null) remove(t, (site, fire) -> true);
    }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) for (var server : List.copyOf(TRAILS.keySet())) clear(server);
    }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity p) {
        if (p == null) clear(server); else forget(p);
    }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity p) { forget(p); }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity p) { forget(p); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                         ServerPlayerEntity newPlayer, boolean alive) { forget(oldPlayer); }
}
