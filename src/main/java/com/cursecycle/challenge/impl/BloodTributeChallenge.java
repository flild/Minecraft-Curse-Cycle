package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent active-time obligation, independent of daylight and without a catch-up damage queue. */
public final class BloodTributeChallenge extends Challenge {
    public static final String ID = "blood_tribute";
    // Баланс испытания: время в секундах, урон в HP (2 HP = одно сердце).
    public static final int KILL_INTERVAL_SECONDS = 30;
    public static final int WARNING_SECONDS = 5;
    public static final int PENALTY_INTERVAL_SECONDS = 1;
    public static final float PENALTY_DAMAGE_HP = 2F;

    public static final int INTERVAL = KILL_INTERVAL_SECONDS * 20;
    public static final int WARNING = WARNING_SECONDS * 20;
    public static final int PENALTY_INTERVAL = PENALTY_INTERVAL_SECONDS * 20;
    public static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of("cursecycle", ID));
    private static final Map<MinecraftServer, Clock> CLOCKS = new IdentityHashMap<>();
    private record Clock(CurseCycleState state, long activation, int tick) {}

    public BloodTributeChallenge() {
        super(ID, "Резня",
                "Чтобы жил ты, должен кто-то умереть.",
                "Каждые " + KILL_INTERVAL_SECONDS + " секунд нужно убить моба "
                        + "или убъют уже тебя.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    private static boolean assigned(CurseCycleState state, UUID owner) {
        return state.getActiveChallengeIds().contains(ID) || state.findPersonalActiveChallengeIds(owner).contains(ID);
    }
    private static boolean active(ServerPlayerEntity player) {
        return !player.isRemoved() && ChallengeActivity.active(player, ID)
                && player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player;
    }
    private static NbtCompound fresh() {
        var nbt = new NbtCompound();
        nbt.putInt("remaining", INTERVAL);
        nbt.putInt("penaltyRemaining", 0);
        nbt.putBoolean("warned", false);
        return nbt;
    }
    private static NbtCompound records(CurseCycleState state) {
        var root = state.getChallengeData();
        if (!root.contains(ID)) { root.put(ID, new NbtCompound()); state.markDirty(); }
        return root.getCompound(ID);
    }
    private static NbtCompound record(CurseCycleState state, ServerPlayerEntity player) {
        var records = records(state);
        String key = player.getUuidAsString();
        if (!records.contains(key)) { records.put(key, fresh()); state.markDirty(); }
        return records.getCompound(key);
    }
    /** Only AFTER_DEATH calls this: a totem, surviving damage or historical kill credit is insufficient. */
    public static void died(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof MobEntity) || victim.isAlive()) return;
        var player = DamageHooks.directPlayerAttacker(source);
        if (player == null || !active(player)) return;
        var state = CurseCycleState.getServerState(player.getServer());
        records(state).put(player.getUuidAsString(), fresh());
        state.markDirty();
        player.sendMessage(Text.translatable("text.cursecycle.blood_tribute.paid", KILL_INTERVAL_SECONDS).formatted(Formatting.GREEN), false);
    }
    /** Always registered, including while disabled. Once per actual server tick, not per onTick invocation. */
    public static void tick(MinecraftServer server) {
        var state = CurseCycleState.getServerState(server);
        var clock = new Clock(state, state.getActivationSerial(), server.getTicks());
        if (clock.equals(CLOCKS.put(server, clock))) return;
        var challenge = ChallengeRegistry.get(ID);
        if (challenge == null || !challenge.isEnabled()
                || !state.getActiveChallengeIds().contains(ID)
                && state.getPersonalActiveChallenges().values().stream().noneMatch(ids -> ids.contains(ID))) {
            if (state.getChallengeData().contains(ID)) { state.getChallengeData().remove(ID); state.markDirty(); }
            return;
        }
        for (var player : java.util.List.copyOf(server.getPlayerManager().getPlayerList())) {
            if (!assigned(state, player.getUuid())) { remove(state, player); continue; }
            // Death, excluded modes and disconnect pause the saved phase; world/player replacement never clears it.
            if (!active(player)) continue;
            var rec = record(state, player);
            int remaining = Math.max(0, Math.min(INTERVAL, rec.getInt("remaining")));
            if (remaining > 0) rec.putInt("remaining", --remaining);
            if (remaining > 0 && remaining <= WARNING && !rec.getBoolean("warned")) {
                rec.putBoolean("warned", true);
                player.sendMessage(Text.translatable("text.cursecycle.blood_tribute.warning", (remaining + 19) / 20)
                        .formatted(Formatting.YELLOW), false);
            }
            if (remaining == 0) {
                int penalty = Math.max(0, Math.min(PENALTY_INTERVAL, rec.getInt("penaltyRemaining")));
                if (penalty > 0) --penalty;
                boolean damage = penalty == 0;
                rec.putInt("penaltyRemaining", damage ? PENALTY_INTERVAL : penalty);
                // Save phase before damage callbacks: other challenges can kill or teleport the player.
                state.markDirty();
                if (damage) {
                    if (!rec.getBoolean("overdue")) {
                        rec.putBoolean("overdue", true);
                        player.sendMessage(Text.translatable("text.cursecycle.blood_tribute.overdue")
                                .formatted(Formatting.RED), false);
                    }
                    var source = new DamageSource(player.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE_TYPE));
                    player.damage(source, PENALTY_DAMAGE_HP);
                }
            } else state.markDirty();
        }
    }
    private static void remove(CurseCycleState state, ServerPlayerEntity player) {
        var root = state.getChallengeData();
        if (root.contains(ID) && root.getCompound(ID).contains(player.getUuidAsString())) {
            root.getCompound(ID).remove(player.getUuidAsString()); state.markDirty();
        }
    }
    public static void clear(MinecraftServer server) { CLOCKS.remove(server); }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) {
        if (active(player)) record(CurseCycleState.getServerState(server), player);
    }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        remove(CurseCycleState.getServerState(server), player);
    }
    // The default onPlayerLeave calls onStop, which would erase debt on every disconnect.
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {}
}
