package com.example.hardcorechallenges.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record NotificationPayload(String title, String message, int type) implements CustomPayload {
    public static final CustomPayload.Id<NotificationPayload> ID = new CustomPayload.Id<>(Identifier.of("hardcorechallenges", "notification"));

    // Type: 0 = Info, 1 = Warning, 2 = Critical/Death
    public static final PacketCodec<RegistryByteBuf, NotificationPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotificationPayload::title,
            PacketCodecs.STRING, NotificationPayload::message,
            PacketCodecs.INTEGER, NotificationPayload::type,
            NotificationPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
