package com.cursecycle.network;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.config.TimingSettings;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TimingSettingsRequestPayload(boolean save, int intervalDays, int noticeDays,
                                         int preparationSeconds) implements CustomPayload {
    public static final Id<TimingSettingsRequestPayload> ID = new Id<>(Identifier.of(CurseCycleMod.MOD_ID, "timing_request"));
    public static final PacketCodec<RegistryByteBuf, TimingSettingsRequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, TimingSettingsRequestPayload::save,
            PacketCodecs.VAR_INT, TimingSettingsRequestPayload::intervalDays,
            PacketCodecs.VAR_INT, TimingSettingsRequestPayload::noticeDays,
            PacketCodecs.VAR_INT, TimingSettingsRequestPayload::preparationSeconds,
            TimingSettingsRequestPayload::new);

    public TimingSettings settings() { return new TimingSettings(intervalDays, noticeDays, preparationSeconds); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
