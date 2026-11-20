package com.cursecycle.network;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.config.TimingSettings;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TimingSettingsRequestPayload(boolean save, double intervalDays, double noticeDays,
                                         int preparationSeconds) implements CustomPayload {
    public static final Id<TimingSettingsRequestPayload> ID = new Id<>(Identifier.of(CurseCycleMod.MOD_ID, "timing_request_v2"));
    public static final PacketCodec<RegistryByteBuf, TimingSettingsRequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, TimingSettingsRequestPayload::save,
            PacketCodecs.DOUBLE, TimingSettingsRequestPayload::intervalDays,
            PacketCodecs.DOUBLE, TimingSettingsRequestPayload::noticeDays,
            PacketCodecs.VAR_INT, TimingSettingsRequestPayload::preparationSeconds,
            TimingSettingsRequestPayload::new);

    public TimingSettings settings() { return new TimingSettings(intervalDays, noticeDays, preparationSeconds); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
