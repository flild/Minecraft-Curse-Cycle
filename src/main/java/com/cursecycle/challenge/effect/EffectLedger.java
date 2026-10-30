package com.cursecycle.challenge.effect;

import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tracks only effect types to which a challenge contributes. External chains keep ticking. */
public final class EffectLedger {
    private final ServerPlayerEntity player;
    private final Map<RegistryEntry<StatusEffect>, Entry> entries = new HashMap<>();
    public boolean publishing;

    public EffectLedger(ServerPlayerEntity player) { this.player = player; }

    private static StatusEffectInstance copy(StatusEffectInstance effect) {
        return effect == null ? null : StatusEffectInstance.fromNbt((NbtCompound) effect.writeNbt());
    }

    public static EffectLedger of(ServerPlayerEntity player) {
        return ((EffectLedgerOwner) player).cursecycle$effects();
    }

    public boolean owns(String owner, RegistryEntry<StatusEffect> type) {
        Entry entry = entries.get(type);
        return entry != null && entry.owned.containsKey(owner);
    }

    public boolean exclusivelyOwns(String owner, RegistryEntry<StatusEffect> type) {
        Entry entry = entries.get(type);
        return entry != null && entry.external == null && entry.owned.size() == 1
                && entry.owned.containsKey(owner);
    }

    /** Adopt a just-created vanilla omen, without replacing it or restarting its timer. */
    public void adopt(String owner, RegistryEntry<StatusEffect> type) {
        StatusEffectInstance effect = player.getStatusEffect(type);
        if (effect == null || entries.containsKey(type)) return;
        Entry entry = new Entry(null);
        entry.vanillaTimed = true;
        entry.owned.put(owner, new Contribution(
                CurseCycleState.getServerState(player.getServer()).getActivationSerial(), copy(effect)));
        entries.put(type, entry);
    }

    /** Vanilla consumed the entire visible/hidden omen chain; do not publish it again. */
    public void consumed(RegistryEntry<StatusEffect> type) { entries.remove(type); }

    public void maintain(String owner, RegistryEntry<StatusEffect> type, int amplifier, int duration, int threshold) {
        if (!ChallengeActivity.active(player, owner)) { remove(owner); return; }
        Entry entry = entries.computeIfAbsent(type, key -> new Entry(copy(player.getStatusEffect(type))));
        Contribution current = entry.owned.get(owner);
        long activation = CurseCycleState.getServerState(player.getServer()).getActivationSerial();
        if (current == null || current.activation != activation || current.effect.getDuration() <= threshold) {
            entry.owned.put(owner, new Contribution(activation, new StatusEffectInstance(type, duration, amplifier)));
            publish(type, entry);
        } else {
            StatusEffectInstance visible = player.getStatusEffect(type);
            if (visible == null || visible.getAmplifier() < amplifier) publish(type, entry);
        }
    }

    public void remove(String owner) {
        for (var it = entries.entrySet().iterator(); it.hasNext();) {
            var pair = it.next();
            if (pair.getValue().owned.remove(owner) != null) publish(pair.getKey(), pair.getValue());
            if (pair.getValue().owned.isEmpty()) it.remove();
        }
    }

    public void externalAdd(StatusEffectInstance effect, boolean replace) {
        if (publishing || !player.canHaveStatusEffect(effect)) return;
        Entry entry = entries.get(effect.getEffectType());
        if (entry == null) return;
        if (replace || entry.external == null) entry.external = copy(effect);
        else entry.external.upgrade(copy(effect));
    }

    public void externalRemove(RegistryEntry<StatusEffect> type) {
        if (!publishing) entries.remove(type);
    }

    public void externalClear() { if (!publishing) entries.clear(); }

    public void tick() {
        for (var pair : entries.entrySet()) {
            Entry entry = pair.getValue();
            if (entry.external != null) {
                ((EffectDurationAccess) entry.external).cursecycle$advanceDuration();
                // Vanilla upgrades a still-live hidden effect when the visible one expires.
                entry.external = liveChain(entry.external);
            }
            if (!entry.vanillaTimed)
                for (var own : entry.owned.values()) ((EffectDurationAccess) own.effect).cursecycle$advanceDuration();
        }
        reconcile();
    }

