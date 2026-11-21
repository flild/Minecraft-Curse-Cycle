package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** A complete, non-loading sponge area every server tick; no restoration queue. */
public final class DroughtChallenge extends Challenge {
    public static final String ID = "drought";
    public static final int RADIUS = 6, HALF_HEIGHT = 3;
    private static final int PARTICLE_SITES = 12;
    private static final List<BlockPos> OFFSETS = offsets();
    private static final Map<MinecraftServer, Sweep> SWEEPS = new HashMap<>();

    public DroughtChallenge() {
        super(ID, "Сушняк",
                "Вода рядом с тобой испаряется.",
                "Вода в радиусе 6 блоков испаряется каждый тик, как от губки.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
    private record Area(ServerWorld world, BlockPos center) {}
    private static final class Sweep {
        final CurseCycleState state;
        final long activation;
        int lastTick;
        Sweep(CurseCycleState state, int now) {
            this.state = state; activation = state.getActivationSerial(); lastTick = now - 1;
        }
    }
    private static final class Feedback {
        int removed;
        final List<BlockPos> sites = new ArrayList<>();
        void add(ServerWorld world, BlockPos pos) {
            // Reservoir sampling spreads steam across dense water without hundreds of packets.
            int index = ++removed <= PARTICLE_SITES ? sites.size() : world.random.nextInt(removed);
            if (sites.size() < PARTICLE_SITES) sites.add(pos);
            else if (index < PARTICLE_SITES) sites.set(index, pos);
        }
        void send(ServerWorld world) {
            if (sites.isEmpty()) return;
            for (var pos : sites)
                world.spawnParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5,
                        2, .25, .15, .25, .025);
            world.playSound(null, sites.getFirst(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
                    SoundCategory.BLOCKS, .5F, 1.6F + world.random.nextFloat() * .4F);
        }
    }
    private static List<BlockPos> offsets() {
        var result = new ArrayList<BlockPos>();
        // The entire fixed cylinder is visited each tick, including regenerated/flowing water.
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
        if (!fluid.isIn(FluidTags.WATER)) return false;
        // Keep every other property and the existing block entity. Aquatic plants without a
        // WATERLOGGED property cannot live without their source; ordinary updates handle them.
        var dry = state.contains(Properties.WATERLOGGED)
                ? state.with(Properties.WATERLOGGED, false) : Blocks.AIR.getDefaultState();
        // Match sponge drops for aquatic plants whose water cannot be drained in place.
        if (state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT)
                || state.isOf(Blocks.SEAGRASS) || state.isOf(Blocks.TALL_SEAGRASS))
            Block.dropStacks(state, area.world, pos, area.world.getBlockEntity(pos));
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
        if (now == sweep.lastTick) return;
        sweep.lastTick = now;
        var areas = new LinkedHashSet<Area>();
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (player.isRemoved() || !ChallengeActivity.active(player, ID)) continue;
            areas.add(new Area(player.getServerWorld(), player.getBlockPos()));
        }
        var visited = new HashMap<ServerWorld, Set<BlockPos>>();
        for (var area : areas) {
            var positions = visited.computeIfAbsent(area.world, ignored -> new HashSet<>());
            var feedback = new Feedback();
            for (var offset : OFFSETS) {
                var pos = area.center.add(offset);
                if (positions.add(pos) && evaporate(area, pos)) feedback.add(area.world, pos);
            }
            feedback.send(area.world);
        }
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
