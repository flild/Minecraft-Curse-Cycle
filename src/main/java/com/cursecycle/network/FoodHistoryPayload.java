package com.cursecycle.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.List;

public record FoodHistoryPayload(List<String> ids) implements CustomPayload {
    public static final Id<FoodHistoryPayload> ID = new Id<>(Identifier.of("cursecycle", "food_history"));
    public static final PacketCodec<RegistryByteBuf, FoodHistoryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.collect(PacketCodecs.toList()), FoodHistoryPayload::ids, FoodHistoryPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
