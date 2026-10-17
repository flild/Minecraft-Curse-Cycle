package com.cursecycle.network;

import com.cursecycle.CurseCycleMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class CurseCycleSyncPayload implements CustomPayload {
    public static final Id<CurseCycleSyncPayload> ID = new Id<>(Identifier.of(CurseCycleMod.MOD_ID, "sync_state"));

    public static final PacketCodec<RegistryByteBuf, CurseCycleSyncPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.VAR_LONG, CurseCycleSyncPayload::currentDay,
        PacketCodecs.VAR_LONG, CurseCycleSyncPayload::ticksUntilNext,
        PacketCodecs.STRING.collect(PacketCodecs.toList()), CurseCycleSyncPayload::activeChallengeIds,
        PacketCodecs.STRING, CurseCycleSyncPayload::nextChallengeId,
        PacketCodecs.STRING.collect(PacketCodecs.toList()), CurseCycleSyncPayload::historyChallengeIds,
        CurseCycleSyncPayload::new
    );

    private final long currentDay;
    private final long ticksUntilNext;
    private final List<String> activeChallengeIds;
    private final String nextChallengeId;
    private final List<String> historyChallengeIds;

    public CurseCycleSyncPayload(long currentDay, long ticksUntilNext, List<String> activeChallengeIds, String nextChallengeId, List<String> historyChallengeIds) {
        this.currentDay = currentDay;
        this.ticksUntilNext = ticksUntilNext;
        this.activeChallengeIds = activeChallengeIds;
        this.nextChallengeId = nextChallengeId != null ? nextChallengeId : "";
        this.historyChallengeIds = historyChallengeIds;
    }

    public long currentDay() { return currentDay; }
    public long ticksUntilNext() { return ticksUntilNext; }
    public List<String> activeChallengeIds() { return activeChallengeIds; }
    public String nextChallengeId() { return nextChallengeId; }
    public List<String> historyChallengeIds() { return historyChallengeIds; }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
