package com.cursecycle.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C authoritative phase sample and acknowledgement. Session zero clears only this mechanic. */
public record HeartbeatStatePayload(long activation, long session, long age, long target, boolean accepted) implements CustomPayload {
    public static final Id<HeartbeatStatePayload> ID = new Id<>(Identifier.of("cursecycle", "heartbeat_state"));
    public static final PacketCodec<RegistryByteBuf, HeartbeatStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, HeartbeatStatePayload::activation,
            PacketCodecs.VAR_LONG, HeartbeatStatePayload::session,
            PacketCodecs.VAR_LONG, HeartbeatStatePayload::age,
            PacketCodecs.VAR_LONG, HeartbeatStatePayload::target,
            PacketCodecs.BOOL, HeartbeatStatePayload::accepted, HeartbeatStatePayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
