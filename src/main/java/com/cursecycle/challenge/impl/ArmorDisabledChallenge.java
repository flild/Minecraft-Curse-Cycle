package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ArmorDisabledChallenge extends Challenge {
    public static final String ID = "armor_disabled";

    public ArmorDisabledChallenge() {
        super(ID, "Голый король", "Броня и элитры полностью отключены",
                "Все виды брони и элитр теряют свою защиту и полезные свойства. "
                        + "Броня не защищает от урона, а элитры не позволяют совершать полёт.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    public static boolean isActiveForPlayer(PlayerEntity player) {
        if (player == null) return false;
        if (player.getWorld().isClient()) {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                    && ClientDataHolder.getActiveChallengeIds().contains(ID);
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return false;
        Challenge challenge = ChallengeRegistry.get(ID);
        MinecraftServer server = serverPlayer.getServer();
        if (challenge == null || !challenge.isEnabled() || server == null) return false;
        CurseCycleState state = CurseCycleState.getServerState(server);
        return state.getActiveChallengeIds().contains(ID)
                || state.getPersonalActiveChallengeIds(player.getUuid()).contains(ID);
    }

    public static boolean isArmorStackDisabled(Entity entity, ItemStack stack) {
        if (!(entity instanceof PlayerEntity player) || !isActiveForPlayer(player)) return false;
        // Identity matters: an identical item in a hand must keep its own enchantments.
        for (ItemStack armor : player.getArmorItems()) {
            if (armor == stack && !armor.isEmpty()) return true;
        }
        return false;
    }

    @Override
    public void onStart(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) {
            player.stopFallFlying();
        } else if (server != null) {
            server.getPlayerManager().getPlayerList().forEach(PlayerEntity::stopFallFlying);
        }
    }

    @Override
    public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null && player.isFallFlying()) player.stopFallFlying();
    }

    @Override
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) {
            player.sendMessage(Text.literal("[Curse-Cycle] Испытание «Голый король» завершено.")
                    .formatted(Formatting.GRAY), false);
        }
    }

    @Override
    public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        // Leaving is not the end of the challenge. No saved modifiers need cleanup.
    }
}
