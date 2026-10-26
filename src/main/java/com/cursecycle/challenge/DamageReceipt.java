package com.cursecycle.challenge;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** One invocation; reductions are observed before totem restoration. */
public final class DamageReceipt {
    public final LivingEntity target;
    /** Non-null only for server-player targets; used by incoming-damage reactions. */
    public final ServerPlayerEntity player;
    public final DamageSource source;
    public final ServerWorld world;
    public final Vec3d position;
    boolean accepted;
    DamageReceipt(LivingEntity target, DamageSource source) {
        this.target = target;
        this.player = target instanceof ServerPlayerEntity p ? p : null;
        this.source = source;
        world = (ServerWorld) target.getWorld();
        position = target.getPos();
    }
    public boolean wasApplied() { return accepted; }
}
