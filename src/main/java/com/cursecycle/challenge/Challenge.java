package com.cursecycle.challenge;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public abstract class Challenge {
    private final String id;
    private final String name;
    private final String shortDescription;
    private final String detailedRules;
    private final ChallengeDifficulty difficulty;
    private final ChallengeScope scope;
    private boolean enabled = true;

    public Challenge(String id, String name, String shortDescription, String detailedRules, ChallengeDifficulty difficulty, ChallengeScope scope) {
        this.id = id;
        this.name = name;
        this.shortDescription = shortDescription;
        this.detailedRules = detailedRules;
        this.difficulty = difficulty;
        this.scope = scope;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getDetailedRules() {
        return detailedRules;
    }

    public ChallengeDifficulty getDifficulty() {
        return difficulty;
    }

    public ChallengeScope getScope() {
        return scope;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Called when the challenge becomes active.
     */
    public void onStart(MinecraftServer server, ServerPlayerEntity player) {}

    /**
     * Called when the challenge stops being active.
     */
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {}

    /**
     * Called every tick while active.
     */
    public void onTick(MinecraftServer server, ServerPlayerEntity player) {}

    /**
     * Called when a player joins the server while this challenge is active.
     */
    public void onPlayerJoin(MinecraftServer server, ServerPlayerEntity player) {
        onStart(server, player);
    }

    /**
     * Called when a player leaves the server while this challenge is active.
     */
    public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        onStop(server, player);
    }

    /** Event Hooks for Challenge Mechanics **/
    public void onPlayerJump(MinecraftServer server, ServerPlayerEntity player) {}
    public void onPlayerDamage(MinecraftServer server, ServerPlayerEntity player, net.minecraft.entity.damage.DamageSource source, float amount) {}
    public void onBlockBreak(MinecraftServer server, ServerPlayerEntity player, net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {}
    public void onBlockPlace(MinecraftServer server, ServerPlayerEntity player, net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {}
    public void onItemUse(MinecraftServer server, ServerPlayerEntity player, net.minecraft.item.ItemStack stack) {}
    public void onEntityInteract(MinecraftServer server, ServerPlayerEntity player, net.minecraft.entity.Entity entity) {}

    /**
     * Save custom state for this challenge.
     */
    public NbtCompound writeNbt(NbtCompound nbt) {
        return nbt;
    }

    /**
     * Read custom state for this challenge.
     */
    public void readNbt(NbtCompound nbt) {}
}
