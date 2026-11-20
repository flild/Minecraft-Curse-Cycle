package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Incremental, non-loading source removal; no water ownership or restoration queue. */
public final class DroughtChallenge extends Challenge {
    public static final String ID = "drought";
    public static final int RADIUS = 6, HALF_HEIGHT = 3, INTERVAL = 5;
    public static final int SCAN_BUDGET = 256, REMOVAL_BUDGET = 8;
    private static final List<BlockPos> OFFSETS = offsets();
    private static final Map<MinecraftServer, Sweep> SWEEPS = new HashMap<>();

    public DroughtChallenge() {
        super(ID, "Засуха", "Ближние источники воды постепенно испаряются.",
                "В радиусе шести блоков и на три блока выше и ниже тебя постепенно исчезают источники воды. "
                        + "Вода из ведра тоже испаряется, а затопленные блоки теряют воду. "
                        + "Водоёмы, полив и водные конструкции могут высохнуть. После испытания вода не возвращается.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }
    private record Area(ServerWorld world, BlockPos center) {}
    private record Site(ServerWorld world, BlockPos pos) {}
    private static final class Cursor {
        final int entityId;
        final ServerWorld world;
        int offset;
        Cursor(net.minecraft.server.network.ServerPlayerEntity player) {
            entityId = player.getId(); world = player.getServerWorld();
        }
    }
    private static final class Sweep {
        final CurseCycleState state;
        final long activation;
        int lastTick, areaCursor;
        final Map<UUID, Cursor> cursors = new LinkedHashMap<>();
        Sweep(CurseCycleState state, int now) {
            this.state = state; activation = state.getActivationSerial(); lastTick = now;
        }
    }
    private static List<BlockPos> offsets() {
        var result = new ArrayList<BlockPos>();
        // Contiguous rows, from the shore inward in each layer; revisit regenerated sources too.
        for (int y = -HALF_HEIGHT; y <= HALF_HEIGHT; y++)
            for (int z = -RADIUS; z <= RADIUS; z++)
                for (int x = -RADIUS; x <= RADIUS; x++)
                    if (x * x + z * z <= RADIUS * RADIUS) result.add(new BlockPos(x, y, z));
        return List.copyOf(result);
    }
    private static boolean available(ServerWorld world, BlockPos pos) {
        if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) return false;
        // Ordinary block/neighbour callbacks need immediate neighbours, including at chunk edges.
        for (int x = (pos.getX() - 1) >> 4; x <= (pos.getX() + 1) >> 4; x++)
            for (int z = (pos.getZ() - 1) >> 4; z <= (pos.getZ() + 1) >> 4; z++)
                if (world.getChunkManager().getWorldChunk(x, z) == null) return false;
        return true;
    }
    private static boolean evaporate(Area area, BlockPos pos) {
        if (!available(area.world, pos)) return false;
        var state = area.world.getBlockState(pos);
        var fluid = state.getFluidState();
        if (!fluid.isIn(FluidTags.WATER) || !fluid.isStill()) return false;
        // Keep every other property and the existing block entity. Aquatic plants without a
        // WATERLOGGED property cannot live without their source; ordinary updates handle them.
        var dry = state.contains(Properties.WATERLOGGED)
                ? state.with(Properties.WATERLOGGED, false) : Blocks.AIR.getDefaultState();
        return area.world.setBlockState(pos, dry, Block.NOTIFY_ALL);
    }
    public static void tick(MinecraftServer server) {
        var state = CurseCycleState.getServerState(server);
        int now = server.getTicks();
        var sweep = SWEEPS.get(server);
        if (sweep == null || sweep.state != state || sweep.activation != state.getActivationSerial()) {
            sweep = new Sweep(state, now); SWEEPS.put(server, sweep);
        }
        // One server-wide pass, independent of repeated callbacks or effective-ID duplication.
        if (now - sweep.lastTick < INTERVAL) return;
        sweep.lastTick = now;
        var current = new LinkedHashMap<Area, List<Cursor>>();
        var owners = new HashSet<UUID>();
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (player.isRemoved() || !ChallengeActivity.active(player, ID)) continue;
            owners.add(player.getUuid());
            var cursor = sweep.cursors.get(player.getUuid());
            if (cursor == null || cursor.entityId != player.getId() || cursor.world != player.getServerWorld()) {
                cursor = new Cursor(player); sweep.cursors.put(player.getUuid(), cursor);
            }
            current.computeIfAbsent(new Area(player.getServerWorld(), player.getBlockPos()),
                    ignored -> new ArrayList<>()).add(cursor);
        }
        sweep.cursors.keySet().retainAll(owners);
        var areas = List.copyOf(current.keySet());
        if (areas.isEmpty()) return;
        var visited = new HashSet<Site>();
        int removed = 0, attempts = Math.min(SCAN_BUDGET, OFFSETS.size() * areas.size());
        int next = Math.floorMod(sweep.areaCursor, areas.size());
        for (int i = 0; i < attempts && removed < REMOVAL_BUDGET; i++) {
            var area = areas.get(next);
            next = (next + 1) % areas.size();
            var cursors = current.get(area);
            int cursor = cursors.getFirst().offset;
            var pos = area.center.add(OFFSETS.get(cursor));
            cursors.getFirst().offset = (cursor + 1) % OFFSETS.size();
            // Overlap never performs duplicate reads/mutations in one pass; even skipped work
            // consumes the attempt budget. Identical player regions already share one cursor.
            if (visited.add(new Site(area.world, pos)) && evaporate(area, pos)) removed++;
        }
        for (var group : current.values()) {
            int offset = group.getFirst().offset;
            for (var owner : group) owner.offset = offset;
        }
        sweep.areaCursor = next;
    }
    public static void clear(MinecraftServer server) { SWEEPS.remove(server); }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) SWEEPS.clear();
    }
    @Override public void onStop(MinecraftServer server, net.minecraft.server.network.ServerPlayerEntity player) {
        if (player == null) clear(server);
    }
}
