package com.cursecycle.network;

import com.cursecycle.CurseCycleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S release/cancel; S2C start/end. Sequence -1 is the activation context, sent with sync_state. */
public record MovementMemoryPayload(long activation, long sequence, boolean holding) implements CustomPayload {
    public static final Id<MovementMemoryPayload> ID = new Id<>(Identifier.of(CurseCycleMod.MOD_ID, "movement_memory"));
    public static final PacketCodec<RegistryByteBuf, MovementMemoryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, MovementMemoryPayload::activation,
            PacketCodecs.VAR_LONG, MovementMemoryPayload::sequence,
            PacketCodecs.BOOL, MovementMemoryPayload::holding, MovementMemoryPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
