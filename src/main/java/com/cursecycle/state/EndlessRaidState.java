package com.cursecycle.state;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.EndlessRaidChallenge;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.village.raid.Raid;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

/** Survives beginActivation, including a transition on an empty server. Vanilla saves raid progress. */
public final class EndlessRaidState extends PersistentState {
    public static final String KEY = "cursecycle_endless_raid";
    private final List<OwnedRaid> raids = new ArrayList<>();
    private record OwnedRaid(RegistryKey<World> dimension, int id, long activation) { }

    public static EndlessRaidState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                new Type<>(EndlessRaidState::new, EndlessRaidState::fromNbt, null), KEY);
    }

    public void record(ServerWorld world, Raid raid) {
        var owned = new OwnedRaid(world.getRegistryKey(), raid.getRaidId(),
                CurseCycleState.getServerState(world.getServer()).getActivationSerial());
        if (!raids.contains(owned)) { raids.add(owned); markDirty(); }
    }

    public void reconcile(ServerWorld world) {
        if (raids.isEmpty()) return;
        var cycle = CurseCycleState.getServerState(world.getServer());
        var challenge = ChallengeRegistry.get(EndlessRaidChallenge.ID);
        boolean active = challenge != null && challenge.isEnabled()
                && (cycle.getActiveChallengeIds().contains(EndlessRaidChallenge.ID)
                    || cycle.getPersonalActiveChallenges().values().stream()
                            .anyMatch(ids -> ids.contains(EndlessRaidChallenge.ID)));
        boolean changed = raids.removeIf(owned -> {
            if (!owned.dimension.equals(world.getRegistryKey())) return false;
            var raid = world.getRaidManager().getRaid(owned.id);
            if (raid == null || raid.hasStopped()) return true;
            if (!active || owned.activation != cycle.getActivationSerial()) {
                stop(world, raid);
                return true;
            }
            return false;
        });
        if (changed) markDirty();
    }

    public void stopActivation(MinecraftServer server) {
        long activation = CurseCycleState.getServerState(server).getActivationSerial();
        boolean changed = raids.removeIf(owned -> {
            if (owned.activation != activation) return false;
            var world = server.getWorld(owned.dimension);
            if (world == null) return false;
            var raid = world.getRaidManager().getRaid(owned.id);
            if (raid != null) stop(world, raid);
            return true;
        });
        if (changed) markDirty();
    }

    private static void stop(ServerWorld world, Raid raid) {
        if (!raid.hasStopped() && !raid.isFinished()) {
            raid.invalidate(); // STOPPED, no victory, no entity removal or equipment mutation.
            world.getRaidManager().markDirty();
        }
    }

    public static EndlessRaidState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        var state = new EndlessRaidState();
        var list = nbt.getList("raids", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            var tag = list.getCompound(i);
            var dimension = Identifier.tryParse(tag.getString("dimension"));
            if (dimension != null) state.raids.add(new OwnedRaid(RegistryKey.of(RegistryKeys.WORLD, dimension),
                    tag.getInt("id"), tag.getLong("activation")));
        }
        return state;
    }

    @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        var list = new NbtList();
        for (var owned : raids) {
            var tag = new NbtCompound();
            tag.putString("dimension", owned.dimension.getValue().toString());
            tag.putInt("id", owned.id);
            tag.putLong("activation", owned.activation);
            list.add(tag);
        }
        nbt.put("raids", list);
        return nbt;
    }
}
