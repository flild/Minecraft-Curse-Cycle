package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.AllDebuffsChallenge;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import com.cursecycle.challenge.effect.EffectLedger;
import com.cursecycle.state.CycleManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class AllDebuffsGameTest implements FabricGameTest {
    private static final String ID = "all_debuffs";

    private static void tick(ServerPlayerEntity player) {
        player.playerTick();
        CycleManager.onServerTick(player.getServer(), CurseCycleMod.getConfig());
    }

    private static NbtCompound saved(ServerPlayerEntity player) {
        var nbt = new NbtCompound();
        player.writeCustomDataToNbt(nbt);
        return nbt;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "all_debuffs_review")
    public void continuousContributionsMilkAndExternalTimers(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            // This synchronous duration test must not create infested damage spawns.
            p.getAbilities().invulnerable = true;
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 300, 2));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 300, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 300));
            s.activate(ID);
            int count = AllDebuffsChallenge.effects().size();
            System.out.println("ALL_DEBUFFS_REVIEW_SET=" + AllDebuffsChallenge.effects().stream()
                    .map(e -> e.registryKey().getValue().toString()).sorted().toList());
            for (int i = 1; i <= 240; i++) {
                tick(p);
                for (var effect : AllDebuffsChallenge.effects()) {
                    c.assertTrue(p.hasStatusEffect(effect), "Continuous contribution: " + effect.registryKey());
                    c.assertEquals(effect.equals(StatusEffects.SLOWNESS) ? 2 : 0,
                            p.getStatusEffect(effect).getAmplifier(), "External stronger effect is never weakened");
                }
                var entries = saved(p).getList("cursecycle_effect_contributions", 10);
                c.assertEquals(count, entries.size(), "One ledger entry per maintained type");
                for (int n = 0; n < entries.size(); n++) {
                    var owners = entries.getCompound(n).getCompound("owners");
                    c.assertEquals(1, owners.getSize(), "One owner per effect");
                    c.assertEquals(40 - i % 20, owners.getCompound(ID).getCompound("effect").getInt("duration"),
                            "Exact 40/20 refresh cadence");
                }
                CycleManager.onServerTick(p.getServer(), CurseCycleMod.getConfig());
                c.assertEquals(entries, saved(p).getList("cursecycle_effect_contributions", 10),
                        "Repeated dispatch does not duplicate or refresh early");
            }
            s.activate("no_sprint");
            for (var effect : java.util.List.of(StatusEffects.SLOWNESS, StatusEffects.SPEED, StatusEffects.WITHER))
                c.assertEquals(60, p.getStatusEffect(effect).getDuration(), "External duration elapsed naturally");
            c.assertEquals(2, p.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier(), "Stronger external survives cleanup");
            s.activate(ID);
            Items.MILK_BUCKET.finishUsing(new ItemStack(Items.MILK_BUCKET), p.getWorld(), p);
            c.assertTrue(p.getStatusEffects().isEmpty(), "Real milk clears effects");
            tick(p);
            for (var effect : AllDebuffsChallenge.effects()) c.assertTrue(p.hasStatusEffect(effect), "Next tick restores own effect");
            c.assertTrue(!p.hasStatusEffect(StatusEffects.WITHER) && !p.hasStatusEffect(StatusEffects.SPEED), "Milk-cleared external effects stay cleared");
            for (int i = 0; i < 32; i++) {
                long serial = s.state.getActivationSerial();
                s.activate(ID);
                c.assertEquals(serial + 1, s.state.getActivationSerial(), "Reactivation changes serial");
                c.assertEquals(count, saved(p).getList("cursecycle_effect_contributions", 10).size(), "Bounded ledger across activations");
                s.activate("no_sprint");
                c.assertTrue(saved(p).getList("cursecycle_effect_contributions", 10).isEmpty(), "Stop frees all entries");
                c.assertTrue(p.getStatusEffects().isEmpty(), "Stop removes all owned effects");
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "all_debuffs_review")
    public void lifecycleEligibilityAndPersistence(TestContext c) throws Exception {
        var server = c.getWorld().getServer();
        var originalMode = server.getDefaultGameMode();
        var challenge = ChallengeRegistry.get(ID);
        server.setDefaultGameMode(GameMode.SURVIVAL);
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            s.activate(ID);
            var late = s.player();
            c.assertTrue(late.hasStatusEffect(StatusEffects.POISON), "Actual JOIN applies active rules");
            late.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 300, 3));
            for (var mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL}) {
                p.changeGameMode(mode);
                tick(p);
                boolean eligible = mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
                c.assertTrue(p.hasStatusEffect(StatusEffects.POISON) == eligible, "Mode eligibility " + mode);
                c.assertEquals(3, late.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier(), "Independent second player");
            }
            challenge.setEnabled(false);
            p.playerTick();
            c.assertTrue(saved(p).getList("cursecycle_effect_contributions", 10).isEmpty(), "Disabled cleanup without onStop");
            challenge.setEnabled(true);
            tick(p);
            p.setHealth(0);
            p.playerTick();
            c.assertTrue(!p.hasStatusEffect(StatusEffects.POISON), "Dead object loses own effects");
            p = s.respawn(p, false);
            tick(p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.POISON), "New object after death receives active rules");
            var origin = p.getServerWorld();
            var originPos = p.getPos();
            p.teleport(server.getWorld(net.minecraft.world.World.NETHER), .5, 80, .5, 0, 0);
            p.onTeleportationDone();
            tick(p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.POISON), "Nether retains rules");
            p.teleport(origin, originPos.x, originPos.y, originPos.z, 0, 0);
            p.onTeleportationDone();
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 2));
            p = s.respawn(p, true);
            tick(p);
            var active = saved(p);
            p.readCustomDataFromNbt(active);
            EffectLedger.of(p).reconcile();
            c.assertTrue(p.hasStatusEffect(StatusEffects.POISON), "Active NBT restores contributions");
            var persistence = server.getOverworld().getPersistentStateManager();
            s.state.markDirty();
            persistence.save();
            var disk = persistence.readNbt(com.cursecycle.state.CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            c.assertEquals(s.state.getActivationSerial(), disk.getCompound("data").getLong("activationSerial"), "Actual state file contains serial");
            s.activate("no_sprint");
            p.readCustomDataFromNbt(active);
            EffectLedger.of(p).reconcile();
            c.assertTrue(!p.hasStatusEffect(StatusEffects.POISON), "Stale offline NBT cleaned without onStop");
            c.assertEquals(2, p.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier(), "Live object transfer and stale cleanup retain external effect");
            c.assertEquals(199, p.getStatusEffect(StatusEffects.SLOWNESS).getDuration(), "External time remains exact across transfer");
            s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID);
            s.state.markDirty();
            tick(p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.POISON) && !late.hasStatusEffect(StatusEffects.POISON), "PERSONAL does not bleed to another player");
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler, server);
            c.assertTrue(!p.hasStatusEffect(StatusEffects.POISON), "Actual DISCONNECT cleans own contribution");
            server.getPlayerManager().remove(p);
            s.state.getPersonalActiveChallengeIds(p.getUuid()).clear();
            s.state.markDirty();
            var returned = s.player(p.getUuid());
            c.assertTrue(!returned.hasStatusEffect(StatusEffects.POISON), "Reconnect after end never restores stale rules");
        } finally {
            challenge.setEnabled(true);
            server.setDefaultGameMode(originalMode);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "all_debuffs_review")
    public void independentChallengeOwnersAndHealing(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            s.activate("double_challenge:all_debuffs,no_sprint");
            p.setSprinting(true);
            c.assertTrue(!p.isSprinting() && p.hasStatusEffect(StatusEffects.POISON), "Both mechanics operate");
            s.activate("no_sprint");
            p.setSprinting(true);
            c.assertTrue(!p.isSprinting() && !p.hasStatusEffect(StatusEffects.POISON), "Stop retains no_sprint");
            s.activate("double_challenge:all_debuffs,no_block_breaking");
            s.state.getActiveChallengeIds().remove(ID);
            ChallengeRegistry.get(ID).onStop(p.getServer(), p);
            c.assertEquals(9, p.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Other owner X survives removing all_debuffs");
            long seed = 0;
            while (net.minecraft.util.math.random.Random.create(seed).nextInt(19) != 1) seed++;
            p.getRandom().setSeed(seed);
            s.activate("double_challenge:all_debuffs,random_potion");
            c.assertTrue(EffectLedger.of(p).owns("random_potion", StatusEffects.SLOWNESS), "Random potion selects overlapping slowness");
            s.state.getActiveChallengeIds().remove("random_potion");
            ChallengeRegistry.get("random_potion").onStop(p.getServer(), p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.SLOWNESS) && p.hasStatusEffect(StatusEffects.POISON), "Removing random owner preserves all_debuffs");
            p.getRandom().setSeed(seed);
            s.activate("double_challenge:all_debuffs,random_potion");
            s.state.getActiveChallengeIds().remove(ID);
            ChallengeRegistry.get(ID).onStop(p.getServer(), p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.SLOWNESS) && !p.hasStatusEffect(StatusEffects.POISON), "Removing all_debuffs preserves random owner");
            s.activate("no_sprint");
            p.setHealth(10);
            p.heal(2);
            c.assertEquals(12F, p.getHealth(), "Healing control without no_healing");
            s.activate("double_challenge:all_debuffs,no_healing");
            p.heal(2);
            c.assertEquals(12F, p.getHealth(), "No healing while harmful effects active");
            s.state.getActiveChallengeIds().remove(ID);
            ChallengeRegistry.get(ID).onStop(p.getServer(), p);
            p.heal(2);
            c.assertEquals(12F, p.getHealth(), "No healing remains after all_debuffs removal");
            c.assertTrue(!p.hasStatusEffect(StatusEffects.POISON), "Only harmful owner removed");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "all_debuffs_real_ticks", tickLimit = 100)
    public void actualServerDispatcherAndCommands(TestContext c) {
        var s = new ChallengeScenario(c);
        var p = s.player();
        p.getAbilities().invulnerable = true; // Isolate dispatch from vanilla damage/spawns.
        boolean[] closed = {false};
        c.runAtEveryTick(() -> {
            if (closed[0] || c.getTick() < 1) return;
            try {
                if (c.getTick() == 1) {
                    var source = p.getServer().getCommandSource().withLevel(2);
                    for (String command : new String[]{"fdc list", "fdc activate all_debuffs", "fdc active"})
                        c.assertTrue(p.getServer().getCommandManager().getDispatcher().execute(command, source) > 0,
                                "Actual command succeeds: " + command);
                }
                for (var effect : AllDebuffsChallenge.effects())
                    c.assertTrue(p.hasStatusEffect(effect), "Real server dispatcher keeps " + effect.registryKey());
                if (c.getTick() == 2) {
                    Items.MILK_BUCKET.finishUsing(new ItemStack(Items.MILK_BUCKET), p.getWorld(), p);
                    c.assertTrue(p.getStatusEffects().isEmpty(), "Milk cleared before END_SERVER_TICK");
                }
                if (c.getTick() == 45) {
                    s.activate("no_sprint");
                    c.assertTrue(p.getStatusEffects().isEmpty(), "All own effects removed after real ticks");
                    closed[0] = true;
                    s.close();
                    c.complete();
                }
            } catch (Exception | AssertionError failure) {
                closed[0] = true;
                s.close();
                throw new RuntimeException(failure);
            }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void exactSetMilkAndIndependentSources(TestContext context) {
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            // Vanilla login protection must expire before checking actual external damage.
            for (int i = 0; i < 61; i++) player.tick();
            scenario.activate("all_debuffs");
            context.assertTrue(!AllDebuffsChallenge.effects().isEmpty(), "Actual harmful registry set exists");
            for (var effect : Registries.STATUS_EFFECT.streamEntries().toList()) {
                boolean expected = effect.registryKey().getValue().getNamespace().equals("minecraft")
                        && effect.value().getCategory() == StatusEffectCategory.HARMFUL
                        && !effect.equals(StatusEffects.INSTANT_DAMAGE) && !effect.equals(StatusEffects.WITHER);
                context.assertTrue(player.hasStatusEffect(effect) == expected,
                        "Exact registry membership: " + effect.registryKey());
                if (expected) context.assertEquals(0, player.getStatusEffect(effect).getAmplifier(), "Level I");
            }
            var late = scenario.player();
            ChallengeRegistry.get("all_debuffs").onTick(late.getServer(), late);
            context.assertTrue(late.hasStatusEffect(StatusEffects.POISON), "Late join receives the shared activation on the first Survival tick");
            context.assertTrue(!player.hasStatusEffect(StatusEffects.WITHER), "No owned wither at activation");
            context.assertTrue(!player.hasStatusEffect(StatusEffects.INSTANT_DAMAGE), "No owned instant damage");
            context.assertEquals(20F, player.getHealth(), "No activation damage pulse");
            player.clearStatusEffects();
            ChallengeRegistry.get("all_debuffs").onTick(player.getServer(), player);
            for (var effect : AllDebuffsChallenge.effects()) context.assertTrue(player.hasStatusEffect(effect), "Next tick restores milk-cleared effects");
            player.removeStatusEffect(StatusEffects.INFESTED); // Isolate HP assertion from random silverfish spawns.
            player.timeUntilRegen = 0;
            StatusEffects.INSTANT_DAMAGE.value().applyInstantEffect(null, null, player, 0, 1.0);
            context.assertEquals(14F, player.getHealth(), "Excluded instant damage from an external source remains allowed");
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 400));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 1));
            scenario.activate("no_sprint");
            context.assertEquals(14F, player.getHealth(), "Cleanup never rolls back external damage");
            context.assertTrue(player.hasStatusEffect(StatusEffects.WITHER), "External wither survives cleanup");
            context.assertTrue(player.hasStatusEffect(StatusEffects.SPEED), "Beneficial effect survives cleanup");
            for (var effect : AllDebuffsChallenge.effects()) context.assertTrue(!player.hasStatusEffect(effect), "Owned harmful effect removed");
            scenario.activate("double_challenge:all_debuffs,no_block_breaking");
            context.assertEquals(9, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Other owner fatigue X wins");
            ChallengeRegistry.get("no_block_breaking").onStop(player.getServer(), player);
            context.assertEquals(0, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Removing X retains owned fatigue I");
            scenario.activate("no_sprint");
            context.assertTrue(!player.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Both owned contributions clean up");
        }
        context.complete();
    }
}
