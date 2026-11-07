package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.effect.EffectLedger;
import com.cursecycle.challenge.impl.EndlessRaidChallenge;
import com.cursecycle.challenge.impl.NetheriteMobsChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.EndlessRaidState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.SharedConstants;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.raid.Raid;
import net.minecraft.village.raid.RaidManager;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;
import java.util.ArrayList;
import java.util.List;

public class EndlessRaidGameTest implements FabricGameTest {
    private static final String ID = EndlessRaidChallenge.ID;

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "endless_raid", tickLimit = 100)
    public void vanillaCountdownRepeatedRaidsAndNoDuplicates(TestContext c) throws Exception {
        try (var fixture = new Village(c); var s = new ChallengeScenario(c)) {
            var p = s.player(); var second = s.player();
            fixture.place(p); fixture.place(second);
            s.activate(ID);
            tickEffects(p); tickEffects(second);
            c.assertTrue(p.hasStatusEffect(StatusEffects.RAID_OMEN), "Vanilla village converts bad omen");
            int duration = p.getStatusEffect(StatusEffects.RAID_OMEN).getDuration();
            c.assertTrue(duration >= 599 && duration <= 600, "Vanilla 600-tick preparation");
            for (int i = 0; i < 580; i++) {
                if (i % 20 == 0) { EndlessRaidChallenge.check(p); EndlessRaidChallenge.check(second); }
                tickEffects(p); tickEffects(second);
            }
            c.assertEquals(0, fixture.raids().size(), "No instant raid, countdown never refreshed");
            for (int i = 0; i < 22; i++) { tickEffects(p); tickEffects(second); }
            c.assertEquals(1, fixture.raids().size(), "Two players start one actual vanilla raid");
            var first = fixture.raids().getFirst();
            c.assertEquals(1, first.getBadOmenLevel(), "No double-player intensity stacking");
            c.assertEquals(1, owned(c).getList("raids", NbtElement.COMPOUND_TYPE).size(), "Only newly created raid owned");
            c.assertFalse(p.hasStatusEffect(StatusEffects.RAID_OMEN), "Omen consumed without resurrection");
            for (int i = 0; i < 50; i++) EndlessRaidChallenge.check(p);
            c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN), "No omen while raid exists");
            first.invalidate();
            EndlessRaidChallenge.check(p); tickEffects(p);
            for (int i = 0; i < 601; i++) tickEffects(p);
            var next = fixture.raids().stream().filter(r -> !r.hasStopped()).toList();
            c.assertEquals(1, next.size(), "Next raid after previous stopped, no bottle");
            c.assertTrue(next.getFirst().getRaidId() != first.getRaidId(), "New vanilla raid ID");
            s.activate("no_sprint");
            c.assertTrue(next.getFirst().hasStopped(), "Own unfinished raid stopped");
            c.assertFalse(next.getFirst().hasWon(), "Cleanup awards no artificial victory");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "endless_raid")
    public void foreignOmensMilkGatesAndCommands(TestContext c) throws Exception {
        try (var fixture = new Village(c); var s = new ChallengeScenario(c)) {
            var p = s.player(); fixture.place(p);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BAD_OMEN, 5000, 0));
            tickEffects(p);
            for (int i = 0; i < 601; i++) tickEffects(p);
            var preexisting = fixture.raids().getFirst();
            s.activate(ID);
            c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Preexisting raid suppresses own omen");
            s.activate("no_sprint");
            c.assertFalse(preexisting.hasStopped(), "Raid from before activation survives");
            preexisting.invalidate();
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BAD_OMEN, 5000, 1));
            s.activate(ID);
            c.assertFalse(EffectLedger.of(p).owns(ID, StatusEffects.BAD_OMEN), "Foreign bad omen not claimed");
            tickEffects(p);
            c.assertFalse(EffectLedger.of(p).owns(ID, StatusEffects.RAID_OMEN), "Foreign preparation not claimed");
            for (int i = 0; i < 601; i++) tickEffects(p);
            var foreign = fixture.raids().getFirst();
            c.assertEquals(0, owned(c).getList("raids", 10).size(), "Foreign raid never owned");
            s.activate("no_sprint");
            c.assertFalse(foreign.hasStopped(), "Foreign raid survives cleanup");
            foreign.invalidate();
            s.activate(ID);
            Items.MILK_BUCKET.finishUsing(new ItemStack(Items.MILK_BUCKET), p.getWorld(), p);
            c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Real milk clears own omen");
            EndlessRaidChallenge.check(p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Next check restores own omen");
            c.assertFalse(p.hasStatusEffect(StatusEffects.RAID_OMEN), "Milk/check does not instantly start preparation");
            // An external omen added after our contribution must survive cleanup with its duration.
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BAD_OMEN, 1234, 2));
            s.activate("no_sprint");
            c.assertEquals(2, p.getStatusEffect(StatusEffects.BAD_OMEN).getAmplifier(), "Foreign amplifier survives");
            c.assertEquals(1234, p.getStatusEffect(StatusEffects.BAD_OMEN).getDuration(), "Foreign duration survives");
            p.clearStatusEffects();
            s.activate(ID);
            for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL)) {
                p.changeGameMode(mode); EndlessRaidChallenge.check(p);
                c.assertEquals(mode.isSurvivalLike(), p.hasStatusEffect(StatusEffects.BAD_OMEN), "Mode gate " + mode);
            }
            fixture.world.getGameRules().get(GameRules.DISABLE_RAIDS).set(true, fixture.world.getServer());
            EndlessRaidChallenge.check(p); tickEffects(p);
            c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN), "disableRaids obeyed");
            fixture.world.getGameRules().get(GameRules.DISABLE_RAIDS).set(false, fixture.world.getServer());
            fixture.world.getServer().setDifficulty(Difficulty.PEACEFUL, true);
            EndlessRaidChallenge.check(p);
            c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Peaceful obeyed");
            fixture.world.getServer().setDifficulty(Difficulty.NORMAL, true);
            var challenge = ChallengeRegistry.get(ID);
            try {
                challenge.setEnabled(false); EndlessRaidChallenge.check(p);
                c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Disabled challenge gate");
            } finally { challenge.setEnabled(true); }
            for (var command : List.of("fdc list", "fdc activate endless_raid", "fdc active"))
                c.assertTrue(fixture.world.getServer().getCommandManager().getDispatcher().execute(command,
                        fixture.world.getServer().getCommandSource().withLevel(2)) > 0, "Command " + command);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "endless_raid")
    public void independentVillagesDimensionKeysAndPersonalGate(TestContext c) throws Exception {
        try (var fixture = new Village(c); var s = new ChallengeScenario(c)) {
            var distant = fixture.pos.add(256, 0, 0);
            fixture.addPoi(distant);
            var p = s.player(); fixture.place(p);
            var other = s.player();
            other.teleport(fixture.world, distant.getX() + .5, distant.getY(), distant.getZ() + .5, 0, 0);
            other.onTeleportationDone();
            var nether = fixture.world.getServer().getWorld(World.NETHER);
            Raid foreignDimension = null;
            try {
                s.activate(ID);
                tickEffects(p); tickEffects(other);
                for (int i = 0; i < 601; i++) { tickEffects(p); tickEffects(other); }
                c.assertEquals(2, fixture.raids().size(), "Two actual independent village raids");
                var own = fixture.raids().getFirst();
                // Test-only insertion: a foreign raid using exactly the same numeric ID in another world.
                var field = RaidManager.class.getDeclaredField("raids"); field.setAccessible(true);
                @SuppressWarnings("unchecked") var netherRaids = (java.util.Map<Integer, Raid>) field.get(nether.getRaidManager());
                c.assertFalse(netherRaids.containsKey(own.getRaidId()), "Fixture ID available in other dimension");
                foreignDimension = new Raid(own.getRaidId(), nether, distant);
                netherRaids.put(own.getRaidId(), foreignDimension);
                EndlessRaidState.get(fixture.world.getServer()).reconcile(nether);
                s.activate("no_sprint");
                c.assertTrue(own.hasStopped(), "Correct dimensional raid stopped");
                c.assertFalse(foreignDimension.hasStopped(), "Same ID in another dimension stays foreign");
                s.state.getPersonalActiveChallenges().put(p.getUuid(), new ArrayList<>(List.of(ID)));
                EndlessRaidChallenge.check(p); tickEffects(p);
                for (int i = 0; i < 601; i++) tickEffects(p);
                var personal = fixture.raids().getFirst();
                EndlessRaidState.get(fixture.world.getServer()).reconcile(fixture.world);
                c.assertFalse(personal.hasStopped(), "Effective PERSONAL gate preserved");
                s.state.getPersonalActiveChallenges().clear();
                EndlessRaidState.get(fixture.world.getServer()).reconcile(fixture.world);
                c.assertTrue(personal.hasStopped(), "Disabled effective gate cleans its world consequence");
            } finally {
                fixture.world.getPointOfInterestStorage().remove(distant);
                if (foreignDimension != null) {
                    var field = RaidManager.class.getDeclaredField("raids"); field.setAccessible(true);
                    ((java.util.Map<?, ?>) field.get(nether.getRaidManager())).remove(foreignDimension.getRaidId());
                }
            }
        }
        c.waitAndRun(5, c::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "endless_raid")
    public void diskOwnershipOfflineEndingAndSurvivingRaiders(TestContext c) throws Exception {
        try (var fixture = new Village(c); var s = new ChallengeScenario(c)) {
            var server = fixture.world.getServer();
            var p = s.player(); fixture.place(p);
            s.activate("double_challenge:endless_raid,netherite_mobs");
            tickEffects(p);
            var playerNbt = new NbtCompound(); p.writeCustomDataToNbt(playerNbt);
            var restoredPlayer = s.player(); restoredPlayer.readCustomDataFromNbt(playerNbt);
            fixture.place(restoredPlayer);
            c.assertTrue(EffectLedger.of(restoredPlayer).owns(ID, StatusEffects.RAID_OMEN), "Player NBT restores origin");
            c.assertEquals(p.getStartRaidPos(), restoredPlayer.getStartRaidPos(), "Vanilla persists preparation position");
            for (int i = 0; i < 601; i++) tickEffects(restoredPlayer);
            var own = fixture.raids().getFirst();
            var raider = EntityType.PILLAGER.create(fixture.world);
            raider.refreshPositionAndAngles(fixture.pos, 0, 0); raider.setAiDisabled(true);
            fixture.world.spawnEntity(raider);
            try {
                own.addRaider(1, raider, null, true);
                fixture.world.tickEntity(raider);
                c.assertTrue(NetheriteMobsChallenge.isIssued(raider.getEquippedStack(EquipmentSlot.CHEST)), "Double equips new raider");
                var persistence = server.getOverworld().getPersistentStateManager();
                persistence.save();
                var disk = persistence.readNbt(EndlessRaidState.KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
                persistence.set(EndlessRaidState.KEY, EndlessRaidState.fromNbt(disk.getCompound("data"), fixture.world.getRegistryManager()));
                var raidDisk = fixture.world.getRaidManager().writeNbt(new NbtCompound(), fixture.world.getRegistryManager());
                var loadedRaids = RaidManager.fromNbt(fixture.world, raidDisk);
                c.assertEquals(own.getBadOmenLevel(), loadedRaids.getRaid(own.getRaidId()).getBadOmenLevel(), "Vanilla raid progress NBT");
                EndlessRaidState.get(server).reconcile(fixture.world);
                c.assertFalse(own.hasStopped(), "Active ownership survives disk reload without duplication");
                // Remove all embedded clients, including fixtures left by earlier test batches.
                for (var online : new ArrayList<>(server.getPlayerManager().getPlayerList())) server.getPlayerManager().remove(online);
                s.activate("no_sprint");
                c.assertFalse(own.hasStopped(), "No online onStop: cleanup must not depend on that callback");
                persistence.save();
                disk = persistence.readNbt(EndlessRaidState.KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
                persistence.set(EndlessRaidState.KEY, EndlessRaidState.fromNbt(disk.getCompound("data"), fixture.world.getRegistryManager()));
                fixture.world.getRaidManager().tick();
                c.assertTrue(own.hasStopped(), "Stale activation stopped before vanilla world tick");
                c.assertFalse(own.hasWon(), "No artificial victory");
                c.assertTrue(raider.isAlive() && !raider.isRemoved(), "Existing raider survives");
                c.assertTrue(NetheriteMobsChallenge.isIssued(raider.getEquippedStack(EquipmentSlot.CHEST)), "Existing gear survives");
                var offline = s.player(); offline.readCustomDataFromNbt(playerNbt);
                EffectLedger.of(offline).reconcile();
                c.assertFalse(offline.hasStatusEffect(StatusEffects.RAID_OMEN), "Offline stale omen cleaned on return");
            } finally { raider.discard(); }
        }
        c.waitAndRun(5, c::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "endless_raid")
    public void fieldDimensionRespawnAndDouble(TestContext c) throws Exception {
        try (var fixture = new Village(c); var s = new ChallengeScenario(c)) {
            var p = s.player();
            p.teleport(fixture.world, fixture.pos.getX() + 400, fixture.pos.getY(), fixture.pos.getZ(), 0, 0);
            p.onTeleportationDone();
            s.activate("double_challenge:endless_raid,no_sprint");
            for (int i = 0; i < 650; i++) tickEffects(p);
            c.assertEquals(0, fixture.raids().size(), "No waves in open field");
            c.assertTrue(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Omen waits for actual village");
            p.setSprinting(true); c.assertFalse(p.isSprinting(), "Double companion works");
            p.setHealth(0); p = s.respawn(p, false); fixture.place(p);
            EndlessRaidChallenge.check(p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Respawn restores omen on next check");
            var nether = fixture.world.getServer().getWorld(World.NETHER);
            p.teleport(nether, .5, 80, .5, 0, 0); p.onTeleportationDone();
            EndlessRaidChallenge.check(p);
            c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Non-raid dimension removes own omen");
            fixture.place(p); EndlessRaidChallenge.check(p); tickEffects(p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.RAID_OMEN), "Return resumes vanilla preparation");
            p.changeGameMode(GameMode.CREATIVE);
            // Even if the 20-tick maintenance has not run, own effect cannot start a creative raid.
            for (int i = 0; i < 601; i++) tickEffects(p);
            c.assertEquals(0, fixture.raids().size(), "Creative cannot initiate our raid between checks");
            p.changeGameMode(GameMode.SURVIVAL); EndlessRaidChallenge.check(p);
            s.activate("no_sprint");
            c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Only own temporary contribution removed");
            p.setSprinting(true); c.assertFalse(p.isSprinting(), "Remaining companion still works");
        }
        c.waitAndRun(5, c::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "endless_raid", tickLimit = 100)
    public void vanillaVictoryLossCelebrationAndNextPreparation(TestContext c) throws Exception {
        for (boolean victory : List.of(true, false)) {
            try (var fixture = new Village(c); var s = new ChallengeScenario(c)) {
                var p = s.player(); fixture.place(p);
                s.activate(ID);
                var raid = prepare(c, fixture, p);
                // Seed the end-of-combat boundary only; vanilla performs the transition,
                // reward and all 600 celebration ticks. This does not simulate a full fight.
                setRaidField(raid, "started", true);
                setRaidField(raid, "wavesSpawned", raid.getMaxWaves(Difficulty.NORMAL));
                raid.addHero(p);
                if (!victory) {
                    fixture.world.getPointOfInterestStorage().remove(fixture.pos);
                    fixture.world.getPointOfInterestStorage().tick(() -> true);
                    c.assertFalse(fixture.world.isNearOccupiedPointOfInterest(fixture.pos), "Loss fixture has no village");
                }
                for (int i = 0; i < 41 && !raid.isFinished(); i++) fixture.world.getRaidManager().tick();
                c.assertEquals(victory, raid.hasWon(), "Vanilla victory boundary");
                c.assertEquals(!victory, raid.hasLost(), "Vanilla village-loss boundary");
                c.assertEquals(victory, p.hasStatusEffect(StatusEffects.HERO_OF_THE_VILLAGE), "Only vanilla victory awards hero");
                if (!victory) fixture.addPoi(fixture.pos);
                for (int i = 0; i < 599; i++) {
                    fixture.world.getRaidManager().tick();
                    EndlessRaidChallenge.check(p);
                    c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN), "No restart during celebration " + i);
                }
                c.assertFalse(raid.hasStopped(), "All 599 celebration ticks preserved");
                fixture.world.getRaidManager().tick();
                c.assertTrue(raid.hasStopped(), "Vanilla 600th tick ends celebration");
                EndlessRaidChallenge.check(p);
                var next = prepare(c, fixture, p);
                c.assertTrue(next.getRaidId() != raid.getRaidId(), "New raid after vanilla finished event");
                s.activate("no_sprint");
                c.assertTrue(next.hasStopped() && !next.hasWon(), "Next unfinished raid stops without victory");
                c.assertEquals(victory, p.hasStatusEffect(StatusEffects.HERO_OF_THE_VILLAGE), "Earned reward is not rolled back");
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "endless_raid", tickLimit = 100)
    public void cadenceSerialCleanupAndIndependentPairs(TestContext c) throws Exception {
        try (var fixture = new Village(c); var s = new ChallengeScenario(c)) {
            var p = s.player(); var second = s.player(); fixture.place(p); fixture.place(second);
            p.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 17)); p.setHealth(17);
            var server = fixture.world.getServer();
            var ticks = MinecraftServer.class.getDeclaredField("ticks"); ticks.setAccessible(true);
            int originalTicks = server.getTicks();
            try {
                s.activate(ID);
                p.clearStatusEffects();
                for (int i = 1; i < 20; i++) {
                    ticks.setInt(server, i); EndlessRaidChallenge.tick(server);
                    c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN), "No issuance before tick20");
                }
                ticks.setInt(server, 20); EndlessRaidChallenge.tick(server);
                c.assertTrue(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Tick20 issues omen");
                tickEffects(p); tickEffects(p);
                int duration = p.getStatusEffect(StatusEffects.RAID_OMEN).getDuration();
                for (int i = 0; i < 50; i++) EndlessRaidChallenge.tick(server);
                c.assertEquals(duration, p.getStatusEffect(StatusEffects.RAID_OMEN).getDuration(), "Repeated same tick does not refresh countdown");
            } finally { ticks.setInt(server, originalTicks); }
            for (int i = 0; i < 32; i++) {
                s.activate("double_challenge:endless_raid,no_sprint");
                var raid = prepare(c, fixture, p);
                tickEffects(second);
                c.assertEquals(1, fixture.raids().size(), "One raid for two players in cycle " + i);
                p.setSprinting(true); c.assertFalse(p.isSprinting(), "Control companion active");
                var state = EndlessRaidState.get(server);
                state.setDirty(false);
                for (int j = 0; j < 50; j++) state.reconcile(fixture.world);
                c.assertFalse(state.isDirty(), "Unchanged ownership never marks dirty");
                c.assertEquals(1, owned(c).getList("raids", 10).size(), "One ownership entry");
                s.activate(i % 2 == 0 ? "no_sprint" : ID);
                c.assertTrue(raid.hasStopped() && !raid.hasWon(), "Each prior serial cleaned");
                c.assertEquals(0, owned(c).getList("raids", 10).size(), "No accumulated ownership entries");
                p.setSprinting(true);
                c.assertEquals(i % 2 != 0, p.isSprinting(), "Both companion endings");
                c.assertEquals(17, p.getInventory().getStack(0).getCount(), "No inventory rollback");
                c.assertEquals(17.0F, p.getHealth(), "No health rollback");
            }
            s.activate("double_challenge:endless_raid,all_debuffs");
            var debuffs = ChallengeRegistry.get("all_debuffs");
            c.assertTrue(p.hasStatusEffect(StatusEffects.POISON), "Debuff companion active");
            c.assertTrue(EffectLedger.of(p).exclusivelyOwns(ID, StatusEffects.BAD_OMEN), "Vanilla omen is outside the harmful companion set");
            ChallengeRegistry.get(ID).onStop(server, p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.POISON), "Ending raids preserves companion effects");
            c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Only raid omen contribution removed");
            s.activate("double_challenge:endless_raid,all_debuffs");
            debuffs.onStop(server, p);
            c.assertFalse(p.hasStatusEffect(StatusEffects.POISON), "Ending companion removes its poison");
            EndlessRaidChallenge.check(p);
            c.assertTrue(EffectLedger.of(p).exclusivelyOwns(ID, StatusEffects.BAD_OMEN), "Raid rule resumes independently");
            var own = prepare(c, fixture, p);
            c.assertEquals(1, owned(c).getList("raids", 10).size(), "Only exclusive raid omen can claim a raid");
            s.activate("no_sprint");
            c.assertTrue(own.hasStopped(), "Independent raid cleanup");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "endless_raid", tickLimit = 100)
    public void sameUuidDiskRejoinAndEmptyServerSameId(TestContext c) throws Exception {
        try (var fixture = new Village(c); var s = new ChallengeScenario(c)) {
            var server = fixture.world.getServer(); var persistence = server.getOverworld().getPersistentStateManager();
            var p = s.player(); fixture.place(p); var uuid = p.getUuid();
            s.activate(ID); tickEffects(p);
            for (int i = 0; i < 200; i++) tickEffects(p);
            int remaining = p.getStatusEffect(StatusEffects.RAID_OMEN).getDuration();
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler, server);
            server.getPlayerManager().remove(p);
            p = s.player(uuid); fixture.place(p);
            c.assertEquals(remaining, p.getStatusEffect(StatusEffects.RAID_OMEN).getDuration(), "Real playerdata same UUID preserves countdown");
            c.assertTrue(EffectLedger.of(p).exclusivelyOwns(ID, StatusEffects.RAID_OMEN), "Join preserves provenance");
            for (int i = 0; i < remaining + 1; i++) tickEffects(p);
            var own = fixture.raids().getFirst();
            persistence.save();
            var disk = persistence.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            var cycle = CurseCycleState.createFromNbt(disk.getCompound("data"), fixture.world.getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, cycle);
            disk = persistence.readNbt(EndlessRaidState.KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            persistence.set(EndlessRaidState.KEY, EndlessRaidState.fromNbt(disk.getCompound("data"), fixture.world.getRegistryManager()));
            fixture.world.getRaidManager().tick();
            c.assertFalse(own.hasStopped(), "Loaded current cycle/ownership keeps raid");
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler, server);
            for (var online : new ArrayList<>(server.getPlayerManager().getPlayerList())) server.getPlayerManager().remove(online);
            long oldSerial = cycle.getActivationSerial();
            com.cursecycle.state.CycleManager.activateChallenge(server, cycle, ID);
            c.assertTrue(cycle.getActivationSerial() > oldSerial, "Same ID on empty server changes serial");
            c.assertFalse(own.hasStopped(), "Empty server did not call onStop");
            fixture.world.getRaidManager().tick();
            c.assertTrue(own.hasStopped() && !own.hasWon(), "Manager tick cleans stale serial even with same ID active");
            c.assertEquals(0, owned(c).getList("raids", 10).size(), "Stale ownership pruned");
            p = s.player(uuid); fixture.place(p);
            c.assertTrue(p.hasStatusEffect(StatusEffects.BAD_OMEN), "New activation rejoin starts fresh omen");
            tickEffects(p);
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler, server);
            server.getPlayerManager().remove(p);
            com.cursecycle.state.CycleManager.activateChallenge(server, cycle, "no_sprint");
            persistence.save();
            disk = persistence.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            persistence.set(CurseCycleState.STATE_KEY, CurseCycleState.createFromNbt(disk.getCompound("data"), fixture.world.getRegistryManager()));
            p = s.player(uuid); fixture.place(p);
            c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN) || p.hasStatusEffect(StatusEffects.RAID_OMEN), "Actual JOIN cleans offline-ended playerdata");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "endless_raid", tickLimit = 100)
    public void actualOwnedWavesAndBothNetheriteEndings(TestContext c) throws Exception {
        try (var fixture = new Village(c); var s = new ChallengeScenario(c)) {
            var p = s.player(); fixture.place(p);
            var wave = Raid.class.getDeclaredMethod("spawnNextWave", BlockPos.class); wave.setAccessible(true);
            var cleanup = new ArrayList<net.minecraft.entity.raid.RaiderEntity>();
            try {
                for (boolean endRaids : List.of(true, false)) {
                    s.activate("double_challenge:endless_raid,netherite_mobs");
                    var raid = prepare(c, fixture, p);
                    wave.invoke(raid, fixture.pos);
                    var members = new ArrayList<>(raid.getAllRaiders()); cleanup.addAll(members);
                    c.assertFalse(members.isEmpty(), "Owned vanilla raid actually spawns a wave");
                    for (var raider : members) {
                        raider.setAiDisabled(true); fixture.world.tickEntity(raider);
                        for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET))
                            c.assertTrue(NetheriteMobsChallenge.isIssued(raider.getEquippedStack(slot)), "Actual wave receives issued armor");
                    }
                    s.activate(endRaids ? "netherite_mobs" : ID);
                    c.assertTrue(raid.hasStopped() && !raid.hasWon(), "Prior activation's unfinished raid stops");
                    for (var raider : members) {
                        c.assertTrue(raider.isAlive() && !raider.isRemoved(), "Spawned raider remains alive");
                        c.assertTrue(NetheriteMobsChallenge.isIssued(raider.getEquippedStack(EquipmentSlot.CHEST)), "Issued equipment remains");
                    }
                    if (endRaids) {
                        c.assertFalse(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Ending raid rule removes only own omen");
                        var fresh = EntityType.PILLAGER.create(fixture.world); cleanup.add(fresh);
                        fresh.refreshPositionAndAngles(fixture.pos, 0, 0); fresh.setAiDisabled(true);
                        fixture.world.spawnEntity(fresh); fixture.world.tickEntity(fresh);
                        c.assertTrue(NetheriteMobsChallenge.isIssued(fresh.getEquippedStack(EquipmentSlot.CHEST)), "Remaining armor rule equips new mobs");
                    } else {
                        c.assertTrue(p.hasStatusEffect(StatusEffects.BAD_OMEN), "Remaining raid rule starts fresh preparation");
                        var next = prepare(c, fixture, p); wave.invoke(next, fixture.pos);
                        var fresh = new ArrayList<>(next.getAllRaiders()); cleanup.addAll(fresh);
                        c.assertFalse(fresh.isEmpty(), "Remaining raid rule spawns actual next wave");
                        for (var raider : fresh) {
                            raider.setAiDisabled(true); fixture.world.tickEntity(raider);
                            c.assertFalse(NetheriteMobsChallenge.isIssued(raider.getEquippedStack(EquipmentSlot.CHEST)), "Ended armor rule does not equip new wave");
                        }
                    }
                    cleanup.forEach(net.minecraft.entity.Entity::discard);
                }
            } finally { cleanup.forEach(net.minecraft.entity.Entity::discard); }
        }
        c.complete();
    }

    private static Raid prepare(TestContext c, Village fixture, ServerPlayerEntity player) throws Exception {
        EndlessRaidChallenge.check(player); tickEffects(player);
        c.assertTrue(player.hasStatusEffect(StatusEffects.RAID_OMEN), "Real village begins preparation");
        int remaining = player.getStatusEffect(StatusEffects.RAID_OMEN).getDuration();
        for (int i = 0; i < remaining + 1; i++) tickEffects(player);
        var raid = fixture.world.getRaidAt(player.getBlockPos());
        c.assertTrue(raid != null && !raid.hasStopped(), "Vanilla countdown creates current raid");
        return raid;
    }

    private static void setRaidField(Raid raid, String name, Object value) throws Exception {
        var field = Raid.class.getDeclaredField(name); field.setAccessible(true); field.set(raid, value);
    }

    private static void tickEffects(ServerPlayerEntity player) throws Exception {
        var tick = LivingEntity.class.getDeclaredMethod("tickStatusEffects");
        tick.setAccessible(true); tick.invoke(player);
    }

    private static NbtCompound owned(TestContext c) {
        return EndlessRaidState.get(c.getWorld().getServer()).writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
    }

    private static final class Village implements AutoCloseable {
        final ServerWorld world;
        final BlockPos pos;
        final Difficulty difficulty;
        final boolean disableRaids;
        final NbtCompound originalOwnership;
        final List<Integer> oldRaidIds;

        Village(TestContext c) {
            world = c.getWorld(); pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
            difficulty = world.getDifficulty();
            disableRaids = world.getGameRules().getBoolean(GameRules.DISABLE_RAIDS);
            originalOwnership = owned(c);
            oldRaidIds = raids().stream().map(Raid::getRaidId).toList();
            world.getServer().setDifficulty(Difficulty.NORMAL, true);
            world.getGameRules().get(GameRules.DISABLE_RAIDS).set(false, world.getServer());
            addPoi(pos);
            c.assertTrue(world.isNearOccupiedPointOfInterest(pos), "Fixture is a real occupied vanilla village POI");
        }

        void addPoi(BlockPos point) {
            var storage = world.getPointOfInterestStorage();
            storage.add(point, world.getRegistryManager().get(RegistryKeys.POINT_OF_INTEREST_TYPE).entryOf(PointOfInterestTypes.HOME));
            storage.getPosition(type -> type.matchesKey(PointOfInterestTypes.HOME), (type, candidate) -> candidate.equals(point), point, 1);
            storage.tick(() -> true);
        }

        void place(ServerPlayerEntity p) {
            p.teleport(world, pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0); p.onTeleportationDone();
        }

        List<Raid> raids() {
            var nbt = world.getRaidManager().writeNbt(new NbtCompound(), world.getRegistryManager());
            var list = nbt.getList("Raids", NbtElement.COMPOUND_TYPE);
            var raids = new ArrayList<Raid>();
            for (int i = 0; i < list.size(); i++) {
                var raid = world.getRaidManager().getRaid(list.getCompound(i).getInt("Id"));
                if (!raid.hasStopped()) raids.add(raid);
            }
            return raids;
        }

        @Override public void close() {
            for (var raid : raids()) if (!oldRaidIds.contains(raid.getRaidId())) raid.invalidate();
            world.getPointOfInterestStorage().remove(pos);
            world.getServer().setDifficulty(difficulty, true);
            world.getGameRules().get(GameRules.DISABLE_RAIDS).set(disableRaids, world.getServer());
            world.getServer().getOverworld().getPersistentStateManager().set(EndlessRaidState.KEY,
                    EndlessRaidState.fromNbt(originalOwnership, world.getRegistryManager()));
        }
    }
}
