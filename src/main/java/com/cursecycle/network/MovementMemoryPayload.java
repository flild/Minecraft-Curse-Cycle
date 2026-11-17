package com.cursecycle.network;

import com.cursecycle.CurseCycleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Per-direction C2S release/cancel and S2C start/end. Sequence -1 supplies the activation context. */
public record MovementMemoryPayload(long activation, long sequence, int direction, boolean holding) implements CustomPayload {
    public static final int FORWARD = 0, BACK = 1, LEFT = 2, RIGHT = 3, DIRECTION_COUNT = 4;

    public MovementMemoryPayload(long activation, long sequence, boolean holding) {
        this(activation, sequence, FORWARD, holding);
    }
    public static final Id<MovementMemoryPayload> ID = new Id<>(Identifier.of(CurseCycleMod.MOD_ID, "movement_memory"));
    public static final PacketCodec<RegistryByteBuf, MovementMemoryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, MovementMemoryPayload::activation,
            PacketCodecs.VAR_LONG, MovementMemoryPayload::sequence,
            PacketCodecs.VAR_INT, MovementMemoryPayload::direction,
            PacketCodecs.BOOL, MovementMemoryPayload::holding, MovementMemoryPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
