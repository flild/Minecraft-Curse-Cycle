package com.cursecycle.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server-owned stamina and sprint lock; the client never supplies these values. */
public record HeavyBodyStatePayload(long activation, int stamina, int recoveryTicks) implements CustomPayload {
    public static final Id<HeavyBodyStatePayload> ID = new Id<>(Identifier.of("cursecycle", "heavy_body_stamina"));
    public static final PacketCodec<RegistryByteBuf, HeavyBodyStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, HeavyBodyStatePayload::activation,
            PacketCodecs.VAR_INT, HeavyBodyStatePayload::stamina,
            PacketCodecs.VAR_INT, HeavyBodyStatePayload::recoveryTicks, HeavyBodyStatePayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
