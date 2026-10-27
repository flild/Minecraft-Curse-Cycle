package com.cursecycle.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Approved takeoff: x/z are the ordinary sprint delta, y is the final jump velocity. */
public record AutoJumpPayload(int entityId, Vec3d impulse) implements CustomPayload {
    public static final Id<AutoJumpPayload> ID = new Id<>(Identifier.of("cursecycle", "auto_jump"));
    public static final PacketCodec<RegistryByteBuf, AutoJumpPayload> CODEC = PacketCodec.of(
            (value, buf) -> { buf.writeVarInt(value.entityId); buf.writeDouble(value.impulse.x); buf.writeDouble(value.impulse.y); buf.writeDouble(value.impulse.z); },
            buf -> new AutoJumpPayload(buf.readVarInt(), new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble())));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
