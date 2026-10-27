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
    private record Standing(long activation, int entityId, RegistryKey<World> world, GameMode mode,
                            Contacts<BlockPos, Block> contacts) {}
    private record Support(BlockPos pos, boolean touchesTop) {}

    public FragileFloorChallenge() {
        super(ID, "Хрупкий пол", "Стоишь на блоке 2 секунды — он ломается. Любой.",
                "После 40 непрерывных серверных тиков под ногами ломается любая блоковая опора: "
                        + "постройки, контейнеры, плиты и даже обычно неразрушимые блоки. "
                        + "На границе каждая опора отсчитывается отдельно. Движение по той же опоре "
                        + "не сбрасывает время; прыжок, уход, смена типа блока, измерения или режима, "
                        + "смерть и выход сбрасывают отсчёт. Пассажиры, Creative и Spectator исключены. "
                        + "Разрушение и содержимое контейнеров обрабатываются штатно, без инструмента; "
                        + "предметы и последствия падения не откатываются. После завершения отсчёты очищаются.",
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
            clock = new Standing(activation, player.getId(), world.getRegistryKey(), mode, new Contacts<>());
            players.put(player.getUuid(), clock);
        }
        for (var entry : clock.contacts.observe(server.getTicks(), supports(player)).entrySet()) {
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

    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { onStop(server, player); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) { standing.remove(server); return; }
        var players = standing.get(server);
        if (players != null) {
            players.remove(player.getUuid());
            if (players.isEmpty()) standing.remove(server);
        }
    }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) { onStop(server, player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                         ServerPlayerEntity newPlayer, boolean alive) { onStop(server, oldPlayer); }
    @Override public void onPlayerJump(MinecraftServer server, ServerPlayerEntity player) { onStop(server, player); }
}
