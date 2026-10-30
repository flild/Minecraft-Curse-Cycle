package com.cursecycle.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S physical press, bound to this connection's server-issued phase. */
public record HeartbeatPressPayload(long activation, long session) implements CustomPayload {
    public static final Id<HeartbeatPressPayload> ID = new Id<>(Identifier.of("cursecycle", "heartbeat_press"));
    public static final PacketCodec<RegistryByteBuf, HeartbeatPressPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, HeartbeatPressPayload::activation,
            PacketCodecs.VAR_LONG, HeartbeatPressPayload::session, HeartbeatPressPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
