package com.example.hardcorechallenges.command;

import com.example.hardcorechallenges.api.challenge.Challenge;
import com.example.hardcorechallenges.api.registry.ChallengeRegistry;
import com.example.hardcorechallenges.manager.ChallengeManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.List;

public class ChallengeCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("challenge")
            .requires(source -> source.hasPermissionLevel(2)) // Только операторы
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
                        if (ChallengeRegistry.createChallenge(id) == null) {
                            context.getSource().sendError(Text.literal("Challenge not found: " + id));
                            return 0;
                        }
                        ChallengeManager.getInstance().startChallenge(id, context.getSource().getServer());
                        context.getSource().sendMessage(Text.literal("Started challenge: " + id));
                        return 1;
                    })
                )
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
                    // Базовая логика для перехода к следующему испытанию
                    ChallengeManager manager = ChallengeManager.getInstance();
                    manager.stopAll(context.getSource().getServer());

                    Collection<Identifier> allIds = ChallengeRegistry.getRegisteredIds();
                    if (!allIds.isEmpty()) {
                        Identifier nextId = allIds.iterator().next(); // Временно берем первое попавшееся
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
            .then(CommandManager.literal("reload")
                .executes(context -> {
                    context.getSource().sendMessage(Text.literal("Reload not fully implemented. Architecture ready."));
                    return 1;
                })
            )
        );
    }
}
