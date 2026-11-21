package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;

import java.util.WeakHashMap;
import java.util.Map;

public final class WarrantyExpiredChallenge extends Challenge {
    public static final String ID = "warranty_expired";
    // At 20 TPS: one point per second; hands get two seconds before the first loss.
    public static final int ARMOR_INTERVAL_TICKS = 10;
    public static final int HELD_INTERVAL_TICKS = 10;
    public static final int HELD_GRACE_TICKS = 40;
    public static final int ARMOR_WEAR = 1;
    public static final int HELD_WEAR = 1;

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private final Map<ServerPlayerEntity, Session> sessions = new WeakHashMap<>();

    public WarrantyExpiredChallenge() {
        super(ID, "Тает на глазах",
                "Броня и предметы в руках постепенно теряют прочность.",
                "Надетая броня и предметы в обеих руках постепенно теряют прочность. "
                        + "Для предметов в руках износ начинается через две секунды. ",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    private static final class Session {
        final ServerWorld world;
        final CurseCycleState state;
        final long activation;
        final GameMode mode;
        final ItemStack[] stacks = new ItemStack[SLOTS.length];
        final int[] nextWear = new int[SLOTS.length];
        int lastTick;

        Session(ServerPlayerEntity player, CurseCycleState state, int now) {
            this.state = state; world = player.getServerWorld();
            activation = state.getActivationSerial(); mode = player.interactionManager.getGameMode();
            lastTick = now - 1;
        }
    }

    @Override public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player.isRemoved() || !ChallengeActivity.active(player, ID)) {
            forget(player); return;
        }
        int now = server.getTicks();
        var state = CurseCycleState.getServerState(server);
        var session = sessions.get(player);
        // Do not carry elapsed time across a different player, activation or inactive period.
        if (session == null || session.world != player.getServerWorld()
                || session.state != state || session.activation != state.getActivationSerial()
                || session.mode != player.interactionManager.getGameMode() || now - session.lastTick > 1) {
            session = new Session(player, state, now); sessions.put(player, session);
        }
        if (session.lastTick == now) return;
        session.lastTick = now;
        for (int i = 0; i < SLOTS.length; i++) {
            var slot = SLOTS[i];
            var stack = player.getEquippedStack(slot);
            if (!stack.isDamageable()) {
                session.stacks[i] = null; continue;
            }
            boolean held = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
            if (session.stacks[i] != stack) {
                // Identity is stable while mining, repairing and changing damage/NBT. Those actions
                // must not renew the grace period. Switching to another stack starts its own timer.
                session.stacks[i] = stack;
                session.nextWear[i] = now + (held ? Math.max(0, HELD_GRACE_TICKS)
                        : Math.max(1, ARMOR_INTERVAL_TICKS));
            }
            if (now - session.nextWear[i] < 0) continue;
            session.nextWear[i] = now + Math.max(1, held ? HELD_INTERVAL_TICKS : ARMOR_INTERVAL_TICKS);
            // Vanilla handles Unbreaking, unbreakability, break animation, removal and statistics.
            // One due loss per slot: no accumulated damage after pauses or low TPS.
            stack.damage(Math.max(0, held ? HELD_WEAR : ARMOR_WEAR), player, slot);
        }
    }

    private void forget(ServerPlayerEntity player) {
        sessions.remove(player);
    }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) sessions.clear();
    }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) forget(player);
    }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) sessions.clear(); else forget(player);
    }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) { forget(player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                         ServerPlayerEntity newPlayer, boolean alive) { forget(oldPlayer); }
}
