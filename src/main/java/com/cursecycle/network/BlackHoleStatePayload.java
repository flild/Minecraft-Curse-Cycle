package com.cursecycle.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Full dimension-local snapshot. Fragments are one-shot visual events, never world entities. */
public record BlackHoleStatePayload(String dimension, List<Hole> holes, List<Fragment> fragments) implements CustomPayload {
    public record Hole(int id, Vec3d center, int warningTicks) {}
    public record Fragment(int holeId, BlockPos pos, int blockState) {}
    public static final int MAX_HOLES = 4, MAX_FRAGMENTS = 24;
    public static final Id<BlackHoleStatePayload> ID = new Id<>(Identifier.of("cursecycle", "black_hole_state"));
    public BlackHoleStatePayload {
        holes = List.copyOf(holes); fragments = List.copyOf(fragments);
        if (holes.size() > MAX_HOLES || fragments.size() > MAX_FRAGMENTS) throw new IllegalArgumentException("Black hole snapshot exceeds budget");
    }
    public static final PacketCodec<RegistryByteBuf, BlackHoleStatePayload> CODEC = new PacketCodec<>() {
        @Override public BlackHoleStatePayload decode(RegistryByteBuf buf) {
            var dimension = buf.readString(128);
            int count = count(buf, MAX_HOLES);
            var holes = new ArrayList<Hole>(count);
            for (int i = 0; i < count; i++) {
                int id = buf.readVarInt(); var center = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
                int warning = buf.readVarInt();
                if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)
                        || warning < 0 || warning > 60) throw new IllegalArgumentException("Invalid black hole state");
                holes.add(new Hole(id, center, warning));
            }
            count = count(buf, MAX_FRAGMENTS);
            var fragments = new ArrayList<Fragment>(count);
            for (int i = 0; i < count; i++) fragments.add(new Fragment(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt()));
            return new BlackHoleStatePayload(dimension, holes, fragments);
        }
        @Override public void encode(RegistryByteBuf buf, BlackHoleStatePayload value) {
            buf.writeString(value.dimension, 128); buf.writeVarInt(value.holes.size());
            for (var hole : value.holes) {
                buf.writeVarInt(hole.id); buf.writeDouble(hole.center.x); buf.writeDouble(hole.center.y); buf.writeDouble(hole.center.z);
                buf.writeVarInt(hole.warningTicks);
            }
            buf.writeVarInt(value.fragments.size());
            for (var fragment : value.fragments) {
                buf.writeVarInt(fragment.holeId); buf.writeBlockPos(fragment.pos); buf.writeVarInt(fragment.blockState);
            }
        }
        private int count(RegistryByteBuf buf, int max) {
            int count = buf.readVarInt();
            if (count < 0 || count > max) throw new IllegalArgumentException("Invalid black hole list size");
            return count;
        }
    };
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
