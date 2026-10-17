package com.cursecycle.network;

import com.cursecycle.CurseCycleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public class TriggerWheelPayload implements CustomPayload {
    public static final Id<TriggerWheelPayload> ID = new Id<>(Identifier.of(CurseCycleMod.MOD_ID, "trigger_wheel"));

    public static final PacketCodec<RegistryByteBuf, TriggerWheelPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.STRING.collect(PacketCodecs.toList()), TriggerWheelPayload::candidateIds,
        PacketCodecs.STRING, TriggerWheelPayload::winnerId,
        TriggerWheelPayload::new
    );

    private final List<String> candidateIds;
    private final String winnerId;

    public TriggerWheelPayload(List<String> candidateIds, String winnerId) {
        this.candidateIds = candidateIds;
        this.winnerId = winnerId;
    }

    public List<String> candidateIds() { return candidateIds; }
    public String winnerId() { return winnerId; }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
