package com.cursecycle.client;

import com.cursecycle.network.MeteorShowerStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Bounded snapshots, interpolated only for rendering. No client world edits. */
public final class MeteorShowerVisuals {
    public static final Map<Integer, Strike> STRIKES = new LinkedHashMap<>();
    private static ClientWorld world;
    private static int ticks, received;
    public static final class Strike {
        public MeteorShowerStatePayload.Strike sample;
        private Vec3d from;
        private int sampled;
        Strike(MeteorShowerStatePayload.Strike sample) { this.sample = sample; from = sample.position(); sampled = ticks; }
        public float elapsed(float delta) { return ticks + delta - sampled; }
        public float age(float delta) { return sample.age() + elapsed(delta); }
        public float impactAge(float delta) { return sample.impactAge() < 0 ? -1 : sample.impactAge() + elapsed(delta); }
        public Vec3d position(float delta) { return from.lerp(sample.position(), Math.min(1, elapsed(delta) / 2.0)); }
    }
    private MeteorShowerVisuals() {}
    public static void clear() { STRIKES.clear(); world = null; ticks = received = 0; }
    public static void tick(MinecraftClient client) {
        if (client.world != world) { clear(); world = client.world; }
        ticks++;
        if (ticks - received > 20) STRIKES.clear();
    }
    public static void receive(MinecraftClient client, MeteorShowerStatePayload payload) {
        if (client.world == null || !client.world.getRegistryKey().getValue().toString().equals(payload.dimension())) return;
        if (client.world != world) { clear(); world = client.world; }
        received = ticks;
        var ids = new HashSet<Integer>();
        for (var sample : payload.strikes()) {
            ids.add(sample.id()); var old = STRIKES.get(sample.id());
            if (old == null || old.sample.impactAge() < 0 && sample.impactAge() >= 0)
                STRIKES.put(sample.id(), new Strike(sample));
            else { old.from = old.position(0); old.sample = sample; old.sampled = ticks; }
        }
        STRIKES.keySet().retainAll(ids);
    }
    public static float time(float delta) { return ticks + delta; }
}
