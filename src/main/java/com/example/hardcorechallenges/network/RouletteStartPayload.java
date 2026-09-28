package com.example.hardcorechallenges.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record RouletteStartPayload(List<String> selectedChallengeNames, List<String> candidatesNames) implements CustomPayload {
    public static final CustomPayload.Id<RouletteStartPayload> ID = new CustomPayload.Id<>(Identifier.of("hardcorechallenges", "roulette_start"));

    public static final PacketCodec<RegistryByteBuf, RouletteStartPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING), RouletteStartPayload::selectedChallengeNames,
            PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING), RouletteStartPayload::candidatesNames,
            RouletteStartPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
