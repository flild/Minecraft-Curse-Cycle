package com.cursecycle.client;

import com.cursecycle.challenge.impl.AutoJumpChallenge;
import com.cursecycle.challenge.impl.HeavyBodyChallenge;
import com.cursecycle.challenge.impl.NoJumpingChallenge;
import com.cursecycle.network.AutoJumpPayload;
import net.minecraft.entity.player.PlayerEntity;

public final class AutoJumpMotion {
    private AutoJumpMotion() {}

    public static void receive(PlayerEntity player, AutoJumpPayload payload) {
        if (player == null || player.getId() != payload.entityId() || !player.isAlive() || player.isRemoved()
                || player.isCreative() || player.isSpectator() || !ClientDataHolder.isSynced()
                || !ClientDataHolder.getActiveChallengeIds().contains(AutoJumpChallenge.ID)
                || !HeavyBodyChallenge.onFoot(player) || NoJumpingChallenge.isNoJumpingActiveForPlayer(player)) return;
        var motion = player.getVelocity();
        // A manual jump may already be ascending by the time the server approval arrives.
        // Do not restart that ascent, nor add sprint twice. Ground is cleared before input sampling.
        if (!player.isOnGround() && motion.y > 0) return;
        var impulse = payload.impulse();
        player.setVelocity(motion.x + impulse.x, impulse.y, motion.z + impulse.z);
        player.setOnGround(false);
    }
}
