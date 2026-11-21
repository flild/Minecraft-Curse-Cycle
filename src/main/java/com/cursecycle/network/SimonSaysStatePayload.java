package com.cursecycle.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server clock sample. No client acknowledgement can complete a command. */
public record SimonSaysStatePayload(int phase, String action, int remaining, int duration) implements CustomPayload {
    public static final int HIDDEN = 0, WAITING = 1, COMMAND = 2, SUCCESS = 3, FAILURE = 4;
    public static final Id<SimonSaysStatePayload> ID = new Id<>(Identifier.of("cursecycle", "simon_says_state"));
    public static final PacketCodec<RegistryByteBuf, SimonSaysStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SimonSaysStatePayload::phase,
            PacketCodecs.STRING, SimonSaysStatePayload::action,
            PacketCodecs.VAR_INT, SimonSaysStatePayload::remaining,
            PacketCodecs.VAR_INT, SimonSaysStatePayload::duration, SimonSaysStatePayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
