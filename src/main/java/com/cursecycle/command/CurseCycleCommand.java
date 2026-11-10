package com.cursecycle.command;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeUtils;
import com.cursecycle.network.NetworkHandler;
import com.cursecycle.roulette.RouletteSelector;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class CurseCycleCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(buildCommands("fdc"));
        dispatcher.register(buildCommands("cursecycle"));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildCommands(String rootAlias) {
        return CommandManager.literal(rootAlias)
            .then(CommandManager.literal("active").executes(ctx -> showActive(ctx.getSource())))
            .then(CommandManager.literal("next").executes(ctx -> showNext(ctx.getSource())))
            .then(adminCommand("trigger_spin").executes(ctx -> triggerSpin(ctx.getSource())))
            .then(adminCommand("activate_next").executes(ctx -> activateNext(ctx.getSource())))
            .then(adminCommand("activate")
                .then(CommandManager.argument("id", StringArgumentType.string())
                    .suggests((ctx, builder) -> {
                        String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                        for (Challenge challenge : ChallengeRegistry.getAll()) {
                            if (challenge.isEnabled() && challenge.getId().startsWith(prefix)) {
                                builder.suggest(challenge.getId());
                            }
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> activateChallenge(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
            .then(CommandManager.literal("list").executes(ctx -> listChallenges(ctx.getSource())))
            .then(CommandManager.literal("history").executes(ctx -> showHistory(ctx.getSource())))
            .then(adminCommand("setday")
                .then(CommandManager.argument("day", LongArgumentType.longArg(1, Long.MAX_VALUE / 24000L))
                    .executes(ctx -> setDay(ctx.getSource(), LongArgumentType.getLong(ctx, "day")))))
            .then(adminCommand("reset").executes(ctx -> resetState(ctx.getSource())));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> adminCommand(String name) {
        return CommandManager.literal(name).requires(source -> source.hasPermissionLevel(2));
    }

    private static int showActive(ServerCommandSource source) {
        CurseCycleState state = CurseCycleState.getServerState(source.getServer());
        List<String> activeIds = state.getActiveChallengeIds();

        if (activeIds.isEmpty()) {
            source.sendFeedback(() -> Text.literal("[Curse-Cycle] Нет активных испытаний.").formatted(Formatting.GRAY), false);
        } else {
            source.sendFeedback(() -> Text.literal("[Curse-Cycle] Активные испытания:").formatted(Formatting.GOLD), false);
            for (String id : activeIds) {
                Challenge c = ChallengeRegistry.get(id);
                String name = c != null ? c.getName() : id;
                Formatting f = c != null ? c.getDifficulty().getColor() : Formatting.WHITE;
                source.sendFeedback(() -> Text.literal(" - " + name + " (" + id + ")").formatted(f), false);
            }
        }
        return 1;
    }

    private static int showNext(ServerCommandSource source) {
        CurseCycleState state = CurseCycleState.getServerState(source.getServer());
        String nextId = state.getNextChallengeId();

        if (nextId == null || nextId.isEmpty()) {
            source.sendFeedback(() -> Text.literal("[Curse-Cycle] Следующее испытание еще не выбрано.").formatted(Formatting.GRAY), false);
        } else {
            String displayName = ChallengeUtils.getFormattedDisplayName(nextId);
            source.sendFeedback(() -> Text.literal("[Curse-Cycle] Следующее испытание: " + displayName).formatted(Formatting.AQUA), false);
        }
        return 1;
    }

    private static int triggerSpin(ServerCommandSource source) {
        CurseCycleState state = CurseCycleState.getServerState(source.getServer());
        RouletteSelector.WheelResult result = CycleManager.selectUpcomingChallenge(state, CurseCycleMod.getConfig(),
                source.getServer().getOverworld().getTimeOfDay());

        if (result == null) {
            source.sendError(Text.literal("[Curse-Cycle] Ошибка: нет доступных испытаний для запуска колеса!"));
            return 0;
        }

        String encodedWinner = result.getEncodedWinnerId();

        List<String> candidateIds = result.wheelItems.stream().map(Challenge::getId).toList();
        NetworkHandler.triggerWheelForAll(source.getServer(), candidateIds, encodedWinner);
        CycleManager.syncState(source.getServer(), state, CurseCycleMod.getConfig());

        source.sendFeedback(() -> Text.literal("[Curse-Cycle] Принудительно запущено колесо фортуны! Победитель: " + result.winner.getName()).formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int activateNext(ServerCommandSource source) {
        CurseCycleState state = CurseCycleState.getServerState(source.getServer());
        String nextId = state.getNextChallengeId();
        if (nextId == null || nextId.isEmpty()) {
            source.sendError(Text.literal("[Curse-Cycle] Следующее испытание не выбрано. Сначала: /fdc trigger_spin"));
            return 0;
        }
        if (!CycleManager.activateManually(source.getServer(), state, nextId)) {
            source.sendError(Text.literal("[Curse-Cycle] Выбранное испытание недоступно. Запустите /fdc trigger_spin заново."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("[Curse-Cycle] Следующее испытание активировано. Предыдущее завершено, отсчёт начат заново.")
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int activateChallenge(ServerCommandSource source, String id) {
        CurseCycleState state = CurseCycleState.getServerState(source.getServer());
        if (!CycleManager.activateManually(source.getServer(), state, id)) {
            source.sendError(Text.literal("[Curse-Cycle] Неизвестное или отключённое испытание: " + id));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("[Curse-Cycle] Испытание активировано: "
                + ChallengeUtils.getFormattedDisplayName(id) + ". Отсчёт начат заново.")
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int listChallenges(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("[Curse-Cycle] Зарегистрированные испытания:").formatted(Formatting.GOLD), false);
        for (Challenge c : ChallengeRegistry.getAll()) {
            Formatting f = c.getDifficulty().getColor();
            String status = c.isEnabled() ? " [ВКЛ]" : " [ВЫКЛ]";
            source.sendFeedback(() -> Text.literal(" • " + c.getName() + " (" + c.getId() + ") - " + c.getDifficulty().getDisplayName() + status).formatted(f), false);
        }
        return 1;
    }

    private static int showHistory(ServerCommandSource source) {
        CurseCycleState state = CurseCycleState.getServerState(source.getServer());
        List<CurseCycleState.HistoryEntry> history = state.getHistory();

        if (history.isEmpty()) {
            source.sendFeedback(() -> Text.literal("[Curse-Cycle] История выпадавших испытаний пуста.").formatted(Formatting.GRAY), false);
        } else {
            source.sendFeedback(() -> Text.literal("[Curse-Cycle] История испытаний:").formatted(Formatting.GOLD), false);
            for (CurseCycleState.HistoryEntry entry : history) {
                String displayName = ChallengeUtils.getFormattedDisplayName(entry.challengeId);
                source.sendFeedback(() -> Text.literal(" - День " + entry.day + ": " + displayName), false);
            }
        }
        return 1;
    }

    private static int setDay(ServerCommandSource source, long day) {
        CurseCycleState state = CurseCycleState.getServerState(source.getServer());
        long newTimeOfDay = (day - 1) * 24000L;
        if (source.getServer().getOverworld() != null) {
            source.getServer().getOverworld().setTimeOfDay(newTimeOfDay);
        }
        state.setCurrentDay(day);
        state.setTotalInGameTicks(newTimeOfDay);
        CycleManager.updateSchedule(source.getServer(), CurseCycleMod.getConfig());
        CycleManager.syncState(source.getServer(), state, CurseCycleMod.getConfig());

        source.sendFeedback(() -> Text.literal("[Curse-Cycle] Игровой день успешно изменен на День " + day).formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int resetState(ServerCommandSource source) {
        CurseCycleState state = CurseCycleState.getServerState(source.getServer());

        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            var ids = new java.util.LinkedHashSet<>(state.getActiveChallengeIds());
            ids.addAll(state.findPersonalActiveChallengeIds(player.getUuid()));
            for (String id : ids) {
                Challenge challenge = ChallengeRegistry.get(id);
                if (challenge != null) challenge.onStop(source.getServer(), player);
            }
        }
        state.reset();
        source.getServer().getOverworld().setTimeOfDay(0);
        CycleManager.updateSchedule(source.getServer(), CurseCycleMod.getConfig());
        CycleManager.syncState(source.getServer(), state, CurseCycleMod.getConfig());

        source.sendFeedback(() -> Text.literal("[Curse-Cycle] Состояние Curse-Cycle сброшено к начальному!").formatted(Formatting.YELLOW), true);
        return 1;
    }
}
