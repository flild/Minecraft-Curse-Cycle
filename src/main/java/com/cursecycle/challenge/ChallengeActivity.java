package com.cursecycle.challenge;

import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-side applicability shared by the new mechanics. */
public final class ChallengeActivity {
    private ChallengeActivity() {}

    public static boolean active(PlayerEntity player, String id) {
        if (!(player instanceof ServerPlayerEntity p) || !p.isAlive()
                || p.isCreative() || p.isSpectator() || p.getServer() == null) return false;
        var challenge = ChallengeRegistry.get(id);
        if (challenge == null || !challenge.isEnabled()) return false;
        var state = CurseCycleState.getServerState(p.getServer());
        return state.getActiveChallengeIds().contains(id)
                || state.getPersonalActiveChallengeIds(p.getUuid()).contains(id);
    }
}
