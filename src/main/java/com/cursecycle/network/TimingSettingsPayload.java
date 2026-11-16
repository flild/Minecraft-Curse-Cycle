package com.cursecycle.network;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.config.TimingSettings;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TimingSettingsPayload(int intervalDays, int noticeDays, int preparationSeconds,
                                    boolean editable, int result) implements CustomPayload {
    public static final int SNAPSHOT = 0, SAVED = 1, DENIED = 2, INVALID = 3, SAVE_FAILED = 4;
    public static final Id<TimingSettingsPayload> ID = new Id<>(Identifier.of(CurseCycleMod.MOD_ID, "timing_settings"));
    public static final PacketCodec<RegistryByteBuf, TimingSettingsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, TimingSettingsPayload::intervalDays,
            PacketCodecs.VAR_INT, TimingSettingsPayload::noticeDays,
            PacketCodecs.VAR_INT, TimingSettingsPayload::preparationSeconds,
            PacketCodecs.BOOL, TimingSettingsPayload::editable,
            PacketCodecs.VAR_INT, TimingSettingsPayload::result,
            TimingSettingsPayload::new);

    public TimingSettings settings() { return new TimingSettings(intervalDays, noticeDays, preparationSeconds); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
