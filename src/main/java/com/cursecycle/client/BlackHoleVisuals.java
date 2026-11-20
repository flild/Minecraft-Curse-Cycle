package com.cursecycle.client;

import com.cursecycle.network.BlackHoleStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Client interpolation only; none of this state changes physics or blocks. */
public final class BlackHoleVisuals {
    public static final int MAX_FRAGMENTS = 96, STALE_TICKS = 30;
    public static final Map<Integer, Hole> HOLES = new LinkedHashMap<>();
    public static final List<Fragment> FRAGMENTS = new ArrayList<>();
    private static ClientWorld world;
    private static int ticks, received;
    public static final class Hole {
        public final int id;
        public Vec3d from, target;
        public int sampled, warning;
        Hole(BlackHoleStatePayload.Hole sample) {
            id = sample.id(); from = target = sample.center(); sampled = ticks; warning = sample.warningTicks();
        }
        public Vec3d position(float delta) {
            return from.lerp(target, Math.min(1, Math.max(0, (ticks + delta - sampled) / 5.0)));
        }
        public float warning(float delta) { return Math.max(0, warning - (ticks + delta - sampled)); }
    }
    public record Fragment(BlackHoleStatePayload.Fragment sample, int born) {
        public float age(float delta) { return ticks + delta - born; }
    }
    private BlackHoleVisuals() {}
    public static void clear() { HOLES.clear(); FRAGMENTS.clear(); world = null; ticks = received = 0; }
    public static void tick(MinecraftClient client) {
        if (client.world == null || client.world != world) { clear(); world = client.world; }
        ticks++;
        if (ticks - received > STALE_TICKS) { HOLES.clear(); FRAGMENTS.clear(); }
        FRAGMENTS.removeIf(f -> f.age(0) >= 40 || !HOLES.containsKey(f.sample.holeId()));
    }
    public static void receive(MinecraftClient client, BlackHoleStatePayload payload) {
        if (client.world == null || !client.world.getRegistryKey().getValue().toString().equals(payload.dimension())) return;
        if (client.world != world) { clear(); world = client.world; }
        received = ticks;
        var ids = new HashSet<Integer>();
        for (var sample : payload.holes()) {
            ids.add(sample.id());
            var old = HOLES.get(sample.id());
            if (old == null || old.target.squaredDistanceTo(sample.center()) > 8 * 8) {
                HOLES.put(sample.id(), new Hole(sample));
                FRAGMENTS.removeIf(f -> f.sample.holeId() == sample.id());
            } else {
                old.from = old.position(0); old.target = sample.center();
                old.sampled = ticks; old.warning = sample.warningTicks();
            }
        }
        HOLES.keySet().retainAll(ids);
        FRAGMENTS.removeIf(f -> !ids.contains(f.sample.holeId()));
        for (var sample : payload.fragments()) if (ids.contains(sample.holeId()) && FRAGMENTS.size() < MAX_FRAGMENTS)
            FRAGMENTS.add(new Fragment(sample, ticks));
    }
    public static float time(float delta) { return ticks + delta; }
}
