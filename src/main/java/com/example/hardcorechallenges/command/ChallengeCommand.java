package com.example.hardcorechallenges.command;

import com.example.hardcorechallenges.api.challenge.Challenge;
import com.example.hardcorechallenges.api.registry.ChallengeRegistry;
import com.example.hardcorechallenges.manager.ChallengeManager;
import com.example.hardcorechallenges.selection.ChallengeSelector;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ChallengeCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("challenge")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.literal("current")
                .executes(context -> {
                    List<Challenge> active = ChallengeManager.getInstance().getActiveChallenges();
                    if (active.isEmpty()) {
                        context.getSource().sendMessage(Text.literal("No active challenges."));
                    } else {
                        context.getSource().sendMessage(Text.literal("Active challenges:"));
                        for (Challenge c : active) {
                            context.getSource().sendMessage(Text.literal("- " + c.getDefinition().id() + ": " + c.getDefinition().name()));
                        }
                    }
                    return 1;
                })
            )
            .then(CommandManager.literal("start")
                .then(CommandManager.argument("id", StringArgumentType.string())
                    .executes(context -> {
                        String idStr = StringArgumentType.getString(context, "id");
                        Identifier id = Identifier.of(idStr);
                        if (ChallengeRegistry.getSupplier(id) == null) {
                            context.getSource().sendError(Text.literal("Challenge not found: " + id));
                            return 0;
                        }
                        ChallengeManager.getInstance().startChallenge(id, context.getSource().getServer());
                        context.getSource().sendMessage(Text.literal("Started challenge: " + id));
                        return 1;
                    })
                )
            )
            .then(CommandManager.literal("start-random")
                .executes(context -> {
                    return startRandomWithRoulette(context.getSource(), 1);
                })
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 10))
                    .executes(context -> {
                        int count = IntegerArgumentType.getInteger(context, "count");
                        return startRandomWithRoulette(context.getSource(), count);
                    })
                )
            )
            .then(CommandManager.literal("roulette")
                .executes(context -> {
                    ChallengeSelector selector = new ChallengeSelector();
                    List<Identifier> available = selector.selectRandomChallenges(3);

                    if (available.isEmpty()) {
                        context.getSource().sendMessage(Text.literal("No challenges available for roulette."));
                        return 0;
                    }

                    context.getSource().sendMessage(Text.literal("Roulette Results:"));
                    for (Identifier id : available) {
                        context.getSource().sendMessage(Text.literal("- " + id));
                    }
                    return 1;
                })
            )
            .then(CommandManager.literal("stop")
                .then(CommandManager.argument("id", StringArgumentType.string())
                    .executes(context -> {
                        String idStr = StringArgumentType.getString(context, "id");
                        Identifier id = Identifier.of(idStr);
                        ChallengeManager.getInstance().stopChallenge(id, context.getSource().getServer());
                        context.getSource().sendMessage(Text.literal("Stopped challenge: " + id));
                        return 1;
                    })
                )
                .executes(context -> {
                    ChallengeManager.getInstance().stopAll(context.getSource().getServer());
                    context.getSource().sendMessage(Text.literal("Stopped all active challenges."));
                    return 1;
                })
            )
            .then(CommandManager.literal("next")
                .executes(context -> {
                    ChallengeManager manager = ChallengeManager.getInstance();
                    manager.stopAll(context.getSource().getServer());

                    Collection<Identifier> allIds = ChallengeRegistry.getRegisteredIds();
                    if (!allIds.isEmpty()) {
                        Identifier nextId = allIds.iterator().next();
                        manager.startChallenge(nextId, context.getSource().getServer());
                        context.getSource().sendMessage(Text.literal("Started next challenge: " + nextId));
                    } else {
                        context.getSource().sendMessage(Text.literal("No challenges available in registry."));
                    }
                    return 1;
                })
            )
            .then(CommandManager.literal("list")
                .executes(context -> {
                    Collection<Identifier> ids = ChallengeRegistry.getRegisteredIds();
                    context.getSource().sendMessage(Text.literal("Registered challenges:"));
                    for (Identifier id : ids) {
                        context.getSource().sendMessage(Text.literal("- " + id.toString()));
                    }
                    return 1;
                })
            )
        );
    }

    private static int startRandomWithRoulette(ServerCommandSource source, int count) {
        ChallengeSelector selector = new ChallengeSelector();
        List<Identifier> selected = selector.selectRandomChallenges(count);

        if (selected.isEmpty()) {
            source.sendMessage(Text.literal("No challenges available to start."));
            return 0;
        }

        List<Identifier> candidates = selector.selectRandomChallenges(com.example.hardcorechallenges.config.ConfigManager.getConfig().rouletteVariantsCount);
        if (candidates.isEmpty()) candidates.addAll(selected);

        List<String> selectedNames = selected.stream()
                .map(id -> ChallengeRegistry.getSupplier(id).get().getDefinition().name())
                .collect(Collectors.toList());
        List<String> candidatesNames = candidates.stream()
                .map(id -> ChallengeRegistry.getSupplier(id).get().getDefinition().name())
                .collect(Collectors.toList());

        com.example.hardcorechallenges.network.NetworkHandler.sendToAll(
                source.getServer(),
                new com.example.hardcorechallenges.network.RouletteStartPayload(selectedNames, candidatesNames)
        );

        for (Identifier id : selected) {
            ChallengeManager.getInstance().startChallenge(id, source.getServer());
        }

        String joined = selected.stream().map(Identifier::toString).collect(Collectors.joining(", "));
        source.sendMessage(Text.literal("Started random challenges via roulette: " + joined));

        return selected.size();
    }
}
