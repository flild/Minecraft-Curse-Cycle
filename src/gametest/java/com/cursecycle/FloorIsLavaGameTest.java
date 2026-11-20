package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.FloorIsLavaPlacements;
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
import net.minecraft.block.BedBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BedPart;
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
import net.minecraft.registry.Registries;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.UUID;

public class FloorIsLavaGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void playerPlacementIsRoundLocalAndReplacementsReleaseMarks(TestContext context) {
        var world = context.getWorld();
        var floor = context.getAbsolutePos(new BlockPos(2, 3, 2));
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            var visitor = scenario.player();
            var lava = ChallengeRegistry.get(FloorIsLavaChallenge.ID);
            world.setBlockState(floor.down(), Blocks.STONE.getDefaultState());
            world.setBlockState(floor, Blocks.AIR.getDefaultState());
            place(context, player, floor, Items.STONE, Hand.MAIN_HAND);
            context.assertFalse(FloorIsLavaPlacements.isSafe(world, floor), "Inactive placements are not recorded");
            scenario.activate(FloorIsLavaChallenge.ID);
            move(player, floor, 0.5, 1, 0.5);
            lava.onTick(world.getServer(), player);
            health(context, player, 17, "A block placed before the round remains dangerous");

            world.setBlockState(floor, Blocks.AIR.getDefaultState());
            place(context, player, floor, Items.STONE, Hand.OFF_HAND);
            context.assertTrue(FloorIsLavaPlacements.isSafe(world, floor), "A successful offhand placement is recorded");
            move(visitor, floor, 0.5, 1, 0.5);
            lava.onTick(world.getServer(), visitor);
            health(context, visitor, 20, "A player's platform also protects other players");
            context.assertFalse(FloorIsLavaPlacements.isSafe(world.getServer().getWorld(World.NETHER), floor),
                    "Coordinates in other dimensions do not inherit safety");
            lava.onPlayerLeave(world.getServer(), player);
            context.assertTrue(FloorIsLavaPlacements.isSafe(world, floor), "Disconnecting does not clear the round's marks");
            scenario.activate(FloorIsLavaChallenge.ID);
            context.assertFalse(FloorIsLavaPlacements.isSafe(world, floor), "Reactivating the same challenge starts fresh");

            world.setBlockState(floor, Blocks.AIR.getDefaultState());
            place(context, player, floor, Items.OAK_SLAB, Hand.MAIN_HAND);
            world.setBlockState(floor, world.getBlockState(floor).with(SlabBlock.WATERLOGGED, true));
            context.assertTrue(FloorIsLavaPlacements.isSafe(world, floor), "Property changes retain a placed block's mark");
            world.setBlockState(floor, Blocks.STONE.getDefaultState());
            context.assertFalse(FloorIsLavaPlacements.isSafe(world, floor), "Replacement by another block clears safety");
            world.setBlockState(floor, Blocks.AIR.getDefaultState());
            place(context, player, floor, Items.STONE, Hand.MAIN_HAND);
            world.breakBlock(floor, false);
            world.setBlockState(floor, Blocks.STONE.getDefaultState());
            context.assertFalse(FloorIsLavaPlacements.isSafe(world, floor), "Destroyed marks cannot exempt later blocks at the same position");

            scenario.activate("double_challenge:the_floor_is_lava,no_block_placing");
            world.setBlockState(floor, Blocks.AIR.getDefaultState());
            move(player, floor, 3.5, 1, 3.5);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
            useBlock(player, floor.down(), Hand.MAIN_HAND);
            context.assertTrue(world.getBlockState(floor).isAir(), "Control: the other challenge rejects placement");
            context.assertFalse(FloorIsLavaPlacements.isSafe(world, floor), "Rejected interactions do not create marks");

