package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SlipperyHandsChallenge extends Challenge {
    public static final String ID = "slippery_hands";
    // Ordinary ice: retain vanilla air drag and acceleration, with ice ground friction.
    private static final float ICE_SLIPPERINESS = 0.98F;

    public SlipperyHandsChallenge() {
        super(ID, "Только фонка не хватает", "Тун тун тун тундундун дун дун",
                "Каждый кубический метр земли под ногами игрока превращается в каток. "
                        + "Трение полностью пропадает, заставляя персонажа неуправляемо скользить при каждом шаге. "
                        + "В Survival и Adventure сцепление с землёй становится как на льду: после отпускания "
                        + "клавиш игрок продолжает скользить до постепенной остановки. Прыжки, плавание, "
                        + "лодки, вагонетки и полёт работают как обычно. Creative и Spectator исключены. "
                        + "После окончания испытания обычное сцепление возвращается; блоки мира не изменяются.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }

    public static boolean isActiveForPlayer(PlayerEntity player) {
        if (player == null || player.isCreative() || player.isSpectator()) return false;
        if (player.getWorld().isClient()) {
            // Use the existing server-supplied IDs for local movement prediction.
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

    public static float getSlipperiness(PlayerEntity player, float original) {
        if (player == null || !player.isOnGround() || player.hasVehicle()
                || player.getAbilities().flying || player.isFallFlying()
                || player.isTouchingWater() || player.isInLava() || player.isClimbing()
                || !isActiveForPlayer(player)) return original;
        return ICE_SLIPPERINESS;
    }

    @Override
    public void onStart(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) {
            player.sendMessage(Text.literal("[Curse-Cycle] Ноги в масле, катимся колбаской")
                    .formatted(Formatting.AQUA), false);
        }
    }

    @Override
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) {
            player.sendMessage(Text.literal("[Curse-Cycle] Испытание «Только фонка не хватает» завершено. "
                    + "Нормальное сцепление с землёй восстановлено.").formatted(Formatting.GRAY), false);
        }
    }

    @Override
    public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        // No stored modifiers to clean up; disconnecting does not end the challenge.
    }
}
