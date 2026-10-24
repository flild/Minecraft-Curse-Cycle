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

    public InfiniteDietChallenge() {
        super(ID, "Жестокая диета", "Ешь не ешь — всё мимо кассы.",
                "Наш любимый фитнес-тренер заблокировал функцию усвоения калорий! "
                        + "Вы можете уплетать жареную свинину, золотые яблоки и торты тоннами, "
                        + "но шкала голода не прибавится. В Survival и Adventure голод и насыщение "
                        + "не восстанавливаются никаким способом: едой, зельями или командами. "
                        + "Еда расходуется как обычно, а её полезные и вредные эффекты сохраняются. "
                        + "Естественная трата голода и насыщения продолжается. Creative и Spectator исключены. "
                        + "После смерти игрок возрождается с ванильным уровнем голода. "
                        + "После окончания испытания питание снова восстанавливается, без возврата потраченного.",
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
                || state.getPersonalActiveChallengeIds(player.getUuid()).contains(ID);
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
