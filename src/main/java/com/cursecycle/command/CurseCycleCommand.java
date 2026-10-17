package com.cursecycle.command;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.network.NetworkHandler;
import com.cursecycle.roulette.RouletteSelector;
import com.cursecycle.state.CurseCycleState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class CurseCycleCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("fdc")
            .then(CommandManager.literal("active").executes(ctx -> showActive(ctx.getSource())))
            .then(CommandManager.literal("next").executes(ctx -> showNext(ctx.getSource())))
            .then(CommandManager.literal("trigger_spin").executes(ctx -> triggerSpin(ctx.getSource())))
            .then(CommandManager.literal("activate")
                .then(CommandManager.argument("id", StringArgumentType.string())
                    .executes(ctx -> activateChallenge(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
            .then(CommandManager.literal("list").executes(ctx -> listChallenges(ctx.getSource())))
            .then(CommandManager.literal("history").executes(ctx -> showHistory(ctx.getSource())))
            .then(CommandManager.literal("setday")
                .then(CommandManager.argument("day", LongArgumentType.longArg(1))
                    .executes(ctx -> setDay(ctx.getSource(), LongArgumentType.getLong(ctx, "day")))))
            .then(CommandManager.literal("reset").executes(ctx -> resetState(ctx.getSource())))
        );

        // Alias for /cursecycle
        dispatcher.register(CommandManager.literal("cursecycle")
            .then(CommandManager.literal("active").executes(ctx -> showActive(ctx.getSource())))
            .then(CommandManager.literal("next").executes(ctx -> showNext(ctx.getSource())))
            .then(CommandManager.literal("trigger_spin").executes(ctx -> triggerSpin(ctx.getSource())))
            .then(CommandManager.literal("activate")
                .then(CommandManager.argument("id", StringArgumentType.string())
                    .executes(ctx -> activateChallenge(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
            .then(CommandManager.literal("list").executes(ctx -> listChallenges(ctx.getSource())))
            .then(CommandManager.literal("history").executes(ctx -> showHistory(ctx.getSource())))
            .then(CommandManager.literal("setday")
                .then(CommandManager.argument("day", LongArgumentType.longArg(1))
                    .executes(ctx -> setDay(ctx.getSource(), LongArgumentType.getLong(ctx, "day")))))
            .then(CommandManager.literal("reset").executes(ctx -> resetState(ctx.getSource())))
        );
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
            Challenge c = ChallengeRegistry.get(nextId);
            String name = c != null ? c.getName() : nextId;
            Formatting f = c != null ? c.getDifficulty().getColor() : Formatting.WHITE;
            source.sendFeedback(() -> Text.literal("[Curse-Cycle] Следующее испытание: " + name + " (" + nextId + ")").formatted(f), false);
        }
        return 1;
    }

    private static int triggerSpin(ServerCommandSource source) {
        CurseCycleState state = CurseCycleState.getServerState(source.getServer());
        RouletteSelector.WheelResult result = RouletteSelector.selectNextChallenge(CurseCycleMod.getConfig(), state);

        if (result == null) {
            source.sendError(Text.literal("[Curse-Cycle] Ошибка: нет доступных испытаний для запуска колеса!"));
            return 0;
        }

        state.setNextChallengeId(result.winner.getId());
        state.setSpinTriggeredForCurrentCycle(true);

        List<String> candidateIds = result.wheelItems.stream().map(Challenge::getId).toList();
        NetworkHandler.triggerWheelForAll(source.getServer(), candidateIds, result.winner.getId());

        source.sendFeedback(() -> Text.literal("[Curse-Cycle] Принудительно запущено колесо фортуны! Победитель: " + result.winner.getName()).formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int activateChallenge(ServerCommandSource source, String id) {
        Challenge c = ChallengeRegistry.get(id);
        if (c == null) {
            source.sendError(Text.literal("[Curse-Cycle] Ошибка: испытание с ID '" + id + "' не найдено!"));
            return 0;
        }

        CurseCycleState state = CurseCycleState.getServerState(source.getServer());

        // Stop active challenges if max limit reached
        while (state.getActiveChallengeIds().size() >= CurseCycleMod.getConfig().maxActiveChallenges && !state.getActiveChallengeIds().isEmpty()) {
            String oldId = state.getActiveChallengeIds().remove(0);
            Challenge oldC = ChallengeRegistry.get(oldId);
            if (oldC != null) {
                for (ServerPlayerEntity p : source.getServer().getPlayerManager().getPlayerList()) {
                    oldC.onStop(source.getServer(), p);
                }
            }
        }

        state.getActiveChallengeIds().add(c.getId());
        state.addHistory(c.getId(), state.getCurrentDay());

        for (ServerPlayerEntity p : source.getServer().getPlayerManager().getPlayerList()) {
            c.onStart(source.getServer(), p);
        }

        source.sendFeedback(() -> Text.literal("[Curse-Cycle] Активировано испытание: " + c.getName()).formatted(Formatting.GREEN), true);
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
                Challenge c = ChallengeRegistry.get(entry.challengeId);
                String name = c != null ? c.getName() : entry.challengeId;
                source.sendFeedback(() -> Text.literal(" - День " + entry.day + ": " + name), false);
            }
        }
        return 1;
    }

    private static int setDay(ServerCommandSource source, long day) {
        CurseCycleState state = CurseCycleState.getServerState(source.getServer());
        long intervalTicks = CurseCycleMod.getConfig().challengeIntervalDays * 24000L;
        state.setCurrentDay(day);
        state.setTotalInGameTicks((day - 1) * 24000L);
        state.setSpinTriggeredForCurrentCycle(false);

        source.sendFeedback(() -> Text.literal("[Curse-Cycle] Игровой день успешно изменен на День " + day).formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int resetState(ServerCommandSource source) {
        CurseCycleState state = CurseCycleState.getServerState(source.getServer());

        // Stop current active challenges
        for (String id : new ArrayList<>(state.getActiveChallengeIds())) {
            Challenge c = ChallengeRegistry.get(id);
            if (c != null) {
                for (ServerPlayerEntity p : source.getServer().getPlayerManager().getPlayerList()) {
                    c.onStop(source.getServer(), p);
                }
            }
        }

        state.getActiveChallengeIds().clear();
        state.setNextChallengeId(null);
        state.setSpinTriggeredForCurrentCycle(false);
        state.setCurrentDay(1);
        state.setTotalInGameTicks(0);
        state.getHistory().clear();
        state.getCurrentPool().clear();
        state.setLastDrawnChallengeId(null);

        source.sendFeedback(() -> Text.literal("[Curse-Cycle] Состояние Curse-Cycle сброшено к начальному!").formatted(Formatting.YELLOW), true);
        return 1;
    }
}
