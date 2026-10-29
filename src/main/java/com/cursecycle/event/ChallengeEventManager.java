package com.cursecycle.event;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

import java.util.LinkedHashSet;
import java.util.Set;

public class ChallengeEventManager {

    public static void registerEvents() {
        // Player Damage Event
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                MinecraftServer server = player.getServer();
                if (server != null) {
                    dispatchToActive(server, player, c -> c.onPlayerDamage(server, player, source, amount));
                }
            }
            return true;
        });

        // Block Break Event
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                MinecraftServer server = serverPlayer.getServer();
                if (server != null) {
                    dispatchToActive(server, serverPlayer, c -> c.onBlockBreak(server, serverPlayer, pos, state));
                }
            }
        });

        // Block Use / Place Event
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                MinecraftServer server = serverPlayer.getServer();
                if (server != null) {
                    dispatchToActive(server, serverPlayer, c -> c.onBlockPlace(server, serverPlayer, hitResult.getBlockPos(), world.getBlockState(hitResult.getBlockPos())));
                }
            }
            return ActionResult.PASS;
        });

        // Item Use Event
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                MinecraftServer server = serverPlayer.getServer();
                if (server != null) {
                    dispatchToActive(server, serverPlayer, c -> c.onItemUse(server, serverPlayer, player.getStackInHand(hand)));
                }
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });

        // Entity Interact Event
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                MinecraftServer server = serverPlayer.getServer();
                if (server != null) {
                    dispatchToActive(server, serverPlayer, c -> c.onEntityInteract(server, serverPlayer, entity));
                }
            }
            return ActionResult.PASS;
        });
    }

    public static void dispatchToActive(MinecraftServer server, ServerPlayerEntity player, ChallengeConsumer consumer) {
        CurseCycleState state = CurseCycleState.getServerState(server);

        Set<String> activeIds = new LinkedHashSet<>(state.getActiveChallengeIds());
        activeIds.addAll(state.getPersonalActiveChallengeIds(player.getUuid()));
        for (String id : activeIds) {
            Challenge challenge = ChallengeRegistry.get(id);
            if (challenge != null && challenge.isEnabled()) {
                consumer.accept(challenge);
            }
        }
    }

    @FunctionalInterface
    public interface ChallengeConsumer {
        void accept(Challenge challenge);
    }
}