    private static StatusEffectInstance liveChain(StatusEffectInstance effect) {
        return effect == null ? null : ((EffectDurationAccess) effect).cursecycle$liveChain();
    }

    public void reconcile() {
        long activation = CurseCycleState.getServerState(player.getServer()).getActivationSerial();
        for (var it = entries.entrySet().iterator(); it.hasNext();) {
            var pair = it.next();
            StatusEffectInstance visible = player.getStatusEffect(pair.getKey());
            boolean changed = pair.getValue().owned.entrySet().removeIf(own ->
                    own.getValue().activation != activation || !ChallengeActivity.active(player, own.getKey())
                            || (pair.getValue().vanillaTimed && visible == null)
                            || (!own.getValue().effect.isInfinite() && own.getValue().effect.getDuration() <= 0));
            int ownedAmplifier = pair.getValue().owned.values().stream()
                    .mapToInt(own -> own.effect.getAmplifier()).max().orElse(-1);
            // Live player copies in vanilla do not copy hidden chains. Rebuild only when needed.
            if (changed || (ownedAmplifier >= 0 && (visible == null || visible.getAmplifier() < ownedAmplifier)))
                publish(pair.getKey(), pair.getValue());
            if (pair.getValue().owned.isEmpty()) it.remove();
        }
    }

    private void publish(RegistryEntry<StatusEffect> type, Entry entry) {
        StatusEffectInstance combined = copy(entry.external);
        for (var own : entry.owned.values()) {
            if (combined == null) combined = copy(own.effect);
            else combined.upgrade(copy(own.effect));
        }
        publishing = true;
        try {
            // setStatusEffect replaces the complete current/hidden chain and sends normal updates.
            if (combined == null) player.removeStatusEffect(type);
            else player.setStatusEffect(combined, null);
        } finally { publishing = false; }
    }

    public void write(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (var pair : entries.entrySet()) {
            NbtCompound tag = new NbtCompound();
            tag.putBoolean("vanillaTimed", pair.getValue().vanillaTimed);
            if (pair.getValue().external != null) tag.put("external", pair.getValue().external.writeNbt());
            NbtCompound owners = new NbtCompound();
            pair.getValue().owned.forEach((id, contribution) -> {
                NbtCompound own = new NbtCompound();
                own.putLong("activation", contribution.activation);
                own.put("effect", contribution.effect.writeNbt());
                owners.put(id, own);
            });
            tag.put("owners", owners);
            list.add(tag);
        }
        nbt.put("cursecycle_effect_contributions", list);
    }

    public void read(NbtCompound nbt) {
        entries.clear();
        NbtList list = nbt.getList("cursecycle_effect_contributions", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            Entry entry = new Entry(tag.contains("external", NbtElement.COMPOUND_TYPE)
                    ? StatusEffectInstance.fromNbt(tag.getCompound("external")) : null);
            entry.vanillaTimed = tag.getBoolean("vanillaTimed");
            RegistryEntry<StatusEffect> type = null;
            NbtCompound owners = tag.getCompound("owners");
            for (String id : owners.getKeys()) {
                NbtCompound own = owners.getCompound(id);
                StatusEffectInstance effect = StatusEffectInstance.fromNbt(own.getCompound("effect"));
                if (effect != null) {
                    type = effect.getEffectType();
                    entry.owned.put(id, new Contribution(own.getLong("activation"), effect));
                }
            }
            if (type != null) entries.put(type, entry);
        }
    }

    private static final class Entry {
        StatusEffectInstance external;
        // Adopted omens are consumed by vanilla itself, possibly later in their creation tick.
        // A parallel ledger clock could expire one tick early and cancel the raid trigger.
        boolean vanillaTimed;
        final Map<String, Contribution> owned = new LinkedHashMap<>();
        Entry(StatusEffectInstance external) { this.external = external; }
    }
    private record Contribution(long activation, StatusEffectInstance effect) {}
}
