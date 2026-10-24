package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.FloorIsLavaChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.UUID;

public class FloorIsLavaGameTest implements FabricGameTest {
    // Synchronous: no other game test can change the shared challenge state mid-test.
    // Advance player ages explicitly to exercise the cooldown without wall-clock waits.
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void supportDamageAndLifecycle(TestContext context) throws Exception {
        var world = context.getWorld();
        var server = world.getServer();
        var manager = server.getPlayerManager();
        var state = CurseCycleState.getServerState(server);
        var challenge = ChallengeRegistry.get(FloorIsLavaChallenge.ID);
        BlockPos floor = context.getAbsolutePos(new BlockPos(2, 3, 2));
        world.setBlockState(floor, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc list");
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc activate " + FloorIsLavaChallenge.ID);
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc active");
        context.assertTrue(state.getActiveChallengeIds().contains(FloorIsLavaChallenge.ID), "The activate command selects the challenge");
        ServerPlayerEntity player = createPlayer(context, UUID.randomUUID());
        move(player, floor, 0.5, 1, 0.5);

        var chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
        chest.addEnchantment(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.PROTECTION), 4);
        player.equipStack(EquipmentSlot.CHEST, chest);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 4));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 0));
        player.playerTick();
        context.assertTrue(player.getArmor() > 0, "Control: armor attributes are applied");
        player.timeUntilRegen = 20;
        tick(context);
        health(context, player, 14, "Immediate damage must bypass login protection, armor, enchantments, resistance and hit cooldown");
        challenge.onStart(server, player);
        challenge.onPlayerJoin(server, player);
        tick(context);
        health(context, player, 14, "Repeated start/join/tick calls in the same tick must not duplicate damage");
        for (int i = 0; i < 19; i++) advance(context);
        health(context, player, 14, "No second pulse before 20 ticks");
        player.timeUntilRegen = 20;
        advance(context);
        health(context, player, 8, "Exactly six damage after 20 ticks, even during attack invulnerability");
        player.clearStatusEffects();

        // Every collision shape is checked at the feet, including off-center support.
        BlockState[] supports = {Blocks.STONE.getDefaultState(), Blocks.OAK_PLANKS.getDefaultState(),
                Blocks.OAK_SLAB.getDefaultState(), Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Blocks.OAK_STAIRS.getDefaultState(), Blocks.OAK_FENCE.getDefaultState(),
                Blocks.COBBLESTONE_WALL.getDefaultState(), Blocks.WHITE_CARPET.getDefaultState()};
        double[] heights = {1, 1, 0.5, 1, 1, 1.5, 1.5, 1.0 / 16};
        for (int i = 0; i < supports.length; i++) {
            world.setBlockState(floor, supports[i], Block.NOTIFY_LISTENERS);
            fresh(context, player);
            move(player, floor, 0.5, heights[i], 0.25);
            tick(context);
            health(context, player, 14, "Solid support must hurt: " + supports[i]);
        }
        world.setBlockState(floor, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        fresh(context, player);
        move(player, floor, -0.2, 1, 0.5);
        tick(context);
        health(context, player, 14, "A block under the edge of the feet must count");

        fresh(context, player);
        move(player, floor, 0.5, 1.1, 0.5);
        tick(context);
        health(context, player, 20, "Air above a floor must be safe");
        player.startFallFlying();
        tick(context);
        health(context, player, 20, "Elytra flight without foot contact must be safe");
        player.stopFallFlying();
        move(player, floor, -player.getWidth() / 2.0, 0.2, 0.5);
        tick(context);
        health(context, player, 20, "Touching a wall beside the feet must be safe");

        world.setBlockState(floor, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        for (var block : new net.minecraft.block.Block[]{Blocks.WATER, Blocks.LAVA, Blocks.LADDER, Blocks.VINE}) {
            world.setBlockState(floor.up(), block.getDefaultState(), Block.NOTIFY_LISTENERS);
            move(player, floor, 0.5, 1.1, 0.5);
            tick(context);
            health(context, player, 20, "No solid support in " + block);
        }
        world.setBlockState(floor.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(floor, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        // Touching water does not exempt standing on a solid lake bottom.
        world.setBlockState(floor.up(), Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
        move(player, floor, 0.5, 1, 0.5);
        tick(context);
        health(context, player, 14, "Solid support under water still counts");
        world.setBlockState(floor.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);

        for (Entity vehicle : new Entity[]{new BoatEntity(world, floor.getX(), floor.getY() + 1, floor.getZ()),
                new MinecartEntity(world, floor.getX(), floor.getY() + 1, floor.getZ())}) {
            fresh(context, player);
            context.assertTrue(player.startRiding(vehicle, true), "Control: player mounted vehicle");
            move(player, floor, 0.5, 1, 0.5);
            tick(context);
            health(context, player, 20, "Boat/minecart riders must be safe even with solid blocks below");
            player.stopRiding();
            vehicle.discard();
        }

        for (GameMode mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
            player.changeGameMode(mode);
            move(player, floor, 0.5, 1, 0.5);
            tick(context);
            health(context, player, 20, "Exempt game mode " + mode);
        }
        player.changeGameMode(GameMode.ADVENTURE);
        move(player, floor, 0.5, 1, 0.5);
        tick(context);
        health(context, player, 14, "Adventure must be affected immediately");
        player.changeGameMode(GameMode.SURVIVAL);

        fresh(context, player);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0));
        context.assertTrue(player.getAbsorptionAmount() == 4, "Control: absorption grants four points");
        tick(context);
        health(context, player, 18, "Vanilla absorption still absorbs damage");
        context.assertTrue(player.getAbsorptionAmount() == 0, "Absorption was consumed");
        player.clearStatusEffects();
        fresh(context, player);
        player.setHealth(4);
        player.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        tick(context);
        context.assertTrue(player.isAlive() && player.getOffHandStack().isEmpty(), "A vanilla totem must still work");
        player.clearStatusEffects();
        player.setAbsorptionAmount(0);
        fresh(context, player);
        player.setHealth(6);
        tick(context);
        context.assertFalse(player.isAlive(), "Floor damage must be lethal");
        player = manager.respawnPlayer(player, false, Entity.RemovalReason.KILLED);
        player.networkHandler.player = player;
        move(player, floor, 0.5, 1, 0.5);
        tick(context);
        health(context, player, 14, "A replacement respawn entity must immediately receive damage");

        var late = createPlayer(context, UUID.randomUUID());
        move(late, floor, 0.5, 1, 0.5);
        tick(context);
        health(context, late, 14, "Late join has its own immediate pulse");
        health(context, player, 14, "Another player's pulse cannot alter the first player's clock");
        manager.remove(late);
        UUID reconnect = player.getUuid();
        manager.remove(player);
        player = createPlayer(context, reconnect);
        move(player, floor, 0.5, 1, 0.5);
        tick(context);
        health(context, player, 8, "Reconnect preserves saved health and immediately applies another six damage");

        var persistence = server.getOverworld().getPersistentStateManager();
        persistence.save();
        var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                SharedConstants.getGameVersion().getSaveVersion().getId());
        state = CurseCycleState.createFromNbt(saved.getCompound("data"), world.getRegistryManager());
        context.assertTrue(state.getActiveChallengeIds().contains(FloorIsLavaChallenge.ID), "Active ID is saved on disk");
        persistence.set(CurseCycleState.STATE_KEY, state);
        fresh(context, player);
        tick(context);
        health(context, player, 14, "Reloaded shared state resumes damage");

        var nether = server.getWorld(World.NETHER);
        BlockPos destination = new BlockPos(floor.getX(), 80, floor.getZ());
        nether.setBlockState(destination, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        fresh(context, player);
        player.teleport(nether, destination.getX() + 0.5, destination.getY() + 1, destination.getZ() + 0.5, 0, 0);
        player.onTeleportationDone(); // The real client acknowledges the dimension transfer.
        tick(context);
        health(context, player, 14, "Damage works after a dimension transfer");
        player.teleport(world, floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 0, 0);
        player.onTeleportationDone();
        nether.setBlockState(destination, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);

        server.getCommandManager().executeWithPrefix(server.getCommandSource(),
                "fdc activate \"double_challenge:the_floor_is_lava,no_sprint\"");
        player.setHealth(20);
        tick(context);
        health(context, player, 14, "Damage works in double challenge");
        player.setSprinting(true);
        context.assertFalse(player.isSprinting(), "The other challenge remains effective");
        CycleManager.activateChallenge(server, state, "no_sprint");
        player.setHealth(20);
        advance(context);
        health(context, player, 20, "Ending the floor challenge stops damage");

        state.getPersonalActiveChallengeIds(player.getUuid()).add(FloorIsLavaChallenge.ID);
        tick(context);
        health(context, player, 14, "Personal dispatch works for the selected player");
        late = createPlayer(context, UUID.randomUUID());
        move(late, floor, 0.5, 1, 0.5);
        tick(context);
        health(context, late, 20, "Personal dispatch does not affect another player");
        manager.remove(late);
        reconnect = player.getUuid();
        manager.remove(player);
        state.getPersonalActiveChallengeIds(reconnect).clear();
        player = createPlayer(context, reconnect);
        move(player, floor, 0.5, 1, 0.5);
        tick(context);
        health(context, player, 14, "A challenge ended while offline must preserve saved health without more damage");
        state.getPersonalActiveChallengeIds(player.getUuid()).clear();
        challenge.onStop(server, player);
        challenge.setEnabled(false);
        state.getActiveChallengeIds().add(FloorIsLavaChallenge.ID);
        player.setHealth(20);
        tick(context);
        health(context, player, 20, "Disabled challenges cannot damage players");
        challenge.setEnabled(true);
        state.getActiveChallengeIds().remove(FloorIsLavaChallenge.ID);
        manager.remove(player);
        context.complete();
    }

    private static void fresh(TestContext context, ServerPlayerEntity player) {
        CycleManager.activateChallenge(context.getWorld().getServer(),
                CurseCycleState.getServerState(context.getWorld().getServer()), FloorIsLavaChallenge.ID);
        player.setHealth(20);
    }

    private static void tick(TestContext context) {
        CycleManager.onServerTick(context.getWorld().getServer(), CurseCycleMod.getConfig());
    }

    private static void advance(TestContext context) {
        context.getWorld().getServer().getPlayerManager().getPlayerList().forEach(p -> p.age++);
        tick(context);
    }

    private static void health(TestContext context, ServerPlayerEntity player, float expected, String message) {
        context.assertTrue(Math.abs(player.getHealth() - expected) < 0.001,
                message + ": expected=" + expected + ", actual=" + player.getHealth());
    }

    private static void move(ServerPlayerEntity player, BlockPos pos, double x, double y, double z) {
        player.refreshPositionAndAngles(pos.getX() + x, pos.getY() + y, pos.getZ() + z, 0, 0);
    }

    private static ServerPlayerEntity createPlayer(TestContext context, UUID id) {
        var server = context.getWorld().getServer();
        var client = ConnectedClientData.createDefault(new GameProfile(id, "lava-" + id.toString().substring(0, 8)), false);
        var player = new ServerPlayerEntity(server, context.getWorld(), client.gameProfile(), client.syncedOptions());
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, player, client);
        player.changeGameMode(GameMode.SURVIVAL);
        return player;
    }
}
