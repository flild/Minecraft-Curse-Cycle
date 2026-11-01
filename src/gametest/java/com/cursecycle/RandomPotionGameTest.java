package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.RandomPotionChallenge;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.random.Random;
import com.cursecycle.challenge.effect.EffectLedger;
import com.cursecycle.state.CycleManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class RandomPotionGameTest implements FabricGameTest {
    private static final String ID = RandomPotionChallenge.ID;

    private static void choose(ServerPlayerEntity p, int index) {
        p.getRandom().setSeed(seedFor(index));
    }

    private static NbtCompound saved(ServerPlayerEntity p) {
        var nbt = new NbtCompound();
        p.writeCustomDataToNbt(nbt);
        return nbt;
    }

    private static void disconnect(ServerPlayerEntity p) {
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler, p.getServer());
        p.getServer().getPlayerManager().remove(p);
    }

    private static long seedFor(int index) {
        for (long seed = 0; ; seed++) if (Random.create(seed).nextInt(19) == index) return seed;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "random_potion_pool")
    public void everyResultAndInstantCompatibility(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            for (int i = 0; i < 61; i++) p.tick(); // Expire vanilla login damage protection.
            for (int index = 0; index < 19; index++) {
                s.activate("no_sprint");
                p.clearStatusEffects();
                p.setHealth(10);
                p.timeUntilRegen = 0;
                choose(p, index);
                s.activate(ID);
                var type = Registries.STATUS_EFFECT.getEntry(Identifier.ofVanilla(RandomPotionChallenge.POOL.get(index))).orElseThrow();
                if (type.value().isInstant()) {
                    c.assertEquals(index == 3 ? 14F : 4F, p.getHealth(), "Instant result applies once at level I");
                    c.assertTrue(p.getStatusEffects().isEmpty(), "Instant has no continuous status");
                } else {
                    c.assertEquals(1, p.getStatusEffects().size(), "Exactly one ordinary result");
                    c.assertEquals(0, p.getStatusEffect(type).getAmplifier(), "Level I");
                    c.assertEquals(600, p.getStatusEffect(type).getDuration(), "Initial duration");
                    c.assertTrue(EffectLedger.of(p).owns(ID, type), "Tracked own contribution");
                }
                var before = saved(p).getList("cursecycle_effect_contributions", 10);
                float health = p.getHealth();
                CycleManager.onServerTick(p.getServer(), CurseCycleMod.getConfig());
                c.assertEquals(health, p.getHealth(), "Duplicate dispatcher does not repeat instant");
                c.assertEquals(before, saved(p).getList("cursecycle_effect_contributions", 10), "Duplicate does not reroll");
                s.activate("no_sprint");
                c.assertEquals(health, p.getHealth(), "Stop never rolls back health");
                c.assertTrue(p.getStatusEffects().isEmpty(), "Stop clears ordinary contribution");
            }
            p.setHealth(10);
            choose(p, 3);
            s.activate("double_challenge:random_potion,no_healing");
            c.assertEquals(10F, p.getHealth(), "no_healing blocks chosen instant health");
            choose(p, 3);
            s.activate("double_challenge:one_heart,random_potion");
            c.assertEquals(2F, p.getMaxHealth(), "one_heart still limits max health");
            c.assertTrue(p.getHealth() <= 2, "Instant healing cannot exceed one heart");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "random_potion_owners")
    public void externalClocksOwnersAndRepeatedCleanup(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1000, 2));
            choose(p, 0);
            s.activate(ID);
            for (int i = 0; i < 100; i++) p.playerTick();
            s.activate("no_sprint");
            c.assertEquals(900, p.getStatusEffect(StatusEffects.SPEED).getDuration(), "External clock is not restored from stale snapshot");
            c.assertEquals(2, p.getStatusEffect(StatusEffects.SPEED).getAmplifier(), "External strength preserved");
            p.clearStatusEffects();
            choose(p, 0);
            s.activate(ID);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 800, 3));
            for (int i = 0; i < 50; i++) p.playerTick();
            s.activate("no_sprint");
            c.assertEquals(750, p.getStatusEffect(StatusEffects.SPEED).getDuration(), "External effect added after selection ages naturally");
            p.clearStatusEffects();
            choose(p, 1);
            s.activate("double_challenge:random_potion,all_debuffs");
            s.state.getActiveChallengeIds().remove(ID);
            ChallengeRegistry.get(ID).onStop(p.getServer(), p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.SLOWNESS) && p.hasStatusEffect(StatusEffects.POISON), "Other owner survives random cleanup");
            choose(p, 1);
            s.activate("double_challenge:random_potion,all_debuffs");
            s.state.getActiveChallengeIds().remove("all_debuffs");
            ChallengeRegistry.get("all_debuffs").onStop(p.getServer(), p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.SLOWNESS) && !p.hasStatusEffect(StatusEffects.POISON), "Random owner survives other cleanup");
            choose(p, 0);
            s.activate("double_challenge:random_potion,no_block_breaking");
            ChallengeRegistry.get(ID).onStop(p.getServer(), p);
            c.assertEquals(9, p.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Mining fatigue X independent of random result");
            for (int i = 0; i < 32; i++) {
                choose(p, 0);
                s.activate("double_challenge:random_potion,no_sprint");
                p.setSprinting(true);
                c.assertTrue(!p.isSprinting() && p.hasStatusEffect(StatusEffects.SPEED), "Both mechanics work");
                s.activate("no_sprint");
                p.setSprinting(true);
                c.assertTrue(!p.isSprinting() && !p.hasStatusEffect(StatusEffects.SPEED), "Only random effect removed");
                c.assertTrue(saved(p).getList("cursecycle_effect_contributions", 10).isEmpty(), "No accumulated contributions");
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "random_potion_lifecycle")
    public void lifecycleModesAndPersistedStaleEffects(TestContext c) throws Exception {
        var server = c.getWorld().getServer();
        var defaultMode = server.getDefaultGameMode();
        var challenge = ChallengeRegistry.get(ID);
        server.setDefaultGameMode(GameMode.SURVIVAL);
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            var other = s.player();
            p.getAbilities().invulnerable = true;
            other.getAbilities().invulnerable = true;
            choose(p, 0);
            choose(other, 2);
            s.activate(ID);
            c.assertTrue(p.hasStatusEffect(StatusEffects.SPEED) && other.hasStatusEffect(StatusEffects.STRENGTH), "Independent per-player choices");
            for (var mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL}) {
                p.changeGameMode(mode);
                choose(p, 0);
                CycleManager.onServerTick(server, CurseCycleMod.getConfig());
                c.assertTrue(p.hasStatusEffect(StatusEffects.SPEED) == (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE), "Mode gate " + mode);
                c.assertTrue(other.hasStatusEffect(StatusEffects.STRENGTH), "Other player unaffected by mode transition");
            }
            challenge.setEnabled(false);
            p.playerTick();
            c.assertTrue(!p.hasStatusEffect(StatusEffects.SPEED), "Disabled cleanup without onStop");
            challenge.setEnabled(true);
            choose(p, 0);
            s.activate(ID);
            p.setHealth(0);
            p.playerTick();
            c.assertTrue(!p.hasStatusEffect(StatusEffects.SPEED), "Death removes own effect");
            var respawned = s.respawn(p, false);
            choose(respawned, 2);
            // Real respawn already chose once. Same-tick callback must preserve that result.
            var afterRespawn = saved(respawned).getList("cursecycle_effect_contributions", 10);
            challenge.onTick(server, respawned);
            c.assertEquals(afterRespawn, saved(respawned).getList("cursecycle_effect_contributions", 10), "Death respawn starts only one new result");
            p = respawned;
            choose(p, 0);
            s.activate(ID);
            var origin = p.getServerWorld();
            var position = p.getPos();
            p.teleport(server.getWorld(net.minecraft.world.World.NETHER), .5, 80, .5, 0, 0);
            p.onTeleportationDone();
            challenge.onTick(server, p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.SPEED), "Nether keeps result");
            p.teleport(origin, position.x, position.y, position.z, 0, 0);
            p.onTeleportationDone();
            p = s.respawn(p, true);
            c.assertTrue(EffectLedger.of(p).owns(ID, StatusEffects.SPEED), "Alive replacement transfers own provenance");
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 900, 2));
            p.playerTick();
            var active = saved(p);
            p.readCustomDataFromNbt(active);
            choose(p, 2);
            challenge.onPlayerJoin(server, p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.STRENGTH) && !EffectLedger.of(p).owns(ID, StatusEffects.SPEED), "Active reload discards previous own choice and starts new result");
            c.assertEquals(899, p.getStatusEffect(StatusEffects.SPEED).getDuration(), "External clock preserved through reload");
            var persistence = server.getOverworld().getPersistentStateManager();
            s.state.markDirty();
            persistence.save();
            var disk = persistence.readNbt(com.cursecycle.state.CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            c.assertEquals(s.state.getActivationSerial(), disk.getCompound("data").getLong("activationSerial"), "Activation saved on disk");
            c.assertTrue(disk.getCompound("data").getList("activeChallengeIds", 8).contains(net.minecraft.nbt.NbtString.of(ID)), "Global activity saved");
            active = saved(p);
            disconnect(p);
            c.assertTrue(!p.hasStatusEffect(StatusEffects.STRENGTH), "DISCONNECT removes own result");
            var returned = s.player(p.getUuid());
            choose(returned, 0);
            challenge.onPlayerJoin(server, returned);
            c.assertTrue(returned.hasStatusEffect(StatusEffects.SPEED), "Reconnect starts new result");
            s.activate("no_sprint");
            returned.readCustomDataFromNbt(active);
            EffectLedger.of(returned).reconcile();
            c.assertTrue(!returned.hasStatusEffect(StatusEffects.STRENGTH), "Offline-ended stale contribution removed without onStop");
            c.assertEquals(899, returned.getStatusEffect(StatusEffects.SPEED).getDuration(), "Stale cleanup keeps external clock");
            returned.clearStatusEffects();
            s.state.getPersonalActiveChallengeIds(returned.getUuid()).add(ID);
            s.state.markDirty();
            choose(returned, 2);
            CycleManager.onServerTick(server, CurseCycleMod.getConfig());
            c.assertTrue(returned.hasStatusEffect(StatusEffects.STRENGTH) && !other.hasStatusEffect(StatusEffects.STRENGTH), "Effective PERSONAL isolated from other player");
            disconnect(returned);
        } finally {
            challenge.setEnabled(true);
            server.setDefaultGameMode(defaultMode);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "random_potion_real_ticks", tickLimit = 700)
    public void realServerIntervalsMilkAndLatePlayer(TestContext c) {
        var s = new ChallengeScenario(c);
        var p = s.player();
        var second = s.player();
        p.getAbilities().invulnerable = true;
        second.getAbilities().invulnerable = true;
        ServerPlayerEntity[] late = {null};
        int[] started = {-1};
        boolean[] closed = {false};
        c.runAtEveryTick(() -> {
            if (closed[0] || c.getTick() < 1) return;
            try {
                var server = p.getServer();
                if (started[0] < 0) {
                    choose(p, 0);
                    choose(second, 1);
                    var source = server.getCommandSource().withLevel(2);
                    for (String command : new String[]{"fdc list", "fdc activate random_potion", "fdc active"})
                        c.assertTrue(server.getCommandManager().getDispatcher().execute(command, source) > 0, "OP2 command " + command);
                    started[0] = server.getTicks();
                }
                int elapsed = server.getTicks() - started[0];
                if (elapsed == 40) {
                    late[0] = s.player();
                    late[0].getAbilities().invulnerable = true;
                    choose(late[0], 2);
                    ChallengeRegistry.get(ID).onPlayerJoin(server, late[0]);
                }
                if (elapsed == 300) {
                    Items.MILK_BUCKET.finishUsing(new ItemStack(Items.MILK_BUCKET), p.getWorld(), p);
                    c.assertTrue(p.getStatusEffects().isEmpty(), "Real milk clears own result");
                }
                if (elapsed == 320) {
                    var origin = second.getServerWorld();
                    var pos = second.getPos();
                    second.teleport(server.getWorld(net.minecraft.world.World.NETHER), .5, 80, .5, 0, 0);
                    second.onTeleportationDone();
                    second.teleport(origin, pos.x, pos.y, pos.z, 0, 0);
                    second.onTeleportationDone();
                }
                if (elapsed == 350) {
                    long time = server.getOverworld().getTimeOfDay();
                    server.getOverworld().setTimeOfDay(time + 1000);
                    CycleManager.onServerTick(server, CurseCycleMod.getConfig());
                    server.getOverworld().setTimeOfDay(time);
                }
                choose(p, 0); // Repeated result is legal at 600.
                choose(second, 2);
                if (late[0] != null) choose(late[0], 0);
                // Use the real monotonic server clock; explicit duplicate of END_SERVER_TICK dispatch.
                CycleManager.onServerTick(server, CurseCycleMod.getConfig());
                if (elapsed < 300 || elapsed >= 600) c.assertTrue(p.hasStatusEffect(StatusEffects.SPEED), "Result only at start/600");
                else c.assertTrue(p.getStatusEffects().isEmpty(), "Milk never triggers an early reroll");
                c.assertTrue(second.hasStatusEffect(elapsed < 600 ? StatusEffects.SLOWNESS : StatusEffects.STRENGTH), "Second independent choice and dimension timer");
                if (late[0] != null) c.assertTrue(late[0].hasStatusEffect(elapsed < 640 ? StatusEffects.STRENGTH : StatusEffects.SPEED), "Late player has independent 600-tick deadline");
                if (elapsed == 600 || elapsed == 640) {
                    var target = elapsed == 600 ? p : late[0];
                    c.assertEquals(600, target.getStatusEffect(StatusEffects.SPEED).getDuration(), "Exactly full duration at boundary");
                    var before = saved(target).getList("cursecycle_effect_contributions", 10);
                    choose(target, 1);
                    CycleManager.onServerTick(server, CurseCycleMod.getConfig());
                    c.assertEquals(before, saved(target).getList("cursecycle_effect_contributions", 10), "Same-tick duplicate never selects again");
                }
                if (elapsed == 645) {
                    s.activate("no_sprint");
                    for (var player : new ServerPlayerEntity[]{p, second, late[0]}) disconnect(player);
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
    public void milkInstantAndForeignContributions(TestContext context) {
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            for (int i = 0; i < 61; i++) player.tick();
            var challenge = ChallengeRegistry.get("random_potion");
            player.getRandom().setSeed(seedFor(0));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200, 1));
            scenario.activate("random_potion");
            context.assertEquals(1, player.getStatusEffect(StatusEffects.SPEED).getAmplifier(), "Foreign stronger speed is preserved");
            player.clearStatusEffects();
            challenge.onTick(player.getServer(), player);
            context.assertTrue(player.getStatusEffects().isEmpty(), "Milk does not cause early choice");
            player = scenario.respawn(player, true);
            context.assertTrue(player.getStatusEffects().isEmpty(), "Live dimension return preserves the interval after milk");
            scenario.activate("no_sprint");
            for (int i = 0; i < 61; i++) player.tick();
            player.getRandom().setSeed(seedFor(4));
            player.timeUntilRegen = 0;
            scenario.activate("random_potion");
            context.assertEquals(14F, player.getHealth(), "Instant damage once at selection");
            player.timeUntilRegen = 0;
            challenge.onTick(player.getServer(), player);
            context.assertEquals(14F, player.getHealth(), "Duplicate tick does not repeat instant damage");
            scenario.activate("no_sprint");
            context.assertEquals(14F, player.getHealth(), "Stopping does not heal damage");
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 900, 2));
            player.getRandom().setSeed(seedFor(0));
            scenario.activate("double_challenge:random_potion,all_debuffs");
            challenge.onStop(player.getServer(), player);
            context.assertEquals(2, player.getStatusEffect(StatusEffects.SPEED).getAmplifier(), "Foreign speed survives owned cleanup");
            context.assertTrue(player.hasStatusEffect(StatusEffects.POISON), "Other challenge contributions remain");
        }
        context.complete();
    }
}
