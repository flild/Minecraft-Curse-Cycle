package com.example.hardcorechallenges.api.challenge;

import net.minecraft.nbt.NbtCompound;

public abstract class BaseChallenge implements Challenge {
    private final ChallengeDefinition definition;
    private ChallengeState state = ChallengeState.INACTIVE;

    public BaseChallenge(ChallengeDefinition definition) {
        this.definition = definition;
    }

    @Override
    public ChallengeDefinition getDefinition() {
        return this.definition;
    }

    @Override
    public void onActivate(ChallengeContext context) {
        this.state = ChallengeState.ACTIVE;
    }

    @Override
    public void onDeactivate(ChallengeContext context) {
        this.state = ChallengeState.INACTIVE;
    }

    @Override
    public void tick(ChallengeContext context) {
    }

    @Override
    public ChallengeState getState() {
        return this.state;
    }

    @Override
    public void setState(ChallengeState state) {
        this.state = state;
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        nbt.putString("State", this.state.name());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        if (nbt.contains("State")) {
            try {
                this.state = ChallengeState.valueOf(nbt.getString("State"));
            } catch (IllegalArgumentException e) {
                this.state = ChallengeState.INACTIVE;
            }
        }
    }
}
