package com.cursecycle.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C activation context, C2S changes of effective walking intent. */
public record HeavyBodyPayload(long activation, boolean moving) implements CustomPayload {
    public static final Id<HeavyBodyPayload> ID = new Id<>(Identifier.of("cursecycle", "heavy_body_input"));
    public static final PacketCodec<RegistryByteBuf, HeavyBodyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, HeavyBodyPayload::activation,
            PacketCodecs.BOOL, HeavyBodyPayload::moving, HeavyBodyPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
