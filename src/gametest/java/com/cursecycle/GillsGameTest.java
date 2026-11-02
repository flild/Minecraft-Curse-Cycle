package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.GillsChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.BlockPos;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class GillsGameTest implements FabricGameTest {
    private static NbtCompound supply(ServerPlayerEntity p) {
        return CurseCycleState.getServerState(p.getServer()).getChallengeData()
                .getCompound("gills").getCompound(p.getUuidAsString());
    }

    private static void seed(ServerPlayerEntity p, int air, int phase) {
        supply(p).putInt("air", air);
        supply(p).putInt("dryTicks", phase);
        p.setAir(air);
        CurseCycleState.getServerState(p.getServer()).markDirty();
    }

    private static void disconnect(ServerPlayerEntity p) {
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler, p.getServer());
        p.getServer().getPlayerManager().remove(p);
    }

    private static void move(TestContext c, ServerPlayerEntity p, BlockPos pos) {
        p.teleport(c.getWorld(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        p.onTeleportationDone();
        p.playerTick();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gills_environment")
    public void eyeWaterExclusionsAndForeignProtection(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            for (int i = 0; i < 61; i++) p.tick();
            var pos = c.getAbsolutePos(new BlockPos(3, 2, 3));
            s.activate("gills");
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 1200, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.CONDUIT_POWER, 1200, 0));
            var helmet = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_HELMET);
            helmet.addEnchantment(c.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENCHANTMENT)
                    .getEntry(net.minecraft.enchantment.Enchantments.RESPIRATION).orElseThrow(), 3);
            p.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, helmet);
            seed(p, 100, 0);
            c.getWorld().setBlockState(pos, Blocks.WATER.getDefaultState());
            move(c, p, pos);
            c.assertTrue(p.isTouchingWater() && !p.isSubmergedIn(FluidTags.WATER), "Wet feet are not submerged eyes");
            GillsChallenge.updateBreathing(p);
            c.assertEquals(99, p.getAir(), "Wet feet and breathing buffs do not recover air");
            c.getWorld().setBlockState(pos, Blocks.WATER_CAULDRON.getDefaultState()
                    .with(net.minecraft.block.LeveledCauldronBlock.LEVEL, 3));
            move(c, p, pos);
            GillsChallenge.updateBreathing(p);
            c.assertEquals(98, p.getAir(), "Cauldron is not WATER-tagged fluid at eyes");
            c.getWorld().setBlockState(pos, Blocks.LAVA.getDefaultState());
            move(c, p, pos);
            c.assertTrue(!p.isSubmergedIn(FluidTags.WATER), "Lava is not water");
            GillsChallenge.updateBreathing(p);
            c.assertEquals(97, p.getAir(), "Lava does not recover air");
            p.extinguish();
            for (int i = 0; i < 3; i++) c.getWorld().setBlockState(pos.up(i), Blocks.WATER.getDefaultState());
            move(c, p, pos);
            seed(p, 100, 19);
            for (int i = 0; i < 50; i++) { p.playerTick(); GillsChallenge.updateBreathing(p); }
            c.assertEquals(300, p.getAir(), "Fifty submerged steps recover exactly 200, capped");
            c.assertEquals(0, supply(p).getInt("dryTicks"), "Water resets suffocation phase");
            c.assertTrue(p.hasStatusEffect(StatusEffects.WATER_BREATHING) && p.hasStatusEffect(StatusEffects.CONDUIT_POWER), "Foreign effects retained");
            var mob = c.spawnEntity(net.minecraft.entity.EntityType.ZOMBIE, 5, 2, 5);
            mob.setAiDisabled(true);
            var underwater = net.minecraft.entity.LivingEntity.class.getDeclaredMethod("getNextAirUnderwater", int.class);
            underwater.setAccessible(true);
            c.assertEquals(99, underwater.invoke(mob, 100), "Non-player vanilla breathing unchanged");
            mob.discard();
            for (int i = 0; i < 3; i++) c.getWorld().setBlockState(pos.up(i), Blocks.AIR.getDefaultState());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gills_lifecycle")
    public void phaseDiskModesPersonalAndOfflineEnd(TestContext c) throws Exception {
        var server = c.getWorld().getServer();
        var defaultMode = server.getDefaultGameMode();
        server.setDefaultGameMode(GameMode.SURVIVAL);
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            s.activate("gills");
            seed(p, 0, 19);
            var challenge = ChallengeRegistry.get("gills");
            for (var mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
                p.changeGameMode(mode);
                p.setAir(50);
                challenge.onTick(server, p);
                c.assertEquals(19, supply(p).getInt("dryTicks"), "Excluded mode freezes phase");
                c.assertTrue(!GillsChallenge.active(p), "Excluded gate");
            }
            p.changeGameMode(GameMode.ADVENTURE);
            c.assertTrue(GillsChallenge.active(p), "Adventure eligible");
            challenge.onStart(server, p);
            c.assertEquals(0, p.getAir(), "Mode return restores saved supply");
            disconnect(p);
            var pm = server.getOverworld().getPersistentStateManager();
            pm.save();
            var disk = pm.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager());
            pm.set(CurseCycleState.STATE_KEY, loaded);
            p = s.player(p.getUuid());
            c.assertEquals(0, p.getAir(), "JOIN restores saved air after actual disk load");
            c.assertEquals(19, supply(p).getInt("dryTicks"), "Disk preserves phase 19");
            for (int i = 0; i < 61; i++) p.tick();
            float hp = p.getHealth();
            GillsChallenge.updateBreathing(p);
            c.assertEquals(hp - 2, p.getHealth(), "First dry tick after rejoin completes saved phase");
            c.assertEquals(0, supply(p).getInt("dryTicks"), "Phase reset after pulse");
            seed(p, 42, 0);
            var origin = p.getPos();
            p.teleport(server.getWorld(net.minecraft.world.World.NETHER), .5, 80, .5, 0, 0);
            p.onTeleportationDone();
            c.assertEquals(42, supply(p).getInt("air"), "Dimension change preserves supply");
            p.teleport(c.getWorld(), origin.x, origin.y, origin.z, 0, 0);
            p.onTeleportationDone();
            p = s.respawn(p, true);
            c.assertEquals(42, p.getAir(), "Alive replacement preserves supply");
            p.setHealth(0);
            GillsChallenge.updateBreathing(p);
            c.assertEquals(42, supply(p).getInt("air"), "Dead object does not tick");
            p = s.respawn(p, false);
            c.assertEquals(300, p.getAir(), "New life resets capacity");
            c.assertEquals(0, supply(p).getInt("dryTicks"), "New life resets phase");
            seed(p, 77, 0);
            disconnect(p);
            // Use the live loaded state, not ChallengeScenario's original reference.
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            c.assertTrue(!loaded.getChallengeData().contains("gills"), "Ended activation removes offline records");
            p = s.player(p.getUuid());
            c.assertTrue(!GillsChallenge.active(p), "Ended JOIN does not resurrect gills");
            var other = s.player();
            loaded.getPersonalActiveChallengeIds(p.getUuid()).add("gills");
            challenge.onPlayerJoin(server, p);
            c.assertEquals(300, p.getAir(), "First personal join starts full");
            GillsChallenge.updateBreathing(p);
            c.assertEquals(299, p.getAir(), "PERSONAL effective gate works");
            c.assertTrue(!GillsChallenge.active(other), "Unassigned player unaffected");
            loaded.getActiveChallengeIds().add("gills");
            challenge.onTick(server, p);
            var snapshot = supply(p).copy();
            CycleManager.onServerTick(server, CurseCycleMod.getConfig());
            c.assertEquals(snapshot, supply(p), "Overlapping GLOBAL/PERSONAL same-tick dispatch deduplicates");
            challenge.setEnabled(false);
            try {
                GillsChallenge.updateBreathing(p);
                c.assertTrue(!GillsChallenge.active(p), "Disabled gate");
                c.assertEquals(snapshot, supply(p), "Disabled update changes no supply");
            } finally { challenge.setEnabled(true); }
        } finally { server.setDefaultGameMode(defaultMode); }
        c.waitAndRun(5, c::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gills_pairs")
    public void damageProtectionAndIndependentPairs(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            for (int i = 0; i < 61; i++) p.tick();
            s.activate("double_challenge:gills,no_sprint");
            p.setSprinting(true);
            seed(p, 0, 19);
            p.timeUntilRegen = 0;
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1200, 0));
            p.setAbsorptionAmount(4);
            c.assertEquals(4F, p.getAbsorptionAmount(), "Absorption fixture has vanilla capacity");
            GillsChallenge.updateBreathing(p);
            c.assertEquals(2F, p.getAbsorptionAmount(), "Drown uses vanilla absorption");
            c.assertEquals(20F, p.getHealth(), "Absorption takes pulse first");
            c.assertTrue(!p.isSprinting(), "Control challenge still works");
            s.activate("no_sprint");
            p.setAir(50);
            p.playerTick();
            c.assertEquals(54, p.getAir(), "Vanilla land recovery restored");
            c.assertEquals(2F, p.getAbsorptionAmount(), "Stop does not restore lost absorption");
            p.removeStatusEffect(StatusEffects.ABSORPTION);
            p.setSprinting(true);
            c.assertTrue(!p.isSprinting(), "no_sprint retained after removal");
            s.activate("double_challenge:gills,no_healing");
            p.setAbsorptionAmount(0);
            p.setHealth(10);
            seed(p, 0, 19);
            p.timeUntilRegen = 0;
            GillsChallenge.updateBreathing(p);
            p.heal(4);
            c.assertEquals(8F, p.getHealth(), "Gills damage and no_healing both apply");
            s.activate("no_healing");
            p.heal(4);
            c.assertEquals(8F, p.getHealth(), "Removing gills leaves healing prohibition and damage");
            s.activate("double_challenge:gills,one_heart");
            p.setHealth(2);
            seed(p, 0, 19);
            p.timeUntilRegen = 0;
            GillsChallenge.updateBreathing(p);
            c.assertEquals(0F, p.getHealth(), "One unprotected pulse is fatal with one heart");
            p = s.respawn(p, false);
            c.assertEquals(300, p.getAir(), "Paired death respawns with full air");
            c.assertEquals(2F, p.getMaxHealth(), "One heart remains after respawn");
            s.activate("one_heart");
            c.assertEquals(2F, p.getMaxHealth(), "Removing gills leaves one heart");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gills_real_ticks", tickLimit = 460)
    public void realServerCadenceAndWaterRecovery(TestContext c) {
        var s = new ChallengeScenario(c);
        var p = s.player();
        p.setNoGravity(true);
        p.getHungerManager().setFoodLevel(17); // Exclude vanilla natural regeneration from pulse measurements.
        var dry = c.getAbsolutePos(new BlockPos(2, 3, 2));
        var water = c.getAbsolutePos(new BlockPos(5, 3, 5));
        for (int i = 0; i < 3; i++) c.getWorld().setBlockState(water.up(i), Blocks.WATER.getDefaultState());
        move(c, p, dry);
        for (int i = 0; i < 61; i++) p.tick();
        int[] started = {-1};
        boolean[] closed = {false};
        c.runAtEveryTick(() -> {
            if (closed[0] || c.getTick() < 1) return;
            try {
                var server = p.getServer();
                if (started[0] < 0) {
                    var dispatcher = server.getCommandManager().getDispatcher();
                    for (String command : new String[]{"fdc list", "fdc activate gills", "fdc active"})
                        c.assertTrue(dispatcher.execute(command, server.getCommandSource().withLevel(2)) > 0, "OP2 " + command);
                    started[0] = server.getTicks();
                }
                int elapsed = server.getTicks() - started[0];
                if (elapsed == 340) move(c, p, water);
                p.playerTick(); // Execute real transformed vanilla breathing before the END_SERVER_TICK path.
                CycleManager.onServerTick(server, CurseCycleMod.getConfig());
                var before = supply(p).copy();
                CycleManager.onServerTick(server, CurseCycleMod.getConfig());
                c.assertEquals(before, supply(p), "Duplicate dispatch cannot advance breathing twice");
                int steps = elapsed + 1;
                int expectedAir = elapsed < 340 ? Math.max(0, 300 - steps) : Math.min(300, (elapsed - 339) * 4);
                c.assertEquals(expectedAir, p.getAir(), "Air at real server tick " + steps);
                float expectedHp = steps < 320 ? 20F : steps < 340 ? 18F : 16F;
                c.assertEquals(expectedHp, p.getHealth(), "Exact drowning boundaries at 320/340, none underwater");
                if (elapsed >= 415) {
                    closed[0] = true;
                    disconnect(p);
                    s.close();
                    for (int i = 0; i < 3; i++) c.getWorld().setBlockState(water.up(i), Blocks.AIR.getDefaultState());
                    c.complete();
                }
            } catch (Throwable failure) {
                closed[0] = true;
                s.close();
                throw failure instanceof RuntimeException runtime ? runtime : new RuntimeException(failure);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<net.minecraft.server.MinecraftServer, java.util.Map<java.util.UUID, Integer>> ticks() throws Exception {
        var field = GillsChallenge.class.getDeclaredField("lastTicks");
        field.setAccessible(true);
        return (java.util.Map<net.minecraft.server.MinecraftServer, java.util.Map<java.util.UUID, Integer>>)
                field.get(ChallengeRegistry.get("gills"));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gills_cleanup")
    public void tickCacheReleasedAtLifecycleBoundaries(TestContext c) throws Exception {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            var server = p.getServer();
            var challenge = ChallengeRegistry.get("gills");
            for (int i = 0; i < 32; i++) {
                s.activate("gills");
                challenge.onTick(server, p);
                challenge.onTick(server, p);
                c.assertEquals(299, p.getAir(), "One step per server tick including same-tick reactivation");
                s.activate("no_sprint");
                c.assertTrue(!ticks().getOrDefault(server, java.util.Map.of()).containsKey(p.getUuid()),
                        "Stop must release cached UUID");
            }
            s.activate("gills");
            challenge.onTick(server, p);
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                    .onPlayDisconnect(p.networkHandler, server);
            c.assertTrue(!ticks().getOrDefault(server, java.util.Map.of()).containsKey(p.getUuid()), "Leave releases cache");
            c.assertEquals(299, s.state.getChallengeData().getCompound("gills").getCompound(p.getUuidAsString()).getInt("air"),
                    "Leave retains persistent supply");
            challenge.onTick(server, p);
            challenge.setEnabled(false);
            try { c.assertTrue(ticks().isEmpty(), "Disable releases cache even without onStop"); }
            finally { challenge.setEnabled(true); }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gills_dirty")
    public void unchangedFullSupplyDoesNotDirtyPersistence(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            s.activate("gills");
            var pos = c.getAbsolutePos(new net.minecraft.util.math.BlockPos(3, 2, 3));
            for (int i = 0; i < 3; i++) c.getWorld().setBlockState(pos.up(i), Blocks.WATER.getDefaultState());
            p.teleport(c.getWorld(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
            p.playerTick();
            c.assertTrue(p.isSubmergedIn(FluidTags.WATER), "Actual submerged eyes");
            s.state.setDirty(false);
            for (int i = 0; i < 100; i++) GillsChallenge.updateBreathing(p);
            ChallengeRegistry.get("gills").onStart(p.getServer(), p);
            c.assertTrue(!s.state.isDirty(), "Unchanged full supply/start must not dirty persistent state");
            c.assertEquals(300, p.getAir(), "Full supply remains capped");
            for (int i = 0; i < 3; i++) c.getWorld().setBlockState(pos.up(i), Blocks.AIR.getDefaultState());
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void actualAirDamagePersistenceAndRespawn(TestContext context) throws Exception {
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            for (int i = 0; i < 61; i++) player.tick();
            scenario.activate("gills");
            var challenge = ChallengeRegistry.get("gills");
            context.assertEquals(300, player.getAir(), "New activation capacity");
            for (int i = 0; i < 340; i++) { player.tick(); GillsChallenge.updateBreathing(player); }
            context.assertEquals(0, player.getAir(), "Dry air exhausted");
            context.assertEquals(16F, player.getHealth(), "Two drowning pulses after capacity expires");
            challenge.onStart(player.getServer(), player);
            context.assertEquals(0, player.getAir(), "Repeated start does not refill");
            player.changeGameMode(GameMode.CREATIVE);
            player.setAir(300);
            player.changeGameMode(GameMode.SURVIVAL);
            challenge.onStart(player.getServer(), player);
            context.assertEquals(0, player.getAir(), "Mode switch does not refill saved supply");
            var pos = context.getAbsolutePos(new net.minecraft.util.math.BlockPos(3, 2, 3));
            context.getWorld().setBlockState(pos, Blocks.WATER.getDefaultState());
            context.getWorld().setBlockState(pos.up(), Blocks.WATER.getDefaultState());
            context.getWorld().setBlockState(pos.up(2), Blocks.WATER.getDefaultState());
            player.teleport(context.getWorld(), pos.getX()+0.5, pos.getY(), pos.getZ()+0.5, 0, 0);
            player.playerTick();
            context.assertTrue(player.isSubmergedIn(FluidTags.WATER), "Eyes really in water");
            context.assertEquals(0, player.getAir(), "Vanilla air calculation is disabled");
            GillsChallenge.updateBreathing(player);
            context.assertEquals(4, player.getAir(), "Own water recovery exactly four");
            var persistence = context.getWorld().getPersistentStateManager();
            persistence.save();
            var disk = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), context.getWorld().getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, loaded);
            player.setAir(300);
            challenge.onPlayerJoin(player.getServer(), player);
            context.assertEquals(4, player.getAir(), "Disk roundtrip and join do not refill");
            player = scenario.respawn(player, true);
            context.assertEquals(4, player.getAir(), "Live respawn preserves air");
            player.kill();
            player = scenario.respawn(player, false);
            context.assertEquals(300, player.getAir(), "New life gets full supply");
            com.cursecycle.state.CycleManager.activateChallenge(player.getServer(), loaded, "no_sprint");
            context.assertTrue(!GillsChallenge.active(player), "Vanilla breathing restored after ending");
        }
        context.complete();
    }
}
