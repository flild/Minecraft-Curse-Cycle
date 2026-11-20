package com.cursecycle.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimingSettingsPayloadTest {
    @Test
    void fractionalDaysSurviveRequestAndSnapshotCodecs() {
        var buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            var request = new TimingSettingsRequestPayload(true, 2.5, 0.5, 60);
            TimingSettingsRequestPayload.CODEC.encode(buffer, request);
            assertEquals(request, TimingSettingsRequestPayload.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
            buffer.clear();
            var snapshot = new TimingSettingsPayload(2.5, 0.5, 60, true, TimingSettingsPayload.SAVED);
            TimingSettingsPayload.CODEC.encode(buffer, snapshot);
            assertEquals(snapshot, TimingSettingsPayload.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
            assertEquals(request.settings(), snapshot.settings());
        } finally {
            buffer.release();
        }
    }
}
