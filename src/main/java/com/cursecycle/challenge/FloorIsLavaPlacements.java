package com.cursecycle.challenge;

import com.cursecycle.challenge.impl.FloorIsLavaChallenge;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.block.BlockState;
import net.minecraft.block.BedBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.Map;

/** Round-local marks, not block ownership or world history. Never serialized or chunk-loading. */
public final class FloorIsLavaPlacements {
    public static final int MAX_BLOCKS = 65_536;
    public static final int MAX_SECTIONS = 1_024;
    private static final Map<MinecraftServer, Session> SESSIONS = new HashMap<>();

    private record Session(CurseCycleState state, long activation, Marks marks) { }

    private FloorIsLavaPlacements() { }

    private static Session session(MinecraftServer server, boolean create) {
        var state = CurseCycleState.getServerState(server);
        var challenge = ChallengeRegistry.get(FloorIsLavaChallenge.ID);
        boolean active = challenge != null && challenge.isEnabled()
                && (state.getActiveChallengeIds().contains(FloorIsLavaChallenge.ID)
                || state.getPersonalActiveChallenges().values().stream()
                        .anyMatch(ids -> ids.contains(FloorIsLavaChallenge.ID)));
        var session = SESSIONS.get(server);
        if (!active || session != null && (session.state != state
                || session.activation != state.getActivationSerial())) {
            clear(server);
            session = null;
        }
        if (active && session == null && create) {
            session = new Session(state, state.getActivationSerial(), new Marks(MAX_BLOCKS, MAX_SECTIONS));
            SESSIONS.put(server, session);
        }
        return session;
    }

    /** Called after the successful BlockItem callback, including secondary bed/door halves. */
    public static void placed(World world, BlockPos pos, BlockState state, LivingEntity placer) {
        if (!(world instanceof ServerWorld serverWorld) || !(placer instanceof ServerPlayerEntity)) return;
        var session = session(serverWorld.getServer(), true);
        if (session == null) return;
        mark(session, serverWorld, pos, state);
        if (state.contains(BedBlock.PART)) {
            var direction = state.get(BedBlock.FACING);
            mark(session, serverWorld, pos.offset(state.get(BedBlock.PART) == BedPart.FOOT
                    ? direction : direction.getOpposite()), state);
        } else if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            mark(session, serverWorld, state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                    ? pos.up() : pos.down(), state);
        }
    }

    private static void mark(Session session, ServerWorld world, BlockPos pos, BlockState placed) {
        if (world.getBlockState(pos).isOf(placed.getBlock())) session.marks.add(world.getRegistryKey(), pos);
    }

    public static boolean isSafe(ServerWorld world, BlockPos pos) {
        var session = session(world.getServer(), false);
        return session != null && session.marks.contains(world.getRegistryKey(), pos);
    }

    /** Observe the actual section mutation before recursive neighbour/block callbacks. */
    public static void replaced(WorldChunk chunk, BlockPos pos, BlockState oldState, BlockState newState) {
        if (!(chunk.getWorld() instanceof ServerWorld world) || oldState.getBlock() == newState.getBlock()) return;
        var session = SESSIONS.get(world.getServer());
        if (session != null) session.marks.remove(world.getRegistryKey(), pos);
    }

    /** Also runs with no online players, when onStop(player) cannot be dispatched. */
    public static void tick(MinecraftServer server) { session(server, false); }
    public static void clear(MinecraftServer server) { SESSIONS.remove(server); }
    public static void clearAll() { SESSIONS.clear(); }

    /** Fixed 512-byte masks per occupied 16x16x16 section. Both dense and sparse spam are bounded. */
    static final class Marks {
        private record Section(RegistryKey<World> world, long position) { }
        private static final class Mask {
            final long[] bits = new long[64];
            int size;
        }
        private final Map<Section, Mask> sections = new HashMap<>();
        private final int maxBlocks, maxSections;
        private int size;

        Marks(int maxBlocks, int maxSections) {
            this.maxBlocks = maxBlocks;
            this.maxSections = maxSections;
        }

        private static Section section(RegistryKey<World> world, BlockPos pos) {
            return new Section(world, ChunkSectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        }

        private static int index(BlockPos pos) {
            return (pos.getY() & 15) << 8 | (pos.getZ() & 15) << 4 | pos.getX() & 15;
        }

        boolean contains(RegistryKey<World> world, BlockPos pos) {
            var mask = sections.get(section(world, pos));
            int index = index(pos);
            return mask != null && (mask.bits[index >>> 6] & 1L << (index & 63)) != 0;
        }

        boolean add(RegistryKey<World> world, BlockPos pos) {
            var key = section(world, pos);
            var mask = sections.get(key);
            int index = index(pos);
            long bit = 1L << (index & 63);
            if (mask != null && (mask.bits[index >>> 6] & bit) != 0) return true;
            if (size >= maxBlocks || mask == null && sections.size() >= maxSections) return false;
            if (mask == null) {
                mask = new Mask();
                sections.put(key, mask);
            }
            mask.bits[index >>> 6] |= bit;
            mask.size++;
            size++;
            return true;
        }

        void remove(RegistryKey<World> world, BlockPos pos) {
            var key = section(world, pos);
            var mask = sections.get(key);
            int index = index(pos);
            long bit = 1L << (index & 63);
            if (mask == null || (mask.bits[index >>> 6] & bit) == 0) return;
            mask.bits[index >>> 6] &= ~bit;
            size--;
            if (--mask.size == 0) sections.remove(key);
        }

        int size() { return size; }
        int sectionCount() { return sections.size(); }
    }
}
