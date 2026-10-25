package com.cursecycle.challenge;

import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashSet;

public final class DamageHooks {
    private static final ThreadLocal<ArrayDeque<DamageReceipt>> pending = ThreadLocal.withInitial(ArrayDeque::new);
    private DamageHooks() {}
    public static boolean needed(ServerPlayerEntity player) {
        return ChallengeActivity.active(player, "damage_slot_drop") || ChallengeActivity.active(player, "damage_teleport");
    }
    public static DamageReceipt begin(ServerPlayerEntity player, DamageSource source) {
        var receipt = new DamageReceipt(player, source);
        pending.get().push(receipt);
        return receipt;
    }
    public static void accepted(PlayerEntity player) {
        var receipt = pending.get().peek();
        if (receipt != null && receipt.player == player) receipt.accepted = true;
    }
    public static void finish(DamageReceipt receipt, boolean completed) {
        var stack = pending.get();
        if (stack.pop() != receipt) throw new IllegalStateException("Unbalanced damage receipt");
        if (stack.isEmpty()) pending.remove();
        if (!completed || !receipt.accepted || !receipt.player.isAlive()) return;
        var server = receipt.player.getServer();
        var state = CurseCycleState.getServerState(server);
        var ids = new LinkedHashSet<>(state.getActiveChallengeIds());
        ids.addAll(state.getPersonalActiveChallengeIds(receipt.player.getUuid()));
        // The drop belongs to the original location, before any reaction teleports the player.
        ids.stream().sorted(Comparator.comparingInt(DamageHooks::priority)).forEach(id -> {
            var challenge = ChallengeRegistry.get(id);
            if (challenge != null && ChallengeActivity.active(receipt.player, id))
                challenge.onPlayerDamageApplied(server, receipt.player, receipt);
        });
    }
    private static int priority(String id) {
        return id.equals("damage_slot_drop") ? 0 : id.equals("damage_teleport") ? 1 : 2;
    }
}
