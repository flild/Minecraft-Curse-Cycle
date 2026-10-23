package com.cursecycle;

import com.cursecycle.challenge.impl.FloorIsWaterChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.UUID;

public class FloorIsWaterGameTest implements FabricGameTest {
    // Keep changes to the shared challenge state in one synchronous test.
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void waterContactAndLifecycle(TestContext context) throws Exception {
        var world = context.getWorld();
        var server = world.getServer();
        var manager = server.getPlayerManager();
        var state = CurseCycleState.getServerState(server);
        BlockPos water = context.getAbsolutePos(new BlockPos(2, 3, 2));
        BlockPos dry = context.getAbsolutePos(new BlockPos(5, 3, 5));
        world.setBlockState(water, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);

        CycleManager.activateChallenge(server, state, FloorIsWaterChallenge.ID);
        ServerPlayerEntity player = createPlayer(context, UUID.randomUUID());
        move(player, water, 0.5, 0.1, 0.5);
        // No login ticks elapsed: /kill must also bypass the join protection window.
        player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        player.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 4));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 4));
        tick(context);
        context.assertFalse(player.isAlive(), "Source water must kill despite armor, potions, totem and login protection");

        player = respawn(context, player);
        move(player, water, 0.5, 0.1, 0.5);
        tick(context);
        context.assertFalse(player.isAlive(), "A respawned player must die again on contact with water");
        player = respawn(context, player);

        player.changeGameMode(GameMode.CREATIVE);
        move(player, water, 0.5, 0.1, 0.5);
        tick(context);
        context.assertTrue(player.isAlive(), "Creative must be exempt");
        player.changeGameMode(GameMode.SPECTATOR);
        tick(context);
        context.assertTrue(player.isAlive(), "Spectator must be exempt");
        player.changeGameMode(GameMode.ADVENTURE);
        tick(context);
        context.assertFalse(player.isAlive(), "Switching to Adventure while in water must kill on the next tick");
        player = respawn(context, player);
        player.changeGameMode(GameMode.SURVIVAL);

        // A level-7 flow is only 1/9 block deep; air above it must remain safe.
        world.setBlockState(water, Blocks.WATER.getDefaultState().with(net.minecraft.block.FluidBlock.LEVEL, 7),
                Block.NOTIFY_LISTENERS);
        move(player, water, 0.5, 0.5, 0.5);
        tick(context);
        context.assertTrue(player.isAlive(), "Standing above shallow flow must be safe");
        move(player, water, 0.5, 0.05, 0.5);
        tick(context);
        context.assertFalse(player.isAlive(), "Even the shallowest flowing water must kill on contact");
        player = respawn(context, player);

        world.setBlockState(water, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
        move(player, water, -0.2, 0.1, 0.5);
        context.assertFalse(player.getBlockPos().equals(water), "Control: feet center is outside the water block");
        tick(context);
        context.assertFalse(player.isAlive(), "Contact at the side of the hitbox must kill");
        player = respawn(context, player);
        move(player, water, 0.5, -1.7, 0.5);
        tick(context);
        context.assertFalse(player.isAlive(), "Water at the head of the hitbox must kill");
        player = respawn(context, player);

        world.setBlockState(water, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.WATERLOGGED, true),
                Block.NOTIFY_LISTENERS);
        move(player, water, 0.5, 0.6, 0.5);
        tick(context);
        context.assertFalse(player.isAlive(), "Water inside waterlogged blocks must kill");
        player = respawn(context, player);

        world.setBlockState(water, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3),
                Block.NOTIFY_LISTENERS);
        move(player, water, 0.5, 0.1, 0.5);
        tick(context);
        context.assertTrue(player.isAlive(), "Water cauldrons must be safe");
        world.setBlockState(water, Blocks.POINTED_DRIPSTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        tick(context);
        context.assertTrue(player.isAlive(), "Dripstone without a water fluid must be safe");
        world.setBlockState(water, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setWeather(0, 6000, true, false);
        tick(context);
        context.assertTrue(player.isAlive(), "Rain without water blocks must be safe");
        world.setWeather(6000, 0, false, false);

        move(player, dry, 0.5, 0, 0.5);
        world.setBlockState(water, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
        ServerPlayerEntity latePlayer = createPlayer(context, UUID.randomUUID());
        move(latePlayer, water, 0.5, 0.1, 0.5);
        tick(context);
        context.assertFalse(latePlayer.isAlive(), "Late joins must inherit the global challenge");
        context.assertTrue(player.isAlive(), "A second player on dry land must remain safe");
        manager.remove(latePlayer);

        UUID reconnectId = player.getUuid();
        manager.remove(player);
        player = createPlayer(context, reconnectId);
        move(player, water, 0.5, 0.1, 0.5);
        tick(context);
        context.assertFalse(player.isAlive(), "Reconnecting must not clear the challenge");
        player = respawn(context, player);
        move(player, dry, 0.5, 0, 0.5);

        // Exercise PersistentStateManager's disk path, not Challenge's unused NBT hooks.
        var persistence = server.getOverworld().getPersistentStateManager();
        persistence.save();
        var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                SharedConstants.getGameVersion().getSaveVersion().getId());
        var restored = CurseCycleState.createFromNbt(saved.getCompound("data"), world.getRegistryManager());
        context.assertTrue(restored.getActiveChallengeIds().contains(FloorIsWaterChallenge.ID),
                "The active ID must actually be written to disk");
        persistence.set(CurseCycleState.STATE_KEY, restored);
        state = restored;
        move(player, water, 0.5, 0.1, 0.5);
        tick(context);
        context.assertFalse(player.isAlive(), "Restored shared state must resume the lethal effect");
        player = respawn(context, player);

        var nether = server.getWorld(World.NETHER);
        BlockPos netherWater = new BlockPos(water.getX(), 80, water.getZ());
        context.assertTrue(nether.setBlockState(netherWater, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS),
                "Control: water must be placed within the Nether's height range");
        player.teleport(nether, netherWater.getX() + 0.5, netherWater.getY() + 0.1, netherWater.getZ() + 0.5, 0, 0);
        context.assertTrue(player.getServerWorld() == nether, "Control: player reached the Nether");
        context.assertEquals(netherWater, player.getBlockPos(), "Control: player reached the water position");
        context.assertTrue(nether.getFluidState(netherWater).isIn(net.minecraft.registry.tag.FluidTags.WATER),
                "Control: destination still contains water");
        context.assertTrue(player.isInTeleportationState(), "Control: dimension transfer protection is active");
        tick(context);
        context.assertFalse(player.isAlive(), "The challenge must apply after a dimension change: "
                + player.getBoundingBox() + " mode=" + player.interactionManager.getGameMode()
                + " active=" + state.getActiveChallengeIds() + " listed=" + manager.getPlayerList().contains(player));
        nether.setBlockState(netherWater, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        player = respawn(context, player);
        player.teleport(world, dry.getX() + 0.5, dry.getY(), dry.getZ() + 0.5, 0, 0);

        CycleManager.activateChallenge(server, state, "double_challenge:floor_is_water,no_sprint");
        move(player, water, 0.5, 0.1, 0.5);
        tick(context);
        context.assertFalse(player.isAlive(), "Water must remain lethal alongside another challenge");
        player = respawn(context, player);

        CycleManager.activateChallenge(server, state, "test_easy");
        move(player, water, 0.5, 0.1, 0.5);
        tick(context);
        context.assertTrue(player.isAlive(), "Water must be safe after the challenge stops");
        manager.remove(player);
        context.complete();
    }

    private static void tick(TestContext context) {
        CycleManager.onServerTick(context.getWorld().getServer(), CurseCycleMod.getConfig());
    }

    private static ServerPlayerEntity respawn(TestContext context, ServerPlayerEntity player) {
        var respawned = context.getWorld().getServer().getPlayerManager()
                .respawnPlayer(player, false, Entity.RemovalReason.KILLED);
        // The real client's respawn request updates this reference outside PlayerManager.
        respawned.networkHandler.player = respawned;
        return respawned;
    }

    private static void move(ServerPlayerEntity player, BlockPos pos, double x, double y, double z) {
        player.refreshPositionAndAngles(pos.getX() + x, pos.getY() + y, pos.getZ() + z, 0, 0);
    }

    private static ServerPlayerEntity createPlayer(TestContext context, UUID id) {
        var server = context.getWorld().getServer();
        var client = ConnectedClientData.createDefault(new GameProfile(id, "water-" + id.toString().substring(0, 8)), false);
        var player = new ServerPlayerEntity(server, context.getWorld(), client.gameProfile(), client.syncedOptions());
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, player, client);
        player.changeGameMode(GameMode.SURVIVAL);
        return player;
    }
}
