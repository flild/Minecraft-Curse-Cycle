package com.example.hardcorechallenges.state;

import com.example.hardcorechallenges.api.challenge.Challenge;
import com.example.hardcorechallenges.api.registry.ChallengeRegistry;
import com.example.hardcorechallenges.manager.ChallengeManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

public class ChallengePersistentState extends PersistentState {

    private static final String STATE_KEY = "hardcorechallenges_state";
    private int dirtyTimer = 0;

    public ChallengePersistentState() {
        super();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        ChallengeManager manager = ChallengeManager.getInstance();

        nbt.putLong("TotalTicks", manager.getTotalTicks());
        nbt.putBoolean("Paused", manager.isPaused());
        nbt.putString("GameMode", manager.getGameMode().name());

        NbtCompound statsNbt = new NbtCompound();
        manager.getStatistics().writeNbt(statsNbt);
        nbt.put("Statistics", statsNbt);

        NbtList challengesList = new NbtList();
        for (Challenge challenge : manager.getActiveChallenges()) {
            NbtCompound challengeNbt = new NbtCompound();
            challengeNbt.putString("Id", challenge.getDefinition().id().toString());
            NbtCompound stateNbt = new NbtCompound();
            challenge.writeNbt(stateNbt);
            challengeNbt.put("State", stateNbt);
            challengesList.add(challengeNbt);
        }
        nbt.put("ActiveChallenges", challengesList);

        return nbt;
    }

    public static ChallengePersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        ChallengePersistentState state = new ChallengePersistentState();
        ChallengeManager manager = ChallengeManager.getInstance();

        manager.setTotalTicks(nbt.getLong("TotalTicks"));
        if (nbt.contains("Statistics")) {
            manager.getStatistics().readNbt(nbt.getCompound("Statistics"));
        }
        if (nbt.contains("GameMode")) {
            try {
                manager.setGameMode(com.example.hardcorechallenges.api.challenge.GameMode.valueOf(nbt.getString("GameMode")));
            } catch (Exception e) {}
        }
        if (nbt.getBoolean("Paused")) {
            manager.pause();
        } else {
            manager.resume();
        }

        manager.clearChallenges();
        if (nbt.contains("ActiveChallenges", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("ActiveChallenges", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound challengeNbt = list.getCompound(i);
                Identifier id = Identifier.of(challengeNbt.getString("Id"));
                Challenge challenge = ChallengeRegistry.createChallenge(id);
                if (challenge != null) {
                    challenge.readNbt(challengeNbt.getCompound("State"));
                    manager.restoreChallenge(challenge);
                }
            }
        }

        return state;
    }

    public static ChallengePersistentState getServerState(MinecraftServer server) {
        PersistentState.Type<ChallengePersistentState> type = new PersistentState.Type<>(
                ChallengePersistentState::new,
                ChallengePersistentState::fromNbt,
                null
        );
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(type, STATE_KEY);
    }

    public static void registerLifecycle() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ChallengePersistentState state = getServerState(server);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ChallengePersistentState state = getServerState(server);
            state.dirtyTimer++;
            if (state.dirtyTimer >= 200) { // Каждые 10 секунд
                state.markDirty();
                state.dirtyTimer = 0;
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ChallengePersistentState state = getServerState(server);
            state.markDirty();
        });
    }
}
