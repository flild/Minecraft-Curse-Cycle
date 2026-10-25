package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class AdhdNoStandingStillChallenge extends Challenge {
    public static final String ID = "adhd_no_standing_still";
    private static final int STANDING_THRESHOLD_TICKS = 60;
    private static final int INITIAL_WITHER_DURATION_TICKS = 200;
    private static final int DURATION_INCREMENT_TICKS = 200;

    // No player/entity references: respawns and disconnected players cannot retain old entities.
    private final Map<MinecraftServer, Session> sessions = new WeakHashMap<>();

    public AdhdNoStandingStillChallenge() {
        super(ID, "У тебя СДВГ?",
                "Движение — это жизнь. Стоять на месте строго запрещено! Ну или очень больно.",
                "У персонажа обострился тяжелейший СДВГ. Если задержаться на одном и том же блоке "
                        + "дольше 3 секунд, накладывается эффект иссушения. С каждым новым нарушением "
                        + "продлевается ещё на 10 секунд. В Survival и Adventure первые 61 тик неподвижности "
                        + "дают Иссушение I на 10 секунд, каждые следующие 61 тик — на 20, 30 секунд и далее. "
                        + "Смена блока или высоты, включая прыжок и вертикальное падение, сбрасывает таймер. "
                        + "Движение внутри одного блока по горизонтали таймер не сбрасывает. Лодки, вагонетки, "
                        + "Creative и Spectator исключены; мобы не затрагиваются. После выхода из транспорта, "
                        + "смены режима, измерения, входа или респауна отсчёт начинается заново; накопление "
                        + "длительности сохраняется до конца испытания в текущем запуске сервера. После "
                        + "перезапуска таймеры и счётчики начинаются заново. Уже выданные эффекты истекают "
                        + "естественно, в том числе после завершения испытания. Более сильное или длительное "
                        + "постороннее иссушение не заменяется.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    private Session session(MinecraftServer server) {
        CurseCycleState state = CurseCycleState.getServerState(server);
        var history = state.getHistory();
        var activation = history.isEmpty() ? null : history.getLast();
        Session session = sessions.get(server);
        // onStop is per online player, and is never called when the server is empty.
        // The shared activation marker also invalidates counters belonging to offline players.
        if (session == null || session.source != state || session.activation != activation) {
            session = new Session(state, activation);
            sessions.put(server, session);
        }
        return session;
    }

    @Override
    public void onStart(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return;
        StandingTracker tracker = session(server).players.computeIfAbsent(player.getUuid(), id -> new StandingTracker());
        if (tracker.position == null) {
            tracker.observe(server.getTicks(), player.getId(), player.getWorld().getRegistryKey(),
                    player.getBlockPos(), player.getY());
        }
        player.sendMessage(Text.literal("[Curse-Cycle] У тебя СДВГ? Стоять на месте больше 3 секунд нельзя!")
                .formatted(Formatting.RED), false);
    }

    @Override
    public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return;
        Session session = session(server);
        boolean active = isEnabled() && (session.source.getActiveChallengeIds().contains(ID)
                || session.source.getPersonalActiveChallengeIds(player.getUuid()).contains(ID));
        if (!active) {
            session.players.remove(player.getUuid());
            return;
        }
        StandingTracker tracker = session.players.computeIfAbsent(player.getUuid(), id -> new StandingTracker());
        if (!player.isAlive() || player.isCreative() || player.isSpectator()
                || player.getRootVehicle() instanceof BoatEntity
                || player.getRootVehicle() instanceof AbstractMinecartEntity) {
            tracker.resetPosition();
            return;
        }
        int duration = tracker.observe(server.getTicks(), player.getId(), player.getWorld().getRegistryKey(),
                player.getBlockPos(), player.getY());
        if (duration == 0) return;
        StatusEffectInstance existing = player.getStatusEffect(StatusEffects.WITHER);
        // Vanilla merges effects of the same type without ownership information. Never remove,
        // downgrade, or shorten one, including its hidden effects from another source.
        if (existing == null || (!existing.isInfinite() && existing.getAmplifier() == 0
                && existing.getDuration() < duration)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, duration, 0));
        }
    }

    @Override
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return;
        session(server).players.remove(player.getUuid());
        player.sendMessage(Text.literal("[Curse-Cycle] Испытание «У тебя СДВГ?» завершено. Контроль позиций отменён.")
                .formatted(Formatting.GRAY), false);
    }

    @Override
    public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return;
        StandingTracker tracker = session(server).players.get(player.getUuid());
        if (tracker != null) tracker.resetPosition();
    }

    private static final class Session {
        final CurseCycleState source;
        final CurseCycleState.HistoryEntry activation;
        final Map<UUID, StandingTracker> players = new HashMap<>();

        Session(CurseCycleState source, CurseCycleState.HistoryEntry activation) {
            this.source = source;
            this.activation = activation;
        }
    }

    static final class StandingTracker {
        private BlockPos position;
        private Object dimension;
        private int entityId;
        private double lastY;
        private int standingSince;
        private int lastTick;
        private int penaltyDuration;

        int observe(int tick, int entityId, Object dimension, BlockPos position, double y) {
            if (this.position == null || this.entityId != entityId || !dimension.equals(this.dimension)
                    || !position.equals(this.position) || Double.compare(y, lastY) != 0) {
                this.position = position.toImmutable();
                this.dimension = dimension;
                this.entityId = entityId;
                lastY = y;
                standingSince = tick;
                lastTick = tick;
                return 0;
            }
            // The same ID may be dispatched via both global and personal lists in a single tick.
            if (tick == lastTick) return 0;
            lastTick = tick;
            if (tick - standingSince <= STANDING_THRESHOLD_TICKS) return 0;
            standingSince = tick;
            penaltyDuration = penaltyDuration == 0 ? INITIAL_WITHER_DURATION_TICKS
                    : (int) Math.min(Integer.MAX_VALUE, (long) penaltyDuration + DURATION_INCREMENT_TICKS);
            return penaltyDuration;
        }

        void resetPosition() {
            position = null;
        }
    }
}
