package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class InfiniteDietChallenge extends Challenge {
    public static final String ID = "infinite_diet";

    // Технические нюансы; не включать в описание для игрока.
    // В Survival/Adventure голод и насыщение не восстанавливаются едой, зельями или командами. Еда
    // расходуется, её эффекты сохраняются; естественная трата голода продолжается. Creative и Spectator
    // исключены. После смерти возвращается ванильный голод. Окончание испытания не возвращает потраченные
    // значения.
    public InfiniteDietChallenge() {
        super(ID, "Жестокая диета",
                "Еда не восполняется.",
                "Еда исчезает, а голод остаётся. Сколько ни ешь, шкала сытости больше не пополняется.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    public static boolean isActiveForPlayer(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || player.isCreative() || player.isSpectator()) return false;
        Challenge challenge = ChallengeRegistry.get(ID);
        MinecraftServer server = serverPlayer.getServer();
        if (challenge == null || !challenge.isEnabled() || server == null) return false;
        CurseCycleState state = CurseCycleState.getServerState(server);
        return state.getActiveChallengeIds().contains(ID)
                || state.findPersonalActiveChallengeIds(player.getUuid()).contains(ID);
    }

    @Override
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) {
            player.sendMessage(Text.literal("[Curse-Cycle] Диета окончена!").formatted(Formatting.GRAY), false);
        }
    }

    @Override
    public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        // Disconnecting does not end the diet. There are no stored effects to remove.
    }
}
