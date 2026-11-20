package com.cursecycle.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MeteorShowerStatePayloadTest {
    private static MeteorShowerStatePayload.Strike strike(int id) {
        return new MeteorShowerStatePayload.Strike(id, new Vec3d(-1.5, -30, 64), new Vec3d(-1.5, -6, 64), true, -27, -1, 8);
    }
    @Test void warningFlightEruptionAndEmptySnapshotRoundTrip() {
        var b = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            for (var p : List.of(new MeteorShowerStatePayload("minecraft:overworld", List.of(strike(1),
                    new MeteorShowerStatePayload.Strike(2, Vec3d.ZERO, Vec3d.ZERO, true, 75, 15, 2.5F))),
                    new MeteorShowerStatePayload("minecraft:the_nether", List.of()))) {
                MeteorShowerStatePayload.CODEC.encode(b, p); assertEquals(p, MeteorShowerStatePayload.CODEC.decode(b));
                assertFalse(b.isReadable()); b.clear();
            }
        } finally { b.release(); }
    }
    @Test void rejectsOversizedOrNegativeCountsBeforeAllocation() {
        for (int count : new int[]{-1, 41, Integer.MAX_VALUE}) {
            var b = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
            try { b.writeString("minecraft:overworld"); b.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> MeteorShowerStatePayload.CODEC.decode(b));
            } finally { b.release(); }
        }
        assertThrows(IllegalArgumentException.class, () -> new MeteorShowerStatePayload("a", Collections.nCopies(41, strike(1))));
        assertThrows(IllegalArgumentException.class, () -> new MeteorShowerStatePayload("a", List.of(strike(1), strike(1))));
    }
    @Test void rejectsNonFiniteCoordinatesAndInvalidAnimationValues() {
        for (double x : new double[]{Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new MeteorShowerStatePayload.Strike(1, new Vec3d(x, 0, 0), Vec3d.ZERO, false, 0, -1, 8));
        for (float height : new float[]{Float.NaN, -1, 25})
            assertThrows(IllegalArgumentException.class, () -> new MeteorShowerStatePayload.Strike(1, Vec3d.ZERO, Vec3d.ZERO, true, 0, -1, height));
        assertThrows(IllegalArgumentException.class, () -> new MeteorShowerStatePayload.Strike(1, Vec3d.ZERO, Vec3d.ZERO, true, -31, -1, 8));
        assertThrows(IllegalArgumentException.class, () -> new MeteorShowerStatePayload.Strike(1, Vec3d.ZERO, Vec3d.ZERO, true, 0, 41, 8));
    }
    @Test void fullCapacitySnapshotIsImmutable() {
        var list = new ArrayList<MeteorShowerStatePayload.Strike>(); for (int i = 0; i < 40; i++) list.add(strike(i));
        var p = new MeteorShowerStatePayload("minecraft:overworld", list); list.clear();
        assertEquals(40, p.strikes().size()); assertThrows(UnsupportedOperationException.class, () -> p.strikes().clear());
    }
}
