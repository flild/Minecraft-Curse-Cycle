package com.example.hardcorechallenges.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SyncStatePayload(List<String> activeChallengeNames, long totalTicks, String gameMode) implements CustomPayload {
    public static final CustomPayload.Id<SyncStatePayload> ID = new CustomPayload.Id<>(Identifier.of("hardcorechallenges", "sync_state"));

    public static final PacketCodec<RegistryByteBuf, SyncStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING), SyncStatePayload::activeChallengeNames,
            PacketCodecs.VAR_LONG, SyncStatePayload::totalTicks,
            PacketCodecs.STRING, SyncStatePayload::gameMode,
            SyncStatePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
