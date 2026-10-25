package com.cursecycle;

import com.cursecycle.challenge.impl.OneTimeMenuChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CakeBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class OneTimeMenuGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void registryIdentityCakeAndPersistence(TestContext context) throws Exception {
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            scenario.activate("one_time_menu");
            player.getHungerManager().setFoodLevel(4);
            var bread = new ItemStack(Items.BREAD, 2);
            bread.finishUsing(context.getWorld(), player);
            context.assertEquals(1, bread.getCount(), "First bread is consumed");
            context.assertTrue(OneTimeMenuChallenge.blocked(player, "minecraft:bread"), "Registry ID recorded");
            bread.set(DataComponentTypes.CUSTOM_NAME, Text.literal("other bread"));
            int food = player.getHungerManager().getFoodLevel();
            bread.finishUsing(context.getWorld(), player);
            context.assertEquals(1, bread.getCount(), "Renaming cannot bypass repeat prohibition");
            context.assertEquals(food, player.getHungerManager().getFoodLevel(), "No nutrition on rejected completion");
            context.assertTrue(bread.use(context.getWorld(), player, Hand.MAIN_HAND).getResult().equals(net.minecraft.util.ActionResult.FAIL), "Start also rejected");
            new ItemStack(Items.BEEF).finishUsing(context.getWorld(), player);
            new ItemStack(Items.COOKED_BEEF).finishUsing(context.getWorld(), player);
            context.assertTrue(OneTimeMenuChallenge.blocked(player, "minecraft:beef"), "Raw type recorded");
            context.assertTrue(OneTimeMenuChallenge.blocked(player, "minecraft:cooked_beef"), "Cooked type separate");
            context.assertTrue(!OneTimeMenuChallenge.blocked(player, "minecraft:milk_bucket"), "Milk not recorded");
            var cake = context.getAbsolutePos(new BlockPos(2, 2, 2));
            context.getWorld().setBlockState(cake.down(), Blocks.STONE.getDefaultState());
            context.getWorld().setBlockState(cake, Blocks.CAKE.getDefaultState());
            player.getHungerManager().setFoodLevel(4);
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            var hit = new BlockHitResult(Vec3d.ofCenter(cake), Direction.UP, cake, false);
            player.interactionManager.interactBlock(player, context.getWorld(), ItemStack.EMPTY, Hand.MAIN_HAND, hit);
            player.interactionManager.interactBlock(player, context.getWorld(), ItemStack.EMPTY, Hand.MAIN_HAND, hit);
            context.assertEquals(1, context.getWorld().getBlockState(cake).get(CakeBlock.BITES), "Only one cake bite");
            var persistence = context.getWorld().getPersistentStateManager();
            scenario.state.markDirty();
            persistence.save();
            var disk = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), context.getWorld().getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, loaded);
            context.assertTrue(OneTimeMenuChallenge.blocked(player, "minecraft:bread"), "History survives actual disk roundtrip");
            var late = scenario.player();
            context.assertTrue(!OneTimeMenuChallenge.blocked(late, "minecraft:bread"), "Independent UUID history");
            player.kill();
            player = scenario.respawn(player, false);
            context.assertTrue(OneTimeMenuChallenge.blocked(player, "minecraft:bread"), "Death does not reset history");
            com.cursecycle.state.CycleManager.activateChallenge(player.getServer(), loaded, "no_sprint");
            com.cursecycle.state.CycleManager.activateChallenge(player.getServer(), loaded, "one_time_menu");
            context.assertTrue(!OneTimeMenuChallenge.blocked(player, "minecraft:bread"), "New activation resets history");
        }
        context.complete();
    }
}
