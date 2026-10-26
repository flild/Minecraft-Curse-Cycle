package com.cursecycle;

import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.DamageTeleportChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class DamageTeleportGameTest implements FabricGameTest {
    private static final int FLAGS = Block.FORCE_STATE | Block.NOTIFY_LISTENERS;

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "damage_teleport", tickLimit = 500)
    public void safeTeleportActualDamageAndLifecycle(TestContext context) throws Exception {
        var world = context.getWorld();
        var origin = Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(8, 3, 8)));
        try (var scenario = new ChallengeScenario(context)) {
            prepare(world, origin, true);
            var player = scenario.player();
            expireLoginProtection(player);
            reset(player, world, origin);
            scenario.activate("damage_teleport");
            at(context, player, origin, "Activation does not teleport");
            player.setYaw(37);
            player.setPitch(-12);
            player.setVelocity(0.1, -0.1, 0.2);
            player.fallDistance = 8;
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);
            context.assertEquals(19F, player.getHealth(), "Only original damage");
            context.assertEquals(37F, player.getYaw(), "Yaw preserved");
            context.assertEquals(-12F, player.getPitch(), "Pitch preserved");
            context.assertEquals(Vec3d.ZERO, player.getVelocity(), "Velocity reset");
            context.assertEquals(0F, player.fallDistance, "Fall distance reset");
            Vec3d after = player.getPos();
            player.damage(player.getDamageSources().generic(), 1);
            at(context, player, after, "Ignored i-frame does not teleport");
            player.setHealth(18);
            at(context, player, after, "Direct health reduction is not damage");

            reset(player, world, origin);
            player.setYaw(0);
            player.setPitch(0);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHIELD));
            player.setCurrentHand(Hand.MAIN_HAND);
            for (int i = 0; i < 6; i++) player.playerTick();
            player.teleport(world, origin.x, origin.y, origin.z, 0, 0);
            var zombie = EntityType.ZOMBIE.create(world);
            zombie.setPosition(origin.add(0, 0, 2));
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().mobAttack(zombie), 4);
            context.assertEquals(20F, player.getHealth(), "Control: shield really blocks damage");
            at(context, player, origin, "Shield-blocked hit does not teleport");
            player.stopUsingItem();
            zombie.discard();
            player.getInventory().clear();

            reset(player, world, origin);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1000));
            player.damage(player.getDamageSources().generic(), 1);
            context.assertEquals(20F, player.getHealth(), "Absorption-only damage leaves health unchanged");
            moved(context, player, origin);

            reset(player, world, origin);
            var boat = new BoatEntity(world, origin.x, origin.y, origin.z);
            world.spawnEntity(boat);
            player.startRiding(boat, true);
            Vec3d riding = player.getPos();
            player.damage(player.getDamageSources().generic(), 1);
            at(context, player, riding, "Passenger does not teleport");
            context.assertTrue(player.getVehicle() == boat, "No forced dismount");
            player.stopRiding();
            boat.discard();

            prepare(world, origin, false);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            at(context, player, origin, "No safe support gives no teleport");
            prepare(world, origin, true);
            ChallengeRegistry.get("damage_teleport").onTick(world.getServer(), player);
            at(context, player, origin, "No queued retry when support later appears");

            prepare(world, origin, false);
            BlockPos east = BlockPos.ofFloored(origin.add(5, 0, 0));
            world.setBlockState(east, Blocks.STONE.getDefaultState(), FLAGS);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            at(context, player, origin.add(5, 1, 0), "First safe height +1 is selected");
            prepare(world, origin, false);
            world.setBlockState(east.down(3), Blocks.STONE.getDefaultState(), FLAGS);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            at(context, player, origin.add(5, -2, 0), "Height -2 remains within search");

            prepare(world, origin, true);
            reset(player, world, origin);
            checkHazardsAndChunks(context, player, world, origin);

            reset(player, world, origin);
            player.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 3));
            player.getInventory().getStack(10).set(DataComponentTypes.CUSTOM_NAME, Text.literal("teleport-origin"));
            scenario.activate("double_challenge:damage_teleport,damage_slot_drop");
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);
            var drops = world.getEntitiesByClass(ItemEntity.class,
                    new Box(origin.x - 1, origin.y - 1, origin.z - 1, origin.x + 1, origin.y + 2, origin.z + 1),
                    item -> item.getStack().isOf(Items.DIAMOND)
                            && item.getStack().get(DataComponentTypes.CUSTOM_NAME) != null
                            && item.getStack().get(DataComponentTypes.CUSTOM_NAME).getString().equals("teleport-origin"));
            context.assertEquals(1, drops.size(), "Exactly one drop at original damage position");
            context.assertTrue(drops.getFirst().getPos().squaredDistanceTo(origin) < 1.0E-12, "Drop precedes teleport even with reverse ID order");
            context.assertEquals(3, drops.getFirst().getStack().getCount(), "Full stack preserved");
            context.assertTrue(player.getInventory().getStack(10).isEmpty(), "Original slot emptied once");

            scenario.activate("damage_teleport");
            reset(player, world, origin);
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.damage(player.getDamageSources().generic(), 100);
            context.assertTrue(player.isAlive(), "Totem saved the player");
            moved(context, player, origin);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 100);
            context.assertTrue(!player.isAlive(), "Lethal control has no totem");
            at(context, player, origin, "Death without totem does not teleport");
            player = scenario.respawn(player, false);
            expireLoginProtection(player);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);
            player = scenario.respawn(player, true);
            expireLoginProtection(player);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);

            var late = scenario.player();
            expireLoginProtection(late);
            reset(late, world, origin);
            late.damage(late.getDamageSources().generic(), 1);
            moved(context, late, origin);
            for (var mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
                player.changeGameMode(mode);
                context.assertTrue(!ChallengeActivity.active(player, "damage_teleport"), "Excluded mode " + mode);
            }
            player.changeGameMode(GameMode.ADVENTURE);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);
            player.changeGameMode(GameMode.SURVIVAL);

            scenario.state.getActiveChallengeIds().clear();
            scenario.state.getPersonalActiveChallengeIds(player.getUuid()).add("damage_teleport");
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);
            reset(late, world, origin);
            late.damage(late.getDamageSources().generic(), 1);
            at(context, late, origin, "Personal ID does not affect the other player");
            scenario.state.getPersonalActiveChallengeIds(player.getUuid()).clear();
            scenario.activate("damage_teleport");

            var persistence = world.getPersistentStateManager();
            scenario.state.markDirty();
            persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(saved.getCompound("data"), world.getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, loaded);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);
            CycleManager.activateChallenge(world.getServer(), loaded, "no_sprint");
            Vec3d ended = player.getPos();
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().generic(), 1);
            at(context, player, ended, "Stopping does not restore position or react to later damage");
            player.setSprinting(true);
            context.assertTrue(!player.isSprinting(), "Other challenge remains active");
        }
        context.waitAndRun(5, context::complete);
    }

    private static void checkHazardsAndChunks(TestContext context, ServerPlayerEntity player, ServerWorld world, Vec3d origin) {
        Vec3d target = origin.add(5, 0, 0);
        BlockPos feet = BlockPos.ofFloored(target);
        context.assertTrue(DamageTeleportChallenge.isSafe(player, world, target), "Control: ordinary stone is safe");
        for (var block : new net.minecraft.block.Block[]{Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.WATER, Blocks.LAVA,
                Blocks.CACTUS, Blocks.SWEET_BERRY_BUSH, Blocks.POWDER_SNOW, Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_GATEWAY}) {
            world.setBlockState(feet.down(), (block == Blocks.SOUL_FIRE ? Blocks.SOUL_SOIL : Blocks.STONE).getDefaultState(), FLAGS);
            world.setBlockState(feet, block.getDefaultState(), FLAGS);
            context.assertTrue(world.getBlockState(feet).isOf(block), "Hazard control actually exists: " + block);
            context.assertTrue(!DamageTeleportChallenge.isSafe(player, world, target), "Unsafe body block " + block);
            world.setBlockState(feet, Blocks.AIR.getDefaultState(), FLAGS);
        }
        world.setBlockState(feet.down(), Blocks.MAGMA_BLOCK.getDefaultState(), FLAGS);
        context.assertTrue(!DamageTeleportChallenge.isSafe(player, world, target), "Magma support rejected");
        world.setBlockState(feet.down(), Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP)
                .with(SlabBlock.WATERLOGGED, true), FLAGS);
        context.assertTrue(!DamageTeleportChallenge.isSafe(player, world, target), "Waterlogged support rejected");
        world.setBlockState(feet.down(), Blocks.STONE.getDefaultState(), FLAGS);
        world.setBlockState(feet.up(), Blocks.STONE.getDefaultState(), FLAGS);
        context.assertTrue(!DamageTeleportChallenge.isSafe(player, world, target), "Whole body needs clearance");
        world.setBlockState(feet.up(), Blocks.AIR.getDefaultState(), FLAGS);
        var border = world.getWorldBorder();
        double centerX = border.getCenterX(), centerZ = border.getCenterZ(), size = border.getSize();
        try {
            border.setCenter(origin.x, origin.z);
            border.setSize(4);
            context.assertTrue(!DamageTeleportChallenge.isSafe(player, world, target), "World border respected");
        } finally {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
        Vec3d distant = origin.add(4096, 0, 0);
        int x = BlockPos.ofFloored(distant).getX() >> 4, z = BlockPos.ofFloored(distant).getZ() >> 4;
        context.assertTrue(!world.getChunkManager().isChunkLoaded(x, z), "Unloaded control chunk");
        context.assertTrue(!DamageTeleportChallenge.isSafe(player, world, distant), "Unloaded candidate rejected");
        context.assertTrue(!world.getChunkManager().isChunkLoaded(x, z), "Safety query did not load the chunk");
    }

    private static void prepare(ServerWorld world, Vec3d origin, boolean floor) {
        BlockPos center = BlockPos.ofFloored(origin);
        for (BlockPos pos : BlockPos.iterate(center.add(-8, -3, -8), center.add(8, 4, 8)))
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), FLAGS);
        if (floor) for (BlockPos pos : BlockPos.iterate(center.add(-8, -1, -8), center.add(8, -1, 8)))
            world.setBlockState(pos, Blocks.STONE.getDefaultState(), FLAGS);
        world.setBlockState(center.down(), Blocks.STONE.getDefaultState(), FLAGS);
    }

    private static void reset(ServerPlayerEntity player, ServerWorld world, Vec3d origin) {
        player.stopUsingItem();
        player.clearStatusEffects();
        player.setHealth(20);
        player.setAbsorptionAmount(0);
        player.timeUntilRegen = 0;
        player.teleport(world, origin.x, origin.y, origin.z, 0, 0);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
    }

    private static void expireLoginProtection(ServerPlayerEntity player) {
        for (int i = 0; i < 61; i++) player.tick();
    }

    private static void at(TestContext context, ServerPlayerEntity player, Vec3d expected, String message) {
        context.assertTrue(player.getPos().squaredDistanceTo(expected) < 1.0E-12,
                message + ": " + player.getPos() + " expected " + expected);
    }

    private static void moved(TestContext context, ServerPlayerEntity player, Vec3d origin) {
        context.assertTrue(Math.abs(Math.hypot(player.getX() - origin.x, player.getZ() - origin.z) - 5) < 1.0E-6,
                "Exactly one five-block horizontal teleport: " + player.getPos() + " from " + origin);
        context.assertTrue(player.getServerWorld() == context.getWorld(), "Dimension preserved");
    }
}
