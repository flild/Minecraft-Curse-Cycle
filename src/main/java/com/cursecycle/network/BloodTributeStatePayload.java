package com.cursecycle.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Snapshot of the saved server timer; zero means the obligation is overdue. */
public record BloodTributeStatePayload(boolean active, int remaining, int duration) implements CustomPayload {
    public static final Id<BloodTributeStatePayload> ID = new Id<>(Identifier.of("cursecycle", "blood_tribute_state"));
    public static final PacketCodec<RegistryByteBuf, BloodTributeStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, BloodTributeStatePayload::active,
            PacketCodecs.VAR_INT, BloodTributeStatePayload::remaining,
            PacketCodecs.VAR_INT, BloodTributeStatePayload::duration, BloodTributeStatePayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
