package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class SunlightBurnChallenge extends Challenge {
    public static final String ID = "sunlight_burn";
    public static final int CHECK_INTERVAL_TICKS = 20;
    public static final int FIRE_DURATION_TICKS = 80;

    // Only scheduling data: no player/world references and no snapshot of vanilla fire.
    private final Map<MinecraftServer, Map<UUID, Check>> checks = new WeakHashMap<>();
    private record Check(long activation, int entityId, Clock clock) {}

    public SunlightBurnChallenge() {
        super(ID, "Стоило купить крем от солнца", "Под открытым дневным небом игрок загорается.",
                "В обычном мире под открытым дневным небом персонаж загорается. "
                        + "Крыша, ночь, дождь в текущей точке и погружение глаз в воду прекращают новые поджигания; "
                        + "уже начавшийся огонь затухает по обычным правилам. Шлем сам по себе не защищает, "
                        + "а Огнестойкость защищает от огненного урона как обычно. "
                        + "Проверка на первом серверном тике и затем каждые 20 тиков: Overworld, время 0–11999, "
                        + "нет дождя или снега в точке глаз, глаза не в воде, вертикальный луч над глазами "
                        + "не пересекает блоковую коллизию. Стекло, листья и плиты защищают; факелы, трава "
                        + "и облака — нет. Остаток горения повышается до 80 тиков без сокращения чужого огня. "
                        + "Creative, Spectator и мёртвые игроки исключены. В Nether и End солнечного поджигания нет. "
                        + "После завершения прекращается только обновление огня, без принудительного тушения.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    static final class Clock {
        private boolean started;
        private int last;
        boolean due(int tick) {
            if (started && tick - last < CHECK_INTERVAL_TICKS) return false;
            started = true;
            last = tick;
            return true;
        }
    }

    static boolean daytime(long timeOfDay) { return Math.floorMod(timeOfDay, 24000L) < 12000L; }

    public static boolean exposed(ServerPlayerEntity player) {
        var world = player.getServerWorld();
        if (!world.getRegistryKey().equals(World.OVERWORLD) || !daytime(world.getTimeOfDay())
                || player.isSubmergedIn(FluidTags.WATER)) return false;
        Vec3d eyes = player.getEyePos();
        BlockPos pos = BlockPos.ofFloored(eyes);
        // World.hasRain excludes snow. Match its local reach checks for BOTH precipitation types.
        if (world.isRaining() && world.isSkyVisible(pos)
                && world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, pos).getY() <= pos.getY()
                && world.getBiome(pos).value().getPrecipitation(pos) != Biome.Precipitation.NONE) return false;
        if (eyes.y >= world.getTopY()) return true;
        return world.raycast(new RaycastContext(eyes, new Vec3d(eyes.x, world.getTopY(), eyes.z),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS;
    }

    @Override public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return;
        if (player.isRemoved() || !ChallengeActivity.active(player, ID)) { onStop(server, player); return; }
        var players = checks.computeIfAbsent(server, ignored -> new HashMap<>());
        long activation = CurseCycleState.getServerState(server).getActivationSerial();
        var check = players.get(player.getUuid());
        if (check == null || check.activation != activation || check.entityId != player.getId()) {
            check = new Check(activation, player.getId(), new Clock());
            players.put(player.getUuid(), check);
        }
        if (check.clock.due(server.getTicks()) && exposed(player)) {
            // Exact ticks; setOnFireFor applies Fire Protection's duration reduction.
            player.setFireTicks(Math.max(player.getFireTicks(), FIRE_DURATION_TICKS));
        }
    }

    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { onStop(server, player); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) { checks.remove(server); return; }
        var players = checks.get(server);
        if (players != null) {
            players.remove(player.getUuid());
            if (players.isEmpty()) checks.remove(server);
        }
    }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) { onStop(server, player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                         ServerPlayerEntity newPlayer, boolean alive) { onStop(server, oldPlayer); }
}
