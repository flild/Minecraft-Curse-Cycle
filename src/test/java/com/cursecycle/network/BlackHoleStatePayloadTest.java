package com.cursecycle.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlackHoleStatePayloadTest {
    @Test void snapshotsAndConsumedBlockEventsRoundTripIncludingEmptyClear() {
        var buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            for (var snapshot : List.of(
                    new BlackHoleStatePayload("minecraft:overworld", List.of(new BlackHoleStatePayload.Hole(17,
                            new Vec3d(-100.125, 62.75, 200.5), 60)),
                            List.of(new BlackHoleStatePayload.Fragment(17, new BlockPos(-101, 61, 201), 123))),
                    new BlackHoleStatePayload("minecraft:the_nether", List.of(), List.of()))) {
                BlackHoleStatePayload.CODEC.encode(buffer, snapshot);
                assertEquals(snapshot, BlackHoleStatePayload.CODEC.decode(buffer));
                assertFalse(buffer.isReadable()); buffer.clear();
            }
        } finally { buffer.release(); }
    }
    @Test void maliciousOrBrokenListSizesAreRejectedBeforeAllocation() {
        for (int size : new int[]{-1, Integer.MAX_VALUE, 5}) {
            var buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
            try {
                buffer.writeString("minecraft:overworld"); buffer.writeVarInt(size);
                assertThrows(IllegalArgumentException.class, () -> BlackHoleStatePayload.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
        assertThrows(IllegalArgumentException.class, () -> new BlackHoleStatePayload("minecraft:overworld",
                Collections.nCopies(5, new BlackHoleStatePayload.Hole(1, Vec3d.ZERO, 0)), List.of()));
    }
    @Test void nonFinitePositionsAndInvalidWarningCannotReachRenderer() {
        for (double x : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0}) {
            var buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
            try {
                buffer.writeString("minecraft:overworld"); buffer.writeVarInt(1); buffer.writeVarInt(1);
                buffer.writeDouble(x); buffer.writeDouble(0); buffer.writeDouble(0);
                buffer.writeVarInt(x == 0 ? 61 : 0);
                assertThrows(IllegalArgumentException.class, () -> BlackHoleStatePayload.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }
}
