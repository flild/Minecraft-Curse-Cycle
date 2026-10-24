package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.InfiniteDietChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CakeBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InfiniteDietGameTest implements FabricGameTest {
    // One synchronous test keeps the shared active IDs isolated from other tests.
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void foodRecoveryAndLifecycle(TestContext context) throws Exception {
        var world = context.getWorld();
        var server = world.getServer();
        var manager = server.getPlayerManager();
        var state = CurseCycleState.getServerState(server);
        var oldState = state.writeNbt(new net.minecraft.nbt.NbtCompound(), world.getRegistryManager());
        Difficulty oldDifficulty = world.getDifficulty();
        List<ServerPlayerEntity> players = new ArrayList<>();
        var challenge = ChallengeRegistry.get(InfiniteDietChallenge.ID);
        try {
            ServerPlayerEntity player = createPlayer(context, UUID.randomUUID(), players);
            setNutrition(player, 10, 2);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200));
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc list");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc activate infinite_diet");
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc active");
            context.assertTrue(state.getActiveChallengeIds().contains(InfiniteDietChallenge.ID), "Command activation");
            assertNutrition(context, player, 10, 2, "Activation preserves existing nutrition");
            challenge.onPlayerJoin(server, player);
            challenge.onPlayerJoin(server, player);
            assertNutrition(context, player, 10, 2, "Repeated application is harmless");

            ItemStack steaks = new ItemStack(Items.COOKED_BEEF, 2);
            steaks.finishUsing(world, player);
            context.assertEquals(1, steaks.getCount(), "Steak is consumed");
            assertNutrition(context, player, 10, 2, "Steak cannot restore nutrition");

            ItemStack apple = new ItemStack(Items.GOLDEN_APPLE);
            apple.finishUsing(world, player);
            context.assertTrue(apple.isEmpty(), "Golden apple is consumed");
            assertNutrition(context, player, 10, 2, "Golden apple cannot restore nutrition");
            context.assertTrue(player.hasStatusEffect(StatusEffects.ABSORPTION), "Apple absorption remains");
            context.assertTrue(player.hasStatusEffect(StatusEffects.REGENERATION), "Apple regeneration remains");
            new ItemStack(Items.PUFFERFISH).finishUsing(world, player);
            context.assertTrue(player.hasStatusEffect(StatusEffects.POISON), "Harmful food effects remain");
            assertNutrition(context, player, 10, 2, "Pufferfish cannot restore nutrition");

            BlockPos cake = context.getAbsolutePos(new BlockPos(2, 2, 2));
            world.setBlockState(cake.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(cake, Blocks.CAKE.getDefaultState(), Block.NOTIFY_LISTENERS);
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.interactionManager.interactBlock(player, world, ItemStack.EMPTY, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(cake), Direction.UP, cake, false));
            context.assertEquals(1, world.getBlockState(cake).get(CakeBlock.BITES), "Cake bite is consumed");
            assertNutrition(context, player, 10, 2, "Cake cannot restore nutrition");

            server.getCommandManager().executeWithPrefix(server.getCommandSource(),
                    "effect give " + player.getName().getString() + " minecraft:saturation 1 4");
            assertNutrition(context, player, 10, 2, "Saturation command cannot restore nutrition");
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(20);
            assertNutrition(context, player, 10, 2, "Direct setters cannot restore nutrition");
            player.getHungerManager().setFoodLevel(8);
            player.getHungerManager().setSaturationLevel(1);
            assertNutrition(context, player, 8, 1, "Direct decreases remain possible");
            player.getHungerManager().setExhaustion(5);
            player.getHungerManager().update(player);
            assertNutrition(context, player, 8, 0, "Exhaustion consumes saturation");
            server.setDifficulty(Difficulty.NORMAL, true);
            player.getHungerManager().setExhaustion(5);
            player.getHungerManager().update(player);
            assertNutrition(context, player, 7, 0, "Exhaustion consumes hunger");
            server.setDifficulty(Difficulty.PEACEFUL, true);
            player.age = 20;
            player.tickMovement();
            assertNutrition(context, player, 7, 0, "Peaceful cannot restore hunger");
            server.setDifficulty(oldDifficulty, true);

            player.changeGameMode(GameMode.ADVENTURE);
            new ItemStack(Items.COOKED_BEEF).finishUsing(world, player);
            assertNutrition(context, player, 7, 0, "Adventure is affected");
            for (GameMode mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
                player.changeGameMode(mode);
                player.getHungerManager().setFoodLevel(10);
                player.getHungerManager().setSaturationLevel(2);
                player.getHungerManager().add(2, 0.5F);
                assertNutrition(context, player, 12, 4, mode + " is exempt");
            }
            player.changeGameMode(GameMode.SURVIVAL);
            player.getHungerManager().add(2, 0.5F);
            assertNutrition(context, player, 12, 4, "Restriction resumes immediately on mode change");

            var late = createPlayer(context, UUID.randomUUID(), players);
            setNutrition(late, 6, 0);
            late.getHungerManager().add(5, 1);
            assertNutrition(context, late, 6, 0, "Late join is affected before any tick");
            assertNutrition(context, player, 12, 4, "Players have independent nutrition");

            UUID reconnectId = player.getUuid();
            manager.remove(player);
            player = createPlayer(context, reconnectId, players);
            assertNutrition(context, player, 12, 4, "Saved player nutrition loads unchanged");
            player.getHungerManager().add(5, 1);
            assertNutrition(context, player, 12, 4, "Reconnect retains restriction");
            player.kill();
            player = manager.respawnPlayer(player, false, Entity.RemovalReason.KILLED);
            player.networkHandler.player = player;
            players.add(player);
            player.changeGameMode(GameMode.SURVIVAL);
            assertNutrition(context, player, 20, 5, "Death respawn uses vanilla nutrition");
            player.getHungerManager().setFoodLevel(10);
            player.getHungerManager().add(5, 1);
            assertNutrition(context, player, 10, 5, "Death respawn retains restriction");

            // Alive respawns reuse HungerManager: its owner must be the new object.
            var previous = player;
            player = manager.respawnPlayer(player, true, Entity.RemovalReason.CHANGED_DIMENSION);
            player.networkHandler.player = player;
            players.add(player);
            context.assertTrue(previous.getHungerManager() == player.getHungerManager(), "Alive respawn reuses manager");
            previous.changeGameMode(GameMode.CREATIVE);
            player.changeGameMode(GameMode.SURVIVAL);
            player.getHungerManager().add(5, 1);
            assertNutrition(context, player, 10, 5, "Copied manager uses the new owner's mode");

            for (var dimension : List.of(World.NETHER, World.END, World.OVERWORLD)) {
                player.teleport(server.getWorld(dimension), 0.5, 90, 0.5, 0, 0);
                player.getHungerManager().add(5, 1);
                assertNutrition(context, player, 10, 5, "Restriction follows dimension " + dimension);
            }

            var persistence = world.getPersistentStateManager();
            state.markDirty();
            persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    SharedConstants.getGameVersion().getSaveVersion().getId());
            state = CurseCycleState.createFromNbt(saved.getCompound("data"), world.getRegistryManager());
            context.assertTrue(state.getActiveChallengeIds().contains(InfiniteDietChallenge.ID), "Active ID saved to disk");
            persistence.set(CurseCycleState.STATE_KEY, state);
            player.getHungerManager().add(5, 1);
            assertNutrition(context, player, 10, 5, "Disk-restored shared state resumes restriction");

            CycleManager.activateChallenge(server, state, "double_challenge:infinite_diet,no_sprint");
            player.getHungerManager().add(5, 1);
            player.setSprinting(true);
            context.assertFalse(player.isSprinting(), "Second challenge remains effective");
            assertNutrition(context, player, 10, 5, "Diet remains effective in a double challenge");

            state.getActiveChallengeIds().clear();
            state.getPersonalActiveChallengeIds(player.getUuid()).add(InfiniteDietChallenge.ID);
            player.getHungerManager().add(5, 1);
            assertNutrition(context, player, 10, 5, "Personal ID applies to its owner");
            late.getHungerManager().add(2, 0.5F);
            assertNutrition(context, late, 8, 2, "Personal ID does not affect another player");
            challenge.setEnabled(false);
            player.getHungerManager().add(1, 0.5F);
            assertNutrition(context, player, 11, 6, "Disabled challenge does not block restoration");
            challenge.setEnabled(true);
            state.getPersonalActiveChallengeIds(player.getUuid()).clear();

            CycleManager.activateChallenge(server, state, InfiniteDietChallenge.ID);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200));
            CycleManager.activateChallenge(server, state, "test_easy");
            new ItemStack(Items.COOKED_BEEF).finishUsing(world, player);
            assertNutrition(context, player, 19, 18.8F, "Food restores vanilla nutrition after stopping");
            context.assertTrue(player.hasStatusEffect(StatusEffects.SPEED), "Unrelated effects survive stopping");

            reconnectId = late.getUuid();
            CycleManager.activateChallenge(server, state, InfiniteDietChallenge.ID);
            manager.remove(late);
            CycleManager.activateChallenge(server, state, "test_easy");
            late = createPlayer(context, reconnectId, players);
            late.getHungerManager().add(2, 0.5F);
            assertNutrition(context, late, 10, 4, "Challenge ending while offline leaves no stale restriction");
        } finally {
            challenge.setEnabled(true);
            for (var player : players) {
                if (manager.getPlayerList().contains(player)) manager.remove(player);
            }
            server.setDifficulty(oldDifficulty, true);
            world.getPersistentStateManager().set(CurseCycleState.STATE_KEY,
                    CurseCycleState.createFromNbt(oldState, world.getRegistryManager()));
        }
        context.complete();
    }

    private static void setNutrition(ServerPlayerEntity player, int food, float saturation) {
        player.getHungerManager().setFoodLevel(food);
        player.getHungerManager().setSaturationLevel(saturation);
    }

    private static void assertNutrition(TestContext context, ServerPlayerEntity player,
                                        int food, float saturation, String message) {
        context.assertEquals(food, player.getHungerManager().getFoodLevel(), message + " (food)");
        context.assertTrue(Math.abs(saturation - player.getHungerManager().getSaturationLevel()) < 0.0001F,
                message + " (saturation: " + player.getHungerManager().getSaturationLevel() + ")");
    }

    private static ServerPlayerEntity createPlayer(TestContext context, UUID id, List<ServerPlayerEntity> players) {
        var server = context.getWorld().getServer();
        var client = ConnectedClientData.createDefault(new GameProfile(id, "diet-" + id.toString().substring(0, 8)), false);
        var player = new ServerPlayerEntity(server, context.getWorld(), client.gameProfile(), client.syncedOptions());
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, player, client);
        player.changeGameMode(GameMode.SURVIVAL);
        players.add(player);
        return player;
    }
}