            scenario.activate(FloorIsLavaChallenge.ID);
            place(context, player, floor, Items.STONE, Hand.MAIN_HAND);
            lava.setEnabled(false);
            lava.setEnabled(true);
            context.assertFalse(FloorIsLavaPlacements.isSafe(world, floor), "Disabling clears all placement data");
            world.setBlockState(floor, Blocks.AIR.getDefaultState());
            place(context, player, floor, Items.STONE, Hand.MAIN_HAND);
            scenario.activate("test_easy");
            FloorIsLavaPlacements.tick(world.getServer());
            scenario.activate(FloorIsLavaChallenge.ID);
            context.assertFalse(FloorIsLavaPlacements.isSafe(world, floor), "Ending the round clears its data");
            world.setBlockState(floor, Blocks.AIR.getDefaultState());
            place(context, player, floor, Items.STONE, Hand.MAIN_HAND);
            FloorIsLavaChallenge.clear(world.getServer());
            context.assertFalse(FloorIsLavaPlacements.isSafe(world, floor), "Server-stop cleanup drops all marks");
        } finally {
            ChallengeRegistry.get(FloorIsLavaChallenge.ID).setEnabled(true);
            FloorIsLavaPlacements.clear(world.getServer());
            world.setBlockState(floor, Blocks.AIR.getDefaultState());
            world.setBlockState(floor.down(), Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void personalAssignmentsMultiblocksAndEmptyServerCleanup(TestContext context) {
        var world = context.getWorld();
        var floor = context.getAbsolutePos(new BlockPos(2, 3, 2));
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            var recipient = scenario.player();
            scenario.state.getPersonalActiveChallengeIds(recipient.getUuid()).add(FloorIsLavaChallenge.ID);
            scenario.state.getPersonalActiveChallengeIds(player.getUuid()).add(FloorIsLavaChallenge.ID);
            world.setBlockState(floor.down(), Blocks.STONE.getDefaultState());
            world.setBlockState(floor, Blocks.AIR.getDefaultState());
            place(context, player, floor, Items.OAK_DOOR, Hand.MAIN_HAND);
            context.assertTrue(FloorIsLavaPlacements.isSafe(world, floor), "Personal-only assignment enables placement tracking");
            context.assertTrue(FloorIsLavaPlacements.isSafe(world, floor.up()), "The upper door half is tracked too");
            scenario.state.getPersonalActiveChallengeIds(player.getUuid()).clear();
            ChallengeRegistry.get(FloorIsLavaChallenge.ID).onStop(world.getServer(), player);
            FloorIsLavaPlacements.tick(world.getServer());
            context.assertTrue(FloorIsLavaPlacements.isSafe(world, floor), "Ending one personal assignment preserves another recipient's platforms");
            scenario.state.getPersonalActiveChallengeIds(recipient.getUuid()).clear();
            FloorIsLavaPlacements.tick(world.getServer());
            scenario.state.getPersonalActiveChallengeIds(recipient.getUuid()).add(FloorIsLavaChallenge.ID);
            context.assertFalse(FloorIsLavaPlacements.isSafe(world, floor), "Ending the final personal assignment clears marks");
            world.setBlockState(floor.up(), Blocks.AIR.getDefaultState());
            world.setBlockState(floor, Blocks.AIR.getDefaultState());
            place(context, player, floor, Items.STONE, Hand.MAIN_HAND);
            context.assertTrue(FloorIsLavaPlacements.isSafe(world, floor), "Control: the new personal round records placements");
            var manager = world.getServer().getPlayerManager();
            manager.remove(player);
            manager.remove(recipient);
            scenario.state.getPersonalActiveChallengeIds(recipient.getUuid()).clear();
            FloorIsLavaPlacements.tick(world.getServer());
            scenario.state.getPersonalActiveChallengeIds(recipient.getUuid()).add(FloorIsLavaChallenge.ID);
            context.assertFalse(FloorIsLavaPlacements.isSafe(world, floor), "Cleanup works without online players or onStop callbacks");
        } finally {
            FloorIsLavaPlacements.clear(world.getServer());
            world.setBlockState(floor.up(), Blocks.AIR.getDefaultState());
            world.setBlockState(floor, Blocks.AIR.getDefaultState());
            world.setBlockState(floor.down(), Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    private static void place(TestContext context, ServerPlayerEntity player, BlockPos pos,
                              net.minecraft.item.Item item, Hand hand) {
        move(player, pos, 3.5, 1, 3.5);
        player.setStackInHand(hand, new ItemStack(item));
        useBlock(player, pos.down(), hand);
        context.assertTrue(!context.getWorld().getBlockState(pos).isAir(), "Control: vanilla places " + item);
    }

    private static void useBlock(ServerPlayerEntity player, BlockPos base, Hand hand) {
        var hit = new BlockHitResult(Vec3d.ofCenter(base).add(0, 0.5, 0), Direction.UP, base, false);
        player.interactionManager.interactBlock(player, player.getServerWorld(), player.getStackInHand(hand), hand, hit);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void safeSurfacesSleepingAndReturnToFloor(TestContext context) {
        var world = context.getWorld();
        var server = world.getServer();
        var state = CurseCycleState.getServerState(server);
        var challenge = ChallengeRegistry.get(FloorIsLavaChallenge.ID);
        BlockPos floor = context.getAbsolutePos(new BlockPos(2, 3, 2));
        ServerPlayerEntity player = createPlayer(context, UUID.randomUUID());
        try {
            fresh(context, player);
            for (var color : DyeColor.values()) {
                for (var part : BedPart.values()) {
                    var bed = Registries.BLOCK.get(Identifier.ofVanilla(color.getName() + "_bed"))
                            .getDefaultState().with(BedBlock.PART, part);
                    safeSupport(context, player, floor, bed, 9.0 / 16);
                }
                safeSupport(context, player, floor, Registries.BLOCK.get(Identifier.ofVanilla(color.getName() + "_wool"))
                        .getDefaultState(), 1);
                safeSupport(context, player, floor, Registries.BLOCK.get(Identifier.ofVanilla(color.getName() + "_carpet"))
                        .getDefaultState(), 1.0 / 16);
            }
            for (var axis : Direction.Axis.values()) {
                safeSupport(context, player, floor, Blocks.HAY_BLOCK.getDefaultState()
                        .with(net.minecraft.block.PillarBlock.AXIS, axis), 1);
            }

            // A safe block beside the feet must not exempt an ordinary floor.
            world.setBlockState(floor, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(floor.east(), Blocks.RED_WOOL.getDefaultState(), Block.NOTIFY_LISTENERS);
            move(player, floor, 0.5, 1, 0.5);
            tick(context);
            health(context, player, 17, "Leaving shelter immediately resumes floor damage");
            move(player, floor.east(), 0.5, 1, 0.5);
            tick(context);
            health(context, player, 17, "Entering shelter stops damage without restoring health");
            move(player, floor, 0.5, 1, 0.5);
            tick(context);
            health(context, player, 17, "Safe blocks do not reset the damage cooldown");
            player.age += 20;
            tick(context);
            health(context, player, 14, "Ordinary floor still deals damage once the cooldown expires");

            fresh(context, player);
            var bed = Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.EAST);
            world.setBlockState(floor, bed, Block.NOTIFY_LISTENERS);
            world.setBlockState(floor.east(), bed.with(BedBlock.PART, BedPart.HEAD), Block.NOTIFY_LISTENERS);
            player.sleep(floor.east());
            context.assertTrue(player.isSleeping(), "Control: player is sleeping in a real bed");
            for (int i = 0; i < 40; i++) advance(context);
            health(context, player, 20, "Sleeping in a bed is safe across damage intervals");
            player.wakeUp(true, true);
            move(player, floor, 0.5, 9.0 / 16, 0.5);
            tick(context);
            health(context, player, 20, "Standing on the bed after waking remains safe");
            world.setBlockState(floor, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            move(player, floor, 0.5, 1, 0.5);
            tick(context);
            health(context, player, 17, "Waking and leaving the bed restores normal floor damage");
        } finally {
            if (player.isSleeping()) player.wakeUp(true, true);
            challenge.onStop(server, player);
            state.getActiveChallengeIds().clear();
            server.getPlayerManager().remove(player);
            world.setBlockState(floor, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(floor.east(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(floor.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        context.complete();
    }

    private static void safeSupport(TestContext context, ServerPlayerEntity player, BlockPos pos,
                                    BlockState block, double height) {
        context.getWorld().setBlockState(pos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        context.getWorld().setBlockState(pos, block, Block.NOTIFY_LISTENERS);
        for (double x : new double[]{0.5, -0.2}) {
            move(player, pos, x, height, 0.5);
            var box = player.getBoundingBox();
            var feet = new net.minecraft.util.math.Box(box.minX, box.minY - 1.0E-6, box.minZ,
                    box.maxX, box.minY, box.maxZ);
            context.assertTrue(context.getWorld().findSupportingBlockPos(player, feet).isPresent(),
                    "Control: safe surface actually supports the player: " + block);
            for (int i = 0; i < 21; i++) advance(context);
            health(context, player, 20, "Safe surface over stone, including its edge: " + block);
        }
    }

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
        health(context, player, 17, "Immediate damage must bypass login protection, armor, enchantments, resistance and hit cooldown");
        challenge.onStart(server, player);
        challenge.onPlayerJoin(server, player);
        tick(context);
        health(context, player, 17, "Repeated start/join/tick calls in the same tick must not duplicate damage");
        for (int i = 0; i < 19; i++) advance(context);
        health(context, player, 17, "No second pulse before 20 ticks");
        player.timeUntilRegen = 20;
        advance(context);
        health(context, player, 14, "Exactly three damage after 20 ticks, even during attack invulnerability");
        player.clearStatusEffects();

        // Every collision shape is checked at the feet, including off-center support.
        BlockState[] supports = {Blocks.STONE.getDefaultState(), Blocks.OAK_PLANKS.getDefaultState(),
                Blocks.OAK_SLAB.getDefaultState(), Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Blocks.OAK_STAIRS.getDefaultState(), Blocks.OAK_FENCE.getDefaultState(),
                Blocks.COBBLESTONE_WALL.getDefaultState(), Blocks.MOSS_CARPET.getDefaultState()};
        double[] heights = {1, 1, 0.5, 1, 1, 1.5, 1.5, 1.0 / 16};
        for (int i = 0; i < supports.length; i++) {
            world.setBlockState(floor, supports[i], Block.NOTIFY_LISTENERS);
            fresh(context, player);
            move(player, floor, 0.5, heights[i], 0.25);
            tick(context);
            health(context, player, 17, "Solid support must hurt: " + supports[i]);
        }
        world.setBlockState(floor, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        fresh(context, player);
        move(player, floor, -0.2, 1, 0.5);
        tick(context);
        health(context, player, 17, "A block under the edge of the feet must count");

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
        health(context, player, 17, "Solid support under water still counts");
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
        health(context, player, 17, "Adventure must be affected immediately");
        player.changeGameMode(GameMode.SURVIVAL);

        fresh(context, player);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0));
        context.assertTrue(player.getAbsorptionAmount() == 4, "Control: absorption grants four points");
        tick(context);
        health(context, player, 20, "Vanilla absorption still absorbs damage");
        context.assertTrue(player.getAbsorptionAmount() == 1, "Absorption was consumed by three points");
        player.clearStatusEffects();
        fresh(context, player);
        player.setHealth(2);
        player.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        tick(context);
        context.assertTrue(player.isAlive() && player.getOffHandStack().isEmpty(), "A vanilla totem must still work");
        player.clearStatusEffects();
        player.setAbsorptionAmount(0);
        fresh(context, player);
        player.setHealth(3);
        tick(context);
        context.assertFalse(player.isAlive(), "Floor damage must be lethal");
        player = manager.respawnPlayer(player, false, Entity.RemovalReason.KILLED);
        player.networkHandler.player = player;
        move(player, floor, 0.5, 1, 0.5);
        tick(context);
        health(context, player, 17, "A replacement respawn entity must immediately receive damage");

        var late = createPlayer(context, UUID.randomUUID());
        move(late, floor, 0.5, 1, 0.5);
        tick(context);
        health(context, late, 17, "Late join has its own immediate pulse");
        health(context, player, 17, "Another player's pulse cannot alter the first player's clock");
        manager.remove(late);
        UUID reconnect = player.getUuid();
        manager.remove(player);
        player = createPlayer(context, reconnect);
        move(player, floor, 0.5, 1, 0.5);
        tick(context);
        health(context, player, 14, "Reconnect preserves saved health and immediately applies another three damage");

        var persistence = server.getOverworld().getPersistentStateManager();
        persistence.save();
        var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                SharedConstants.getGameVersion().getSaveVersion().getId());
        state = CurseCycleState.createFromNbt(saved.getCompound("data"), world.getRegistryManager());
        context.assertTrue(state.getActiveChallengeIds().contains(FloorIsLavaChallenge.ID), "Active ID is saved on disk");
        persistence.set(CurseCycleState.STATE_KEY, state);
        fresh(context, player);
        tick(context);
        health(context, player, 17, "Reloaded shared state resumes damage");

        var nether = server.getWorld(World.NETHER);
        BlockPos destination = new BlockPos(floor.getX(), 80, floor.getZ());
        nether.setBlockState(destination, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        fresh(context, player);
        player.teleport(nether, destination.getX() + 0.5, destination.getY() + 1, destination.getZ() + 0.5, 0, 0);
        player.onTeleportationDone(); // The real client acknowledges the dimension transfer.
        tick(context);
        health(context, player, 17, "Damage works after a dimension transfer");
        player.teleport(world, floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 0, 0);
        player.onTeleportationDone();
        nether.setBlockState(destination, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);

        server.getCommandManager().executeWithPrefix(server.getCommandSource(),
                "fdc activate \"double_challenge:the_floor_is_lava,no_sprint\"");
        player.setHealth(20);
        tick(context);
        health(context, player, 17, "Damage works in double challenge");
        player.setSprinting(true);
        context.assertFalse(player.isSprinting(), "The other challenge remains effective");
        CycleManager.activateChallenge(server, state, "no_sprint");
        player.setHealth(20);
        advance(context);
        health(context, player, 20, "Ending the floor challenge stops damage");

        state.getPersonalActiveChallengeIds(player.getUuid()).add(FloorIsLavaChallenge.ID);
        tick(context);
        health(context, player, 17, "Personal dispatch works for the selected player");
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
        health(context, player, 17, "A challenge ended while offline must preserve saved health without more damage");
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
