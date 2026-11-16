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

    // Технические нюансы; не включать в описание для игрока.
    // В Survival/Adventure меняется сцепление с землёй, блоки мира не изменяются. После отпускания клавиш
    // скорость постепенно затухает. Прыжки, плавание, лодки, вагонетки и полёт работают штатно; Creative и
    // Spectator исключены. После завершения сцепление восстанавливается.
    public SlipperyHandsChallenge() {
        super(ID, "Только фонка не хватает",
                "Под ногами будто сплошной лёд.",
                "Теперь ты везде скользишь.",
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
                || state.findPersonalActiveChallengeIds(player.getUuid()).contains(ID);
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
