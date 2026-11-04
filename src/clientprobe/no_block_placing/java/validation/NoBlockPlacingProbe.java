package validation;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ModChallenges;
import com.cursecycle.challenge.impl.NoBlockPlacingChallenge;
import com.cursecycle.client.ClientDataHolder;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.*;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import sun.misc.Unsafe;
import java.util.List;

/** Transformed item/block methods with a synthetic client player/world; no window or network. */
public final class NoBlockPlacingProbe implements PreLaunchEntrypoint {
    private static final String ID = NoBlockPlacingChallenge.ID;
    private static TestWorld world;
    private static Player player;
    private static ItemStack stack;
    private static GameMode mode = GameMode.SURVIVAL;
    private static boolean alive = true;
    private static int checks, vanillaChecks;
    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isCreative() { return mode == GameMode.CREATIVE; }
        @Override public boolean isSpectator() { return mode == GameMode.SPECTATOR; }
        @Override public World getWorld() { return world; }
        @Override public ItemStack getStackInHand(Hand hand) { return stack; }
    }
    public static class TestWorld extends ClientWorld {
        public TestWorld() { super(null, null, null, null, 0, 0, null, null, false, 0); }
        @Override public BlockState getBlockState(BlockPos pos) { return Blocks.STONE.getDefaultState(); }
        @Override public FeatureSet getEnabledFeatures() { return FeatureFlags.DEFAULT_ENABLED_FEATURES; }
    }
    private static class Placement extends ItemPlacementContext {
        Placement(ItemUsageContext usage) { super(usage); }
        @Override public boolean canPlace() { vanillaChecks++; return false; }
    }
    private static void check(boolean condition, String why) {
        checks++; if (!condition) throw new AssertionError(why);
    }
    private static void sync(String... ids) { ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of()); }
    private static void continuation(boolean denied) {
        stack = new ItemStack(Items.STONE, 7);
        int before = vanillaChecks;
        ((BlockItem) Items.STONE).place(new Placement(new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(BlockPos.ORIGIN), Direction.UP, BlockPos.ORIGIN, false))));
        check(vanillaChecks == before + (denied ? 0 : 1), "Actual place method reaches vanilla only when allowed");
    }
    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion(); net.minecraft.Bootstrap.initialize();
            ModChallenges.registerAll();
            // preLaunch has no server datapack/tag sync. Supply only the vanilla candle tag used here.
            var tags = net.minecraft.registry.entry.RegistryEntry.Reference.class.getDeclaredMethod("setTags", java.util.Collection.class);
            tags.setAccessible(true);
            tags.invoke(Items.CANDLE.getRegistryEntry(), List.of(net.minecraft.registry.tag.ItemTags.CANDLES));
            check(new ItemStack(Items.CANDLE).isIn(net.minecraft.registry.tag.ItemTags.CANDLES), "Synthetic candle tag installed");
            var field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
            var unsafe = (Unsafe) field.get(null);
            world = (TestWorld) unsafe.allocateInstance(TestWorld.class);
            var clientField = World.class.getDeclaredField("isClient"); clientField.setAccessible(true); clientField.set(world, true);
            player = (Player) unsafe.allocateInstance(Player.class);
            String[][] targets = {
                    {"net.minecraft.item.BlockItem", "cursecycle$denyPlacement"},
                    {"net.minecraft.item.BucketItem", "cursecycle$denyFluid"},
                    {"net.minecraft.item.PowderSnowBucketItem", "cursecycle$denyFluid"},
                    {"net.minecraft.item.FlintAndSteelItem", "cursecycle$denyNewFire"},
                    {"net.minecraft.item.FireChargeItem", "cursecycle$denyNewFire"},
                    {"net.minecraft.block.FlowerPotBlock", "cursecycle$denyPlanting"},
                    {"net.minecraft.block.CakeBlock", "cursecycle$denyCandle"}};
            for (var target : targets) {
                var cls = Class.forName(target[0]);
                check(java.util.Arrays.stream(cls.getDeclaredMethods()).anyMatch(m -> m.getName().contains(target[1])), "Transformed " + target[0]);
            }
            var hit = new BlockHitResult(Vec3d.ofCenter(BlockPos.ORIGIN), Direction.UP, BlockPos.ORIGIN, false);
            for (var currentMode : List.of(GameMode.SURVIVAL, GameMode.ADVENTURE)) {
                mode = currentMode; sync(ID, "no_sprint");
                for (var hand : Hand.values()) {
                    continuation(true);
                    for (var item : List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET)) {
                        check(!((BucketItem) item).placeFluid(player, world, BlockPos.ORIGIN, hit), "Client denies fluid");
                    }
                    check(!((PowderSnowBucketItem) Items.POWDER_SNOW_BUCKET).placeFluid(player, world, BlockPos.ORIGIN, hit), "Client denies snow");
                    for (var item : List.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)) {
                        stack = new ItemStack(item);
                        check(item.useOnBlock(new ItemUsageContext(player, hand, hit)) == ActionResult.FAIL, "Client denies new fire");
                    }
                    for (var item : List.of(Items.DANDELION, Items.CANDLE)) {
                        var block = item == Items.CANDLE ? Blocks.CAKE : Blocks.FLOWER_POT;
                        stack = new ItemStack(item, 7);
                        check(block.getDefaultState().onUseWithItem(stack, world, player, hand, hit) == ItemActionResult.FAIL, "Client denies insertion " + item);
                        check(stack.getCount() == 7, "Client preserves insertion resource");
                    }
                }
            }
            for (var currentMode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) { mode = currentMode; continuation(false); }
            mode = GameMode.SURVIVAL; alive = false; continuation(false); alive = true;
            ChallengeRegistry.get(ID).setEnabled(false); continuation(false); ChallengeRegistry.get(ID).setEnabled(true);
            continuation(true); sync("no_sprint"); continuation(false);
            sync(ID); continuation(true); ClientDataHolder.reset(); continuation(false);
            check(!NoBlockPlacingChallenge.blocks(null), "Null automation actor allowed");
            System.out.println("NO_BLOCK_PLACING_CLIENT_PROBE_PASSED checks=" + checks + " vanillaContinuations=" + vanillaChecks);
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
