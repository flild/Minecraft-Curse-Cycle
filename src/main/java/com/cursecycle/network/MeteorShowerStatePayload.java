package com.cursecycle.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Full dimension-local visual snapshot. Damage and terrain remain server-authoritative. */
public record MeteorShowerStatePayload(String dimension, List<Strike> strikes) implements CustomPayload {
    public static final int MAX_STRIKES = 40;
    public record Strike(int id, Vec3d target, Vec3d position, boolean geyser, int age, int impactAge, float height) {
        public Strike {
            if (!finite(target) || !finite(position) || age < -30 || age > 180 || impactAge < -1 || impactAge > 40
                    || !Float.isFinite(height) || height < 0 || height > 24) throw new IllegalArgumentException("Invalid meteor state");
        }
        private static boolean finite(Vec3d v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    }
    public MeteorShowerStatePayload {
        strikes = List.copyOf(strikes);
        if (dimension.length() > 128 || strikes.size() > MAX_STRIKES) throw new IllegalArgumentException("Meteor snapshot exceeds budget");
        var ids = new HashSet<Integer>();
        for (var strike : strikes) if (!ids.add(strike.id)) throw new IllegalArgumentException("Duplicate meteor id");
    }
    public static final Id<MeteorShowerStatePayload> ID = new Id<>(Identifier.of("cursecycle", "meteor_shower_state"));
    public static final PacketCodec<RegistryByteBuf, MeteorShowerStatePayload> CODEC = new PacketCodec<>() {
        @Override public MeteorShowerStatePayload decode(RegistryByteBuf b) {
            String dimension = b.readString(128); int count = b.readVarInt();
            if (count < 0 || count > MAX_STRIKES) throw new IllegalArgumentException("Invalid meteor count");
            var strikes = new ArrayList<Strike>(count);
            for (int i = 0; i < count; i++) strikes.add(new Strike(b.readVarInt(), read(b), read(b), b.readBoolean(),
                    b.readVarInt(), b.readVarInt(), b.readFloat()));
            return new MeteorShowerStatePayload(dimension, strikes);
        }
        @Override public void encode(RegistryByteBuf b, MeteorShowerStatePayload p) {
            b.writeString(p.dimension, 128); b.writeVarInt(p.strikes.size());
            for (var s : p.strikes) {
                b.writeVarInt(s.id); write(b, s.target); write(b, s.position); b.writeBoolean(s.geyser);
                b.writeVarInt(s.age); b.writeVarInt(s.impactAge); b.writeFloat(s.height);
            }
        }
        private Vec3d read(RegistryByteBuf b) { return new Vec3d(b.readDouble(), b.readDouble(), b.readDouble()); }
        private void write(RegistryByteBuf b, Vec3d v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
