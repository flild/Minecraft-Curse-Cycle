package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.effect.EffectLedger;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import java.util.UUID;

public class NoBlockBreakingGameTest implements FabricGameTest {
    private static final String ID = "no_block_breaking";

    private static void tick(ServerPlayerEntity player) {
        player.playerTick(); // Real vanilla status-effect tick, including the ledger Mixin.
        CycleManager.onServerTick(player.getServer(), CurseCycleMod.getConfig());
    }

    private static NbtCompound contribution(ServerPlayerEntity player) {
        var nbt = new NbtCompound();
        player.writeCustomDataToNbt(nbt);
        var entries = nbt.getList("cursecycle_effect_contributions", 10);
        for (int i = 0; i < entries.size(); i++) {
            var owners = entries.getCompound(i).getCompound("owners");
            if (owners.contains(ID)) return owners.getCompound(ID).getCompound("effect");
        }
        return new NbtCompound();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_block_breaking")
    public void dispatcherCadenceMilkAndRepeatedActivations(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            c.assertTrue(!p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Inactive control");
            s.activate(ID);
            c.assertEquals(40, contribution(p).getInt("duration"), "Own contribution starts at 40");
            for (int i = 1; i <= 240; i++) {
                tick(p);
                c.assertEquals(9, p.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Continuous level X");
                int expected = 40 - i % 20;
                c.assertEquals(expected, contribution(p).getInt("duration"), "Refresh at exactly 20 remaining");
                CycleManager.onServerTick(p.getServer(), CurseCycleMod.getConfig());
                c.assertEquals(expected, contribution(p).getInt("duration"), "Duplicate dispatch does not extend early");
            }
            Items.MILK_BUCKET.finishUsing(new ItemStack(Items.MILK_BUCKET), p.getWorld(), p);
            c.assertTrue(!p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Real milk path removes fatigue");
            tick(p);
            c.assertEquals(9, p.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Next dispatcher tick restores X");
            for (int i = 0; i < 32; i++) {
                long serial = s.state.getActivationSerial();
                s.activate(ID);
                c.assertEquals(serial + 1, s.state.getActivationSerial(), "Same-ID reactivation gets a new serial");
                var nbt = new NbtCompound();
                p.writeCustomDataToNbt(nbt);
                var entries = nbt.getList("cursecycle_effect_contributions", 10);
                c.assertEquals(1, entries.size(), "Repeated activation retains one effect type");
                c.assertEquals(1, entries.getCompound(0).getCompound("owners").getSize(), "One owner, no duplicate contribution");
                s.activate("no_sprint");
                c.assertTrue(!p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Stop removes owned fatigue");
                p.writeCustomDataToNbt(nbt);
                c.assertTrue(nbt.getList("cursecycle_effect_contributions", 10).isEmpty(), "No retained contribution after stop");
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_block_breaking")
    public void lifecycleEligibilityAndIndependentPlayers(TestContext c) {
        var challenge = ChallengeRegistry.get(ID);
        var server = c.getWorld().getServer();
        var originalMode = server.getDefaultGameMode();
        server.setDefaultGameMode(GameMode.SURVIVAL); // TestServer normally connects players in Creative.
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            s.activate(ID);
            var late = s.player(); // PlayerManager fires the actual Fabric JOIN callback.
            c.assertEquals(9, late.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Late join applies immediately");
            late.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 300, 10));
            for (var mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL}) {
                p.changeGameMode(mode);
                tick(p);
                boolean eligible = mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
                c.assertTrue(p.hasStatusEffect(StatusEffects.MINING_FATIGUE) == eligible, "Eligibility: " + mode);
                c.assertEquals(10, late.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Other player unaffected");
            }
            challenge.setEnabled(false);
            p.playerTick();
            c.assertTrue(!p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Disabled cleans up without dispatching onStop");
            challenge.setEnabled(true);
            tick(p);
            p.setHealth(0);
            p.playerTick();
            c.assertTrue(!p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Dead object loses only owned contribution");
            p = s.respawn(p, false);
            tick(p);
            c.assertEquals(9, p.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "New respawn object receives active rules");
            var origin = p.getServerWorld();
            var originPos = p.getPos();
            p.teleport(server.getWorld(net.minecraft.world.World.NETHER), .5, 80, .5, 0, 0);
            p.onTeleportationDone();
            tick(p);
            c.assertEquals(9, p.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Fatigue persists in Nether");
            p.teleport(origin, originPos.x, originPos.y, originPos.z, 0, 0);
            p.onTeleportationDone();
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 200, 2));
            p = s.respawn(p, true);
            tick(p);
            s.activate("no_sprint");
            c.assertEquals(2, p.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Live object replacement preserves external chain");
            p.removeStatusEffect(StatusEffects.MINING_FATIGUE);
            s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID);
            s.state.markDirty();
            tick(p);
            c.assertEquals(9, p.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "PERSONAL effective assignment");
            c.assertEquals(10, late.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "No PERSONAL bleed to other player");
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler, p.getServer());
            c.assertTrue(!p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Real disconnect event cleans own contribution");
            c.assertEquals(10, late.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Disconnect preserves other player");
            p.getServer().getPlayerManager().remove(p);
            s.state.getPersonalActiveChallengeIds(p.getUuid()).clear();
            s.state.markDirty();
            var returned = s.player(p.getUuid());
            c.assertTrue(!returned.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Reconnect after ending never reapplies stale rules");
        } finally {
            challenge.setEnabled(true);
            server.setDefaultGameMode(originalMode);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_block_breaking")
    public void vanillaMiningAndIndependentChallengeCleanup(TestContext c) throws Exception {
        var world = c.getWorld();
        var pos = c.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 2, 1));
        var originalBlock = world.getBlockState(pos);
        var originalSupport = world.getBlockState(pos.down());
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            world.setBlockState(pos.down(), Blocks.DIRT.getDefaultState());
            world.setBlockState(pos, Blocks.DANDELION.getDefaultState());
            p.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + 1.5, 0, 0);
            p.setOnGround(true);
            float control = Blocks.STONE.getDefaultState().calcBlockBreakingDelta(p, world, pos);
            var source = p.getServer().getCommandSource().withLevel(2);
            for (String cmd : new String[]{"fdc list", "fdc activate no_block_breaking", "fdc active"})
                c.assertTrue(p.getServer().getCommandManager().getDispatcher().execute(cmd, source) > 0, "Command succeeds: " + cmd);
            c.assertTrue(Blocks.STONE.getDefaultState().calcBlockBreakingDelta(p, world, pos) < control, "Vanilla fatigue slows ordinary mining");
            p.interactionManager.processBlockBreakingAction(pos, PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                    Direction.UP, world.getTopY(), 0);
            c.assertTrue(world.getBlockState(pos).isAir(), "Normal START destroys instant-break flower under fatigue X");
            c.assertEquals(1, p.getStatHandler().getStat(Stats.MINED.getOrCreateStat(Blocks.DANDELION)), "Vanilla mined statistic increments");
            c.assertTrue(!world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                    new net.minecraft.util.math.Box(pos).expand(1), e -> e.getStack().isOf(Items.DANDELION)).isEmpty(), "Vanilla flower drop exists");
            world.setBlockState(pos, Blocks.STONE.getDefaultState());
            p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            c.assertTrue(p.interactionManager.tryBreakBlock(pos), "Final successful mining path has no extra veto");
            c.assertEquals(1, p.getMainHandStack().getDamage(), "Vanilla tool wear survives");
            p.changeGameMode(GameMode.ADVENTURE);
            world.setBlockState(pos, Blocks.STONE.getDefaultState());
            c.assertTrue(!p.interactionManager.tryBreakBlock(pos), "Adventure keeps the vanilla CanBreak restriction");
            p.getMainHandStack().set(net.minecraft.component.DataComponentTypes.CAN_BREAK,
                    new net.minecraft.item.BlockPredicatesChecker(java.util.List.of(
                            net.minecraft.predicate.BlockPredicate.Builder.create().blocks(Blocks.STONE).build()), true));
            c.assertTrue(p.interactionManager.tryBreakBlock(pos), "Adventure CanBreak tool remains usable under fatigue X");
            p.changeGameMode(GameMode.SURVIVAL);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState());
            var hit = new BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos), Direction.UP, pos, false);
            p.interactionManager.interactBlock(p, world, p.getMainHandStack(), Hand.MAIN_HAND, hit);
            c.assertTrue(p.currentScreenHandler != p.playerScreenHandler, "Fatigue does not prohibit chest access");
            p.closeHandledScreen();
            s.activate("double_challenge:no_block_breaking,no_sprint");
            p.setSprinting(true);
            c.assertTrue(!p.isSprinting() && p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Both mechanics work");
            s.activate("no_sprint");
            p.setSprinting(true);
            c.assertTrue(!p.isSprinting() && !p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Removing fatigue preserves sprint restriction");
            s.activate("double_challenge:all_debuffs,no_block_breaking");
            s.state.getActiveChallengeIds().remove(ID);
            ChallengeRegistry.get(ID).onStop(p.getServer(), p);
            c.assertEquals(0, p.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Other owner's fatigue I remains");
            long speedSeed = 0;
            while (net.minecraft.util.math.random.Random.create(speedSeed).nextInt(19) != 0) speedSeed++;
            p.getRandom().setSeed(speedSeed);
            s.activate("double_challenge:random_potion,no_block_breaking");
            c.assertTrue(p.hasStatusEffect(StatusEffects.SPEED), "Deterministic random potion chooses speed");
            s.state.getActiveChallengeIds().remove(ID);
            ChallengeRegistry.get(ID).onStop(p.getServer(), p);
            c.assertTrue(!p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Fatigue owner removed independently of random potion");
            c.assertTrue(p.hasStatusEffect(StatusEffects.SPEED), "Random potion contribution survives");
        } finally {
            world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                    new net.minecraft.util.math.Box(pos).expand(2), e -> true).forEach(net.minecraft.entity.Entity::discard);
            world.setBlockState(pos, originalBlock);
            world.setBlockState(pos.down(), originalSupport);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_block_breaking_real_ticks", tickLimit = 100)
    public void realServerTicksAndFragileFloorRemainIndependent(TestContext c) {
        var s = new ChallengeScenario(c);
        var p = s.player();
        var pos = c.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 2, 1));
        var world = c.getWorld();
        var originalBlock = world.getBlockState(pos);
        world.setBlockState(pos, Blocks.STONE.getDefaultState());
        boolean[] closed = {false};
        c.runAtEveryTick(() -> {
            if (closed[0] || c.getTick() < 1) return;
            try {
                p.refreshPositionAndAngles(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 0, 0);
                p.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
                if (c.getTick() == 1) s.activate("double_challenge:no_block_breaking,fragile_floor");
                c.assertEquals(9, p.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Real END_SERVER_TICK maintains fatigue");
                if (c.getTick() == 2) {
                    Items.MILK_BUCKET.finishUsing(new ItemStack(Items.MILK_BUCKET), world, p);
                    c.assertTrue(!p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Milk removes before the real server tick");
                }
                if (c.getTick() == 40) c.assertTrue(world.getBlockState(pos).isOf(Blocks.STONE), "Support still present before 40 observations");
                if (c.getTick() == 42) {
                    c.assertTrue(world.getBlockState(pos).isAir(), "Fragile floor still destroys support under fatigue X");
                    s.state.getActiveChallengeIds().remove(ID);
                    ChallengeRegistry.get(ID).onStop(p.getServer(), p);
                    c.assertTrue(!p.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Removing fatigue leaves floor challenge active");
                    c.assertTrue(s.state.getActiveChallengeIds().contains("fragile_floor"), "Independent challenge retained");
                    closed[0] = true;
                    s.close();
                    world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                            new net.minecraft.util.math.Box(pos).expand(2), e -> true).forEach(net.minecraft.entity.Entity::discard);
                    world.setBlockState(pos, originalBlock);
                    c.complete();
                }
            } catch (Throwable failure) {
                closed[0] = true;
                s.close();
                world.setBlockState(pos, originalBlock);
                throw failure;
            }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "no_block_breaking")
    public void contributionsAndPersistence(TestContext context) throws Exception {
        var server = context.getWorld().getServer();
        var state = CurseCycleState.getServerState(server);
        var original = state.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager());
        UUID uuid = UUID.randomUUID();
        var data = ConnectedClientData.createDefault(new GameProfile(uuid, "fatigue-test"), false);
        var player = new ServerPlayerEntity(server, context.getWorld(), data.gameProfile(), data.syncedOptions());
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(GameMode.SURVIVAL);
        var challenge = ChallengeRegistry.get("no_block_breaking");
        try {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 200, 2));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 500, 1));
            CycleManager.activateChallenge(server, state, "double_challenge:no_block_breaking,no_sprint");
            context.assertEquals(9, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Fatigue X immediately");
            for (int i = 0; i < 25; i++) { player.playerTick(); challenge.onTick(server, player); }
            context.assertEquals(9, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Refresh never raises level");
            CycleManager.activateChallenge(server, state, "no_sprint");
            context.assertEquals(2, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Weaker external effect restored");
            context.assertEquals(175, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getDuration(), "External duration elapsed while hidden");
            context.assertTrue(player.hasStatusEffect(StatusEffects.SPEED), "Other type survives cleanup");

            CycleManager.activateChallenge(server, state, "no_block_breaking");
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 100, 10));
            challenge.onTick(server, player);
            context.assertEquals(10, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Stronger external effect wins");
            CycleManager.activateChallenge(server, state, "no_sprint");
            context.assertEquals(10, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Stronger external effect survives");

            CycleManager.activateChallenge(server, state, "no_block_breaking");
            player.clearStatusEffects();
            context.assertTrue(!player.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Milk clears contributions");
            challenge.onTick(server, player);
            context.assertEquals(9, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Next challenge tick restores fatigue");
            player.setStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 100, 2), null);
            challenge.onTick(server, player);
            context.assertEquals(9, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Direct replacement cannot suppress owned effect until refresh threshold");
            player.changeGameMode(GameMode.CREATIVE);
            EffectLedger.of(player).reconcile();
            context.assertEquals(2, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Excluded mode preserves direct external contribution");
            player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
            player.changeGameMode(GameMode.SURVIVAL);
            challenge.onTick(server, player);

            NbtCompound saved = new NbtCompound();
            player.writeCustomDataToNbt(saved);
            player.readCustomDataFromNbt(saved);
            EffectLedger.of(player).reconcile();
            context.assertEquals(9, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Owned NBT reloads");
            var serialized = state.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager());
            var restored = CurseCycleState.createFromNbt(serialized, context.getWorld().getRegistryManager());
            context.assertEquals(state.getActivationSerial(), restored.getActivationSerial(), "Activation identity persists");
            var persistence = context.getWorld().getPersistentStateManager();
            state.markDirty();
            persistence.save();
            var disk = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            context.assertEquals(state.getActivationSerial(), disk.getCompound("data").getLong("activationSerial"),
                    "Activation identity is written to the real world state file");
            CycleManager.activateChallenge(server, state, "no_sprint");
            player.readCustomDataFromNbt(saved);
            EffectLedger.of(player).reconcile();
            context.assertTrue(!player.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Stale saved contribution removed after offline ending");

            // A stronger external chain can expire while the owned contribution remains.
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 80, 2));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 5, 10));
            CycleManager.activateChallenge(server, state, "no_block_breaking");
            for (int i = 0; i < 10; i++) { player.playerTick(); challenge.onTick(server, player); }
            CycleManager.activateChallenge(server, state, "no_sprint");
            context.assertEquals(2, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Expired external stronger effect reveals original hidden effect");
            context.assertEquals(70, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getDuration(), "Hidden chain keeps its true remaining duration");
        } finally {
            server.getPlayerManager().remove(player);
            context.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY,
                    CurseCycleState.createFromNbt(original, context.getWorld().getRegistryManager()));
        }
        context.complete();
    }
}
