package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class FragileFloorChallenge extends Challenge {
    public static final String ID = "fragile_floor";
    public static final int STANDING_THRESHOLD_TICKS = 40;
    private static final double CONTACT_EPSILON = 1.0E-6;

    // No player/world references or persisted clocks. A new server starts with no contacts.
    private final Map<MinecraftServer, Map<UUID, Standing>> standing = new WeakHashMap<>();
    // Synthetic negative IDs keep simultaneous supports separate from player mining animations.
    private int nextCrackId = Integer.MIN_VALUE;
    private record Standing(long activation, int entityId, RegistryKey<World> world, GameMode mode,
                            Contacts<BlockPos, Block> contacts, Map<BlockPos, Crack> cracks) {}
    private record Crack(int id, int stage) {}
    private record Support(BlockPos pos, boolean touchesTop) {}

    // Технические нюансы; не включать в описание для игрока.
    // Нужны две секунды непрерывной опоры на один блок. Материал, прочность и принадлежность постройке не
    // важны; контейнеры и неразрушимые блоки тоже подходят. Уход или прыжок сбрасывает отсчёт. Разрушение и
    // выпавшие вещи не откатываются.
    public FragileFloorChallenge() {
        super(ID, "Хрупкий пол",
                "Если стоять на блоке слишком долго, то он ломается.",
                "Через две секунды под твоим весом ломается любой блок. Даже бедрок? Трещины и нарастающий треск предупреждают об обрушении. Отойди или прыгни, чтобы сбросить отсчёт.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    /** Consecutive observations, never elapsed world/day time or combined player time. */
    static final class Contacts<K, T> {
        private record Count<T>(T type, long tick, int count) {}
        private Map<K, Count<T>> counts = new HashMap<>();
        private long lastTick = Long.MIN_VALUE;

        Map<K, T> observe(long tick, Map<K, T> supports) {
            if (lastTick == tick) return Map.of();
            lastTick = tick;
            var next = new HashMap<K, Count<T>>();
            var due = new LinkedHashMap<K, T>();
            supports.forEach((pos, type) -> {
                var old = counts.get(pos);
                int count = old != null && old.type.equals(type) && old.tick == tick - 1 ? old.count + 1 : 1;
                if (count >= STANDING_THRESHOLD_TICKS) due.put(pos, type);
                else next.put(pos, new Count<>(type, tick, count));
            });
            counts = next;
            return due;
        }
    }

    /** Query only collisions at the feet; side faces and entities cannot support this clock. */
    public static Map<BlockPos, Block> supports(ServerPlayerEntity player) {
        var result = new LinkedHashMap<BlockPos, Block>();
        if (player.hasVehicle() || player.getVelocity().y > CONTACT_EPSILON) return result;
        Box box = player.getBoundingBox();
        Box feet = new Box(box.minX, box.minY - CONTACT_EPSILON, box.minZ,
                box.maxX, box.minY, box.maxZ);
        var world = player.getServerWorld();
        var collisions = new BlockCollisionSpliterator<>(world, player, feet, false, (pos, shape) ->
                new Support(pos.toImmutable(), shape.getBoundingBoxes().stream().anyMatch(part ->
                        Math.abs(part.maxY - box.minY) < CONTACT_EPSILON
                                && part.maxX > box.minX && part.minX < box.maxX
                                && part.maxZ > box.minZ && part.minZ < box.maxZ)));
        while (collisions.hasNext()) {
            var support = collisions.next();
            if (support.touchesTop) result.put(support.pos, world.getBlockState(support.pos).getBlock());
        }
        return result;
    }

    @Override public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player.isRemoved() || !ChallengeActivity.active(player, ID)) { onStop(server, player); return; }
        var players = standing.computeIfAbsent(server, ignored -> new HashMap<>());
        long activation = CurseCycleState.getServerState(server).getActivationSerial();
        var world = player.getServerWorld();
        var mode = player.interactionManager.getGameMode();
        var clock = players.get(player.getUuid());
        if (clock == null || clock.activation != activation || clock.entityId != player.getId()
                || !clock.world.equals(world.getRegistryKey()) || clock.mode != mode) {
            if (clock != null) clearCracks(server, clock);
            clock = new Standing(activation, player.getId(), world.getRegistryKey(), mode, new Contacts<>(), new HashMap<>());
            players.put(player.getUuid(), clock);
        }
        var due = clock.contacts.observe(server.getTicks(), supports(player));
        updateCracks(server, clock);
        for (var entry : due.entrySet()) {
            // Neighbour updates from a preceding break can remove/change another due support.
            if (!ChallengeActivity.active(player, ID) || player.getServerWorld() != world) break;
            if (world.getBlockState(entry.getKey()).isOf(entry.getValue())
                    && supports(player).get(entry.getKey()) == entry.getValue()) {
                // Vanilla empty-tool loot, BlockEntity contents, fluids, neighbours and game event.
                // No player mining gate, afterBreak, tool wear, or extra XP is introduced.
                world.breakBlock(entry.getKey(), true, player);
            }
        }
    }

    private void updateCracks(MinecraftServer server, Standing clock) {
        var world = server.getWorld(clock.world);
        if (world == null) return;
        clock.cracks.entrySet().removeIf(entry -> {
            if (clock.contacts.counts.containsKey(entry.getKey())) return false;
            world.setBlockBreakingInfo(entry.getValue().id, entry.getKey(), -1);
            return true;
        });
        BlockPos soundPos = null;
        int soundStage = -1;
        for (var entry : clock.contacts.counts.entrySet()) {
            int stage = entry.getValue().count * 10 / STANDING_THRESHOLD_TICKS;
            var previous = clock.cracks.get(entry.getKey());
            if (previous != null && previous.stage == stage) continue;
            int id = previous == null ? nextCrackId++ : previous.id;
            clock.cracks.put(entry.getKey(), new Crack(id, stage));
            world.setBlockBreakingInfo(id, entry.getKey(), stage);
            // Intervals shorten from 12 to 8 to 4 ticks as collapse approaches.
            if ((stage == 2 || stage == 5 || stage >= 7) && stage > soundStage) {
                soundPos = entry.getKey();
                soundStage = stage;
            }
        }
        // At most one creak per player/tick even at the intersection of several blocks.
        if (soundPos != null) {
            var material = world.getBlockState(soundPos).getSoundGroup();
            world.playSound(null, soundPos, material.getHitSound(), SoundCategory.BLOCKS,
                    0.15F + soundStage * 0.04F, 0.65F + soundStage * 0.035F);
        }
    }

    private static void clearCracks(MinecraftServer server, Standing clock) {
        var world = server.getWorld(clock.world);
        if (world != null) clock.cracks.forEach((pos, crack) -> world.setBlockBreakingInfo(crack.id, pos, -1));
        clock.cracks.clear();
    }

    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { onStop(server, player); }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        // Disabled IDs are pruned before tick/leave dispatch, so those callbacks cannot clean up.
        if (!enabled) {
            standing.forEach((server, players) -> players.values().forEach(clock -> clearCracks(server, clock)));
            standing.clear();
        }
    }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) {
            var players = standing.remove(server);
            if (players != null) players.values().forEach(clock -> clearCracks(server, clock));
            return;
        }
        var players = standing.get(server);
        if (players != null) {
            var clock = players.remove(player.getUuid());
            if (clock != null) clearCracks(server, clock);
            if (players.isEmpty()) standing.remove(server);
        }
    }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) { onStop(server, player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                         ServerPlayerEntity newPlayer, boolean alive) { onStop(server, oldPlayer); }
    @Override public void onPlayerJump(MinecraftServer server, ServerPlayerEntity player) { onStop(server, player); }
}
