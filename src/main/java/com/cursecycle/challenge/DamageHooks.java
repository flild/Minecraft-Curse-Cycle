package com.cursecycle.challenge;

import com.cursecycle.state.CurseCycleState;
import com.cursecycle.challenge.impl.PacifistChallenge;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashSet;

public final class DamageHooks {
    private static final ThreadLocal<ArrayDeque<DamageReceipt>> pending = ThreadLocal.withInitial(ArrayDeque::new);
    private DamageHooks() {}
    /** Last direct melee/projectile source only; historical kill credit, pets and traps do not qualify. */
    public static ServerPlayerEntity directPlayerAttacker(DamageSource source) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return null;
        boolean melee = source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK) && source.getSource() == player;
        boolean projectile = source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_PROJECTILE)
                && source.getSource() instanceof net.minecraft.entity.projectile.ProjectileEntity;
        return melee || projectile ? player : null;
    }
    public static boolean needed(ServerPlayerEntity player) {
        return ChallengeActivity.active(player, "damage_slot_drop") || ChallengeActivity.active(player, "damage_teleport");
    }
    public static boolean needed(LivingEntity target, DamageSource source) {
        return (target instanceof ServerPlayerEntity player && needed(player))
                || PacifistChallenge.attacker(target, source) != null;
    }
    public static DamageReceipt begin(LivingEntity target, DamageSource source) {
        var receipt = new DamageReceipt(target, source);
        pending.get().push(receipt);
        return receipt;
    }
    /** The attack call site owns a receipt before entering LivingEntity.damage. Borrow it once. */
    public static DamageReceipt beginAttack(LivingEntity target, DamageSource source) {
        var receipt = begin(target, source);
        receipt.awaitingAttackDamage = true;
        return receipt;
    }
    public static boolean claimAttackDamage(LivingEntity target, DamageSource source) {
        var receipt = pending.get().peek();
        if (receipt == null || !receipt.awaitingAttackDamage || receipt.target != target || receipt.source != source)
            return false;
        receipt.awaitingAttackDamage = false;
        return true;
    }
    /** Nested damage of a recorded target must not confirm its caller's result. */
    public static boolean isPending(LivingEntity target) {
        var receipt = pending.get().peek();
        return receipt != null && receipt.target == target;
    }
    public static void reduced(LivingEntity target, float health, float absorption) {
        var receipt = pending.get().peek();
        if (receipt != null && receipt.target == target) receipt.recordReduction(health, absorption);
    }
    public static void finish(DamageReceipt receipt, boolean completed) {
        var stack = pending.get();
        if (stack.pop() != receipt) throw new IllegalStateException("Unbalanced damage receipt");
        if (stack.isEmpty()) pending.remove();
        if (!completed || !receipt.accepted) return;
        if (receipt.player != null && receipt.player.isAlive()) incoming(receipt);
        PacifistChallenge.reflect(receipt);
    }
    private static void incoming(DamageReceipt receipt) {
        var server = receipt.player.getServer();
        var state = CurseCycleState.getServerState(server);
        var ids = new LinkedHashSet<>(state.getActiveChallengeIds());
        ids.addAll(state.findPersonalActiveChallengeIds(receipt.player.getUuid()));
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
