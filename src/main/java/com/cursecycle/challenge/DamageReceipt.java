package com.cursecycle.challenge;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** One invocation; reductions are observed before totem restoration. */
public final class DamageReceipt {
    public final ServerPlayerEntity player;
    public final DamageSource source;
    public final ServerWorld world;
    public final Vec3d position;
    boolean accepted;
    DamageReceipt(ServerPlayerEntity player, DamageSource source) {
        this.player = player;
        this.source = source;
        world = player.getServerWorld();
        position = player.getPos();
    }
}
