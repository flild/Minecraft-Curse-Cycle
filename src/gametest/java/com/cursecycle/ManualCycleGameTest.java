package com.cursecycle;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.effect.EffectLedger;
import com.cursecycle.challenge.impl.AllDebuffsChallenge;
import com.cursecycle.network.CurseCycleSyncPayload;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ManualCycleGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "cycle_sleep", tickLimit = 320)
    public void twoDayChallengesReplaceEachOtherThroughTwoRealBedSleeps(TestContext c) {
        var server = c.getWorld().getServer();
        var world = server.getOverworld();
        var config = CurseCycleMod.getConfig();
        var settings = com.cursecycle.config.TimingSettings.from(config);
        long originalTime = world.getTimeOfDay();
        boolean daylight = world.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE);
        int sleepPercentage = world.getGameRules().getInt(net.minecraft.world.GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        var scenario = new ChallengeScenario(c);
        var player = scenario.player();
        var foot = c.getAbsolutePos(new net.minecraft.util.math.BlockPos(2, 3, 2));
        var head = foot.east();
        var originalFoot = world.getBlockState(foot);
        var originalHead = world.getBlockState(head);
        var bed = net.minecraft.block.Blocks.RED_BED.getDefaultState()
                .with(net.minecraft.block.BedBlock.FACING, net.minecraft.util.math.Direction.EAST);
        world.setBlockState(foot, bed, net.minecraft.block.Block.NOTIFY_LISTENERS);
        world.setBlockState(head, bed.with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.HEAD),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        world.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(true, server);
        world.getGameRules().get(net.minecraft.world.GameRules.PLAYERS_SLEEPING_PERCENTAGE).set(100, server);
        config.challengeIntervalDays = 2;
        config.noticeLeadTimeDays = 1;
        world.setTimeOfDay(0);
        scenario.state.restartCycleAt(0);
        scenario.activate("slippery_hands");
        long serial = scenario.state.getActivationSerial();
        String[] choices = {"blocked_direction", "slippery_hands"};
        int[] stage = {0};
        boolean[] done = {false};
        Runnable cleanup = () -> {
            done[0] = true;
            if (player.isSleeping()) player.wakeUp(true, true);
            scenario.close();
            world.setBlockState(foot, originalFoot, net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(head, originalHead, net.minecraft.block.Block.NOTIFY_LISTENERS);
            settings.applyTo(config);
            world.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(daylight, server);
            world.getGameRules().get(net.minecraft.world.GameRules.PLAYERS_SLEEPING_PERCENTAGE).set(sleepPercentage, server);
            world.setTimeOfDay(originalTime);
        };
        Runnable sleep = () -> {
            scenario.state.getCurrentPool().clear();
            scenario.state.getCurrentPool().add(choices[stage[0]]);
            world.setTimeOfDay(stage[0] * 48_000L + 24_000);
            CycleManager.updateSchedule(server, config);
            CycleManager.syncState(server, scenario.state, config);
            var preview = latestSync(scenario, player);
            c.assertEquals(choices[stage[0]], preview.nextChallengeId(), "Wheel announces one day before replacement");
            c.assertEquals(24_000L, preview.ticksUntilNext(), "Announcement does not reset the interval");
            world.setTimeOfDay(stage[0] * 48_000L + 37_000);
            world.calculateAmbientDarkness();
            player.refreshPositionAndAngles(foot.getX() + 0.5, foot.getY() + 1, foot.getZ() + 0.5, 0, 0);
            c.assertTrue(player.trySleep(head).right().isPresent(), "Vanilla bed interaction accepted");
            c.assertTrue(player.isSleeping(), "Real bed sleep started");
        };
        try {
            sleep.run();
        } catch (Throwable failure) {
            cleanup.run();
            throw failure;
        }
        c.runAtEveryTick(() -> {
            if (done[0]) return;
            try {
                // Embedded connections do not run ServerPlayNetworkHandler.tick/playerTick.
                player.playerTick();
                CycleManager.updateSchedule(server, config);
                c.assertFalse(scenario.state.getActiveChallengeIds().isEmpty(), "No empty tick between challenges");
                long boundary = (stage[0] + 1L) * 48_000;
                if (world.getTimeOfDay() < boundary) return;
                c.assertFalse(player.isSleeping(), "Vanilla sleep woke the player at sunrise");
                var changed = latestSync(scenario, player);
                c.assertEquals(List.of(choices[stage[0]]), changed.activeChallengeIds(), "HUD immediately receives the replacement");
                c.assertEquals("", changed.nextChallengeId(), "Previous wheel result consumed");
                c.assertEquals(serial + stage[0] + 1, scenario.state.getActivationSerial(), "One activation per two-day interval");
                c.assertTrue(changed.ticksUntilNext() > 47_900 && changed.ticksUntilNext() <= 48_000,
                        "Next change is two days away with the new challenge already active");
                if (stage[0] == 0) {
                    c.assertFalse(com.cursecycle.challenge.impl.SlipperyHandsChallenge.isActiveForPlayer(player), "Ice effect removed");
                    c.assertTrue(com.cursecycle.challenge.impl.BlockedDirectionChallenge.reconcile(server) != null,
                            "Replacement direction actually initialized");
                    stage[0]++;
                    sleep.run();
                } else {
                    c.assertTrue(com.cursecycle.challenge.impl.SlipperyHandsChallenge.isActiveForPlayer(player), "Following challenge takes effect too");
                    cleanup.run();
                    c.complete();
                }
            } catch (Throwable failure) {
                cleanup.run();
                throw failure;
            }
        });
        c.waitAndRun(300, () -> {
            if (done[0]) return;
            cleanup.run();
            throw new AssertionError("Two bed sleeps did not complete within 300 ticks");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "manual_controls")
    public void emptySavedCycleRecoversImmediatelyAndSynchronizesTheReplacement(TestContext c) {
        var server = c.getWorld().getServer();
        long originalTime = server.getOverworld().getTimeOfDay();
        try (var scenario = new ChallengeScenario(c)) {
            var player = scenario.player();
            scenario.state.restartCycleAt(originalTime);
            scenario.state.getActiveChallengeIds().clear();
            scenario.state.setNextChallengeId("blocked_direction");
            var saved = scenario.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
            var restored = CurseCycleState.createFromNbt(saved, c.getWorld().getRegistryManager());
            server.getOverworld().getPersistentStateManager().set(CurseCycleState.STATE_KEY, restored);
            CycleManager.updateSchedule(server, CurseCycleMod.getConfig());
            c.assertEquals(List.of("blocked_direction"), restored.getActiveChallengeIds(), "Recover without waiting another interval");
            c.assertEquals(List.of("blocked_direction"), latestSync(scenario, player).activeChallengeIds(), "HUD also recovers");
            c.assertTrue(com.cursecycle.challenge.impl.BlockedDirectionChallenge.reconcile(server) != null, "Direction is ready");
        } finally {
            server.getOverworld().setTimeOfDay(originalTime);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "manual_controls")
    public void menuSettingsCheckPermissionsAndPersistWithoutResettingGameplay(TestContext c) throws Exception {
        var server = c.getWorld().getServer();
        var config = CurseCycleMod.getConfig();
        var original = com.cursecycle.config.TimingSettings.from(config);
        long time = server.getOverworld().getTimeOfDay();
        var path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("cursecycle.json");
        byte[] oldFile = java.nio.file.Files.readAllBytes(path);
        try (var s = new ChallengeScenario(c)) {
            var player = s.player();
            var request = new com.cursecycle.network.TimingSettingsRequestPayload(true, 2, 1, 90);
            c.assertFalse(com.cursecycle.config.TimingSettingsService.canEdit(player), "Ordinary player cannot edit");
            c.assertEquals(com.cursecycle.network.TimingSettingsPayload.DENIED,
                    com.cursecycle.config.TimingSettingsService.handle(player, request), "Forged save denied");
            c.assertEquals(original, com.cursecycle.config.TimingSettings.from(config), "Denied packet changes nothing");
            c.assertEquals(com.cursecycle.network.TimingSettingsPayload.SNAPSHOT,
                    com.cursecycle.config.TimingSettingsService.handle(player,
                            new com.cursecycle.network.TimingSettingsRequestPayload(false, 0, 0, 0)), "Everyone can read");
            server.getPlayerManager().getOpList().add(new net.minecraft.server.OperatorEntry(player.getGameProfile(), 2, false));
            try {
                c.assertTrue(com.cursecycle.config.TimingSettingsService.snapshot(player, 0).editable(), "Operator can edit");
                var before = s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
                c.assertEquals(com.cursecycle.network.TimingSettingsPayload.INVALID,
                        com.cursecycle.config.TimingSettingsService.handle(player,
                                new com.cursecycle.network.TimingSettingsRequestPayload(true, 1, 2, 60)), "Invalid schedule rejected");
                c.assertEquals(before, s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager()), "Invalid packet leaves state intact");
                server.getOverworld().setTimeOfDay(119_999);
                s.state.restartCycleAt(0);
                s.state.setNextChallengeId("no_jumping");
                long serial = s.state.getActivationSerial();
                var active = List.copyOf(s.state.getActiveChallengeIds());
                int history = s.state.getHistory().size();
                c.assertEquals(com.cursecycle.network.TimingSettingsPayload.SAVED,
                        com.cursecycle.config.TimingSettingsService.handle(player, request), "Operator save succeeds");
                c.assertEquals(request.settings(), com.cursecycle.config.TimingSettings.from(com.cursecycle.config.CurseCycleConfig.load()), "Real config reload retains timing");
                c.assertEquals(active, s.state.getActiveChallengeIds(), "Current restrictions retained");
                c.assertEquals(history, s.state.getHistory().size(), "History retained");
                c.assertEquals(serial, s.state.getActivationSerial(), "No extra activation");
                c.assertEquals("no_jumping", s.state.getNextChallengeId(), "Announced result retained");
                c.assertEquals(119_999L, server.getOverworld().getTimeOfDay(), "World time retained");
                c.assertEquals(48_000L, latestSync(s, player).ticksUntilNext(), "HUD receives new interval immediately");
            } finally { server.getPlayerManager().removeFromOperators(player.getGameProfile()); }
            c.assertEquals(com.cursecycle.network.TimingSettingsPayload.DENIED,
                    com.cursecycle.config.TimingSettingsService.handle(player, request), "Revoked operator cannot reuse open form");
        } finally {
            original.applyTo(config);
            java.nio.file.Files.write(path, oldFile);
            server.getOverworld().setTimeOfDay(time);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "manual_controls")
    public void failedMenuSaveRollsBackConfigAndLeavesWorldUntouched(TestContext c) throws Exception {
        var server = c.getWorld().getServer();
        var config = CurseCycleMod.getConfig();
        var original = com.cursecycle.config.TimingSettings.from(config);
        var path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("cursecycle.json");
        byte[] oldFile = java.nio.file.Files.readAllBytes(path);
        try (var s = new ChallengeScenario(c)) {
            var player = s.player();
            server.getPlayerManager().getOpList().add(new net.minecraft.server.OperatorEntry(player.getGameProfile(), 2, false));
            try {
                var before = s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
                java.nio.file.Files.delete(path);
                java.nio.file.Files.createDirectory(path);
                java.nio.file.Files.writeString(path.resolve("blocked"), "fixture");
                c.assertEquals(com.cursecycle.network.TimingSettingsPayload.SAVE_FAILED,
                        com.cursecycle.config.TimingSettingsService.handle(player,
                                new com.cursecycle.network.TimingSettingsRequestPayload(true, original.intervalDays() + 1, 0, 7)),
                        "Disk error returned to menu");
                c.assertEquals(original, com.cursecycle.config.TimingSettings.from(config), "Memory rolled back");
                c.assertEquals(before, s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager()), "World untouched");
            } finally {
                server.getPlayerManager().removeFromOperators(player.getGameProfile());
                java.nio.file.Files.deleteIfExists(path.resolve("blocked"));
                java.nio.file.Files.deleteIfExists(path);
                java.nio.file.Files.write(path, oldFile);
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "manual_controls")
    public void freshWorldWaitsForFirstPlayerAndResetRestartsPreparation(TestContext c) throws Exception {
        var server = c.getWorld().getServer();
        long oldTime = server.getOverworld().getTimeOfDay();
        try (var s = new ChallengeScenario(c)) {
            s.state.reset();
            CycleManager.updateSchedule(server, CurseCycleMod.getConfig());
            c.assertTrue(s.state.getActiveChallengeIds().isEmpty(), "No activation on an empty server");
            c.assertTrue(s.state.getNextChallengeId() == null, "No wheel before the first player");
            var player = s.player();
            var joined = latestSync(s, player);
            c.assertTrue(joined.activeChallengeIds().isEmpty(), "Join receives no restrictions");
            c.assertEquals(60 * 20L, joined.ticksUntilNext(), "Join receives one minute of preparation");
            for (int i = 0; i < 50; i++) CycleManager.updateSchedule(server, CurseCycleMod.getConfig());
            c.assertEquals(60 * 20L, CycleManager.ticksUntilNext(s.state, CurseCycleMod.getConfig(), 0),
                    "Repeated schedule calls in one tick do not consume preparation");
            var source = server.getCommandSource().withLevel(2);
            var dispatcher = server.getCommandManager().getDispatcher();
            dispatcher.execute("fdc activate no_sprint", source);
            c.assertFalse(s.state.isInitialPreparationPending(), "Manual activation bypasses preparation");
            dispatcher.execute("fdc reset", source);
            c.assertTrue(s.state.isInitialPreparationPending(), "Reset restores preparation");
            c.assertTrue(s.state.getActiveChallengeIds().isEmpty(), "Reset does not immediately activate a challenge");
            c.assertEquals(60 * 20L, latestSync(s, player).ticksUntilNext(), "Reset immediately sends preparation to HUD");
        } finally {
            server.getOverworld().setTimeOfDay(oldTime);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "initial_preparation", tickLimit = 180)
    public void firstWheelPrecedesActivationAndSurvivesRealDiskReload(TestContext c) {
        var server = c.getWorld().getServer();
        var config = CurseCycleMod.getConfig();
        int oldPreparation = config.initialPreparationSeconds;
        long oldTime = server.getOverworld().getTimeOfDay();
        var s = new ChallengeScenario(c);
        var state = new CurseCycleState[]{s.state};
        Runnable cleanup = () -> {
            config.initialPreparationSeconds = oldPreparation;
            server.getOverworld().setTimeOfDay(oldTime);
            s.close();
        };
        java.util.function.Consumer<Runnable> checked = action -> {
            try { action.run(); }
            catch (RuntimeException | Error failure) { cleanup.run(); throw failure; }
        };
        checked.accept(() -> {
            config.initialPreparationSeconds = 1;
            s.state.reset();
            s.state.getCurrentPool().add("no_sprint");
            var player = s.player();
            CycleManager.updateSchedule(server, config);
            c.waitAndRun(10, () -> checked.accept(() -> {
                server.getOverworld().setTimeOfDay(12_000_000);
                CycleManager.updateSchedule(server, config);
                c.assertTrue(state[0].getActiveChallengeIds().isEmpty(), "World time jump does not activate early");
                c.assertTrue(state[0].getNextChallengeId() == null, "One second of preparation has not finished");
            }));
            c.waitAndRun(30, () -> checked.accept(() -> {
                c.assertEquals("no_sprint", state[0].getNextChallengeId(), "Initial wheel has selected its winner");
                c.assertTrue(state[0].getActiveChallengeIds().isEmpty(), "Restrictions wait for the animation");
            }));
            c.waitAndRun(50, () -> checked.accept(() -> {
                var manager = server.getOverworld().getPersistentStateManager();
                manager.save();
                try {
                    var disk = manager.readNbt(CurseCycleState.STATE_KEY, null,
                            net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
                    state[0] = CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager());
                    manager.set(CurseCycleState.STATE_KEY, state[0]);
                } catch (java.io.IOException failure) {
                    throw new RuntimeException(failure);
                }
                c.assertTrue(state[0].isInitialPreparationPending(), "Disk retains unfinished first wheel");
                c.assertEquals("no_sprint", state[0].getNextChallengeId(), "Disk retains the promised result");
            }));
            c.waitAndRun(110, () -> checked.accept(() ->
                    c.assertTrue(state[0].getActiveChallengeIds().isEmpty(), "Reload does not shorten the wheel")));
            c.waitAndRun(130, () -> {
                try {
                    c.assertEquals(List.of("no_sprint"), state[0].getActiveChallengeIds(), "Promised first challenge activated");
                    c.assertEquals(1, state[0].getHistory().size(), "Exactly one activation after reload");
                    c.assertFalse(state[0].isInitialPreparationPending(), "Regular cycle has started");
                    s.channel(player).runPendingTasks();
                    long wheels = s.channel(player).outboundMessages().stream()
                            .filter(CustomPayloadS2CPacket.class::isInstance).map(CustomPayloadS2CPacket.class::cast)
                            .map(CustomPayloadS2CPacket::payload)
                            .filter(com.cursecycle.network.TriggerWheelPayload.class::isInstance).count();
                    c.assertEquals(1L, wheels, "Exactly one wheel packet despite reload");
                    boolean fullInterval = s.channel(player).outboundMessages().stream()
                            .filter(CustomPayloadS2CPacket.class::isInstance).map(CustomPayloadS2CPacket.class::cast)
                            .map(CustomPayloadS2CPacket::payload).filter(CurseCycleSyncPayload.class::isInstance)
                            .map(CurseCycleSyncPayload.class::cast)
                            .anyMatch(payload -> payload.activeChallengeIds().equals(List.of("no_sprint"))
                                    && payload.ticksUntilNext() == config.challengeIntervalDays * 24_000L);
                    c.assertTrue(fullInterval, "Activation packet starts a full configured interval");
                } finally {
                    cleanup.run();
                }
                c.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "manual_controls")
    public void actualWheelWinnerStartsNowAndPreviousEffectsAreReleased(TestContext c) throws Exception {
        var server = c.getWorld().getServer();
        long originalTime = server.getOverworld().getTimeOfDay();
        try (var s = new ChallengeScenario(c)) {
            var player = s.player();
            player.getAbilities().invulnerable = true;
            var dispatcher = server.getCommandManager().getDispatcher();
            var source = server.getCommandSource().withLevel(2);
            server.getOverworld().setTimeOfDay(119_999);
            c.assertEquals(1, dispatcher.execute("fdc activate all_debuffs", source), "Set up previous challenge");
            c.assertTrue(player.hasStatusEffect(StatusEffects.BLINDNESS), "Previous challenge actually applied effects");
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 600, 1));
            c.assertEquals(1, dispatcher.execute("fdc trigger_spin", source), "Actual wheel command");
            String promised = s.state.getNextChallengeId();
            c.assertTrue(promised != null, "Wheel saved its result");
            var expected = ChallengeUtils.resolveToActiveIds(promised);
            var remainingBag = List.copyOf(s.state.getCurrentPool());
            int history = s.state.getHistory().size();
            long serial = s.state.getActivationSerial();

            c.assertEquals(1, dispatcher.execute("fdc activate_next", source), "Start the wheel winner");
            c.assertEquals(expected, s.state.getActiveChallengeIds(), "Exactly the promised result");
            c.assertTrue(s.state.getNextChallengeId() == null, "Selection consumed");
            c.assertEquals(history + 1, s.state.getHistory().size(), "Only one history entry");
            c.assertEquals(serial + 1, s.state.getActivationSerial(), "Only one activation");
            c.assertEquals(remainingBag, s.state.getCurrentPool(), "Unseen challenges remain in the bag");
            c.assertEquals(119_999L, server.getOverworld().getTimeOfDay(), "World clock unchanged");
            c.assertEquals(120_000L, CycleManager.ticksUntilNext(s.state, CurseCycleMod.getConfig(), 119_999),
                    "Full five-day interval starts now");
            for (var effect : AllDebuffsChallenge.effects())
                c.assertFalse(EffectLedger.of(player).owns("all_debuffs", effect), "Previous effect ownership released");
            c.assertTrue(player.hasStatusEffect(StatusEffects.SPEED), "Unrelated vanilla effect survives");

            server.getOverworld().setTimeOfDay(120_000);
            CycleManager.updateSchedule(server, CurseCycleMod.getConfig());
            c.assertEquals(serial + 1, s.state.getActivationSerial(), "Old world boundary does not reactivate");
            c.assertEquals(0, dispatcher.execute("fdc activate_next", source), "Cannot consume the same result twice");
            c.assertEquals(history + 1, s.state.getHistory().size(), "Failed repeat adds no history");
        } finally {
            server.getOverworld().setTimeOfDay(originalTime);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "manual_controls")
    public void nextDoubleStopsEachEffectiveGlobalAndPersonalChallengeOnce(TestContext c) throws Exception {
        var oldChallenge = ChallengeRegistry.get("test_easy");
        var probe = new StopProbe();
        try (var s = new ChallengeScenario(c)) {
            var first = s.player();
            var second = s.player();
            ChallengeRegistry.register(probe);
            s.state.getActiveChallengeIds().clear();
            s.state.getActiveChallengeIds().addAll(List.of("test_easy", "test_easy"));
            s.state.getPersonalActiveChallengeIds(first.getUuid()).addAll(
                    List.of("test_easy", "test_easy", "all_debuffs"));
            s.state.getPersonalActiveChallengeIds(UUID.randomUUID()).add("all_debuffs");
            ChallengeRegistry.get("all_debuffs").onStart(first.getServer(), first);
            c.assertTrue(first.hasStatusEffect(StatusEffects.BLINDNESS), "Personal effect really active");
            s.state.setNextChallengeId("double_challenge:no_sprint,no_jumping");
            c.assertEquals(1, first.getServer().getCommandManager().getDispatcher().execute(
                    "cursecycle activate_next", first.getServer().getCommandSource().withLevel(2)), "Long alias works");
            c.assertEquals(1, probe.stops.get(first.getUuid()), "Overlapping scopes stop once");
            c.assertEquals(1, probe.stops.get(second.getUuid()), "Global challenge stops on every player");
            c.assertEquals(List.of("no_sprint", "no_jumping"), s.state.getActiveChallengeIds(), "Both children started");
            c.assertTrue(s.state.getPersonalActiveChallenges().isEmpty(), "Offline and online personal assignments cleared");
            c.assertFalse(first.hasStatusEffect(StatusEffects.BLINDNESS), "Personal effects removed");
        } finally {
            ChallengeRegistry.register(oldChallenge);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "manual_controls")
    public void invalidSelectionsPermissionsAndCompletionLeaveActiveChallengeSafe(TestContext c) throws Exception {
        var disabled = ChallengeRegistry.get("no_jumping");
        boolean originalEnabled = disabled.isEnabled();
        try (var s = new ChallengeScenario(c)) {
            var server = c.getWorld().getServer();
            var source = server.getCommandSource().withLevel(2);
            var dispatcher = server.getCommandManager().getDispatcher();
            dispatcher.execute("fdc activate no_sprint", source);
            disabled.setEnabled(false);
            for (String pending : new String[]{null, "removed_challenge", "no_jumping",
                    "double_challenge:double_challenge,no_sprint"}) {
                s.state.setNextChallengeId(pending);
                var before = s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
                c.assertEquals(0, dispatcher.execute("fdc activate_next", source), "Unavailable next is rejected");
                c.assertEquals(before, s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager()),
                        "Rejection preserves all state and effects");
            }
            for (String alias : List.of("fdc", "cursecycle")) {
                try {
                    dispatcher.execute(alias + " activate_next", server.getCommandSource().withLevel(1));
                    throw new AssertionError("Non-operator was allowed to change challenges");
                } catch (CommandSyntaxException expected) {
                    // Permission is enforced by the real Brigadier tree.
                }
                var completions = dispatcher.getCompletionSuggestions(dispatcher.parse(alias + " activate no_", source))
                        .join().getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList();
                c.assertTrue(completions.contains("no_sprint"), "Tab suggests enabled IDs");
                c.assertFalse(completions.contains("no_jumping"), "Tab omits disabled IDs");
            }
        } finally {
            disabled.setEnabled(originalEnabled);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "manual_controls")
    public void directActivationAndRealDiskReloadKeepTheNewTimerAndWorldTime(TestContext c) throws Exception {
        var server = c.getWorld().getServer();
        long originalTime = server.getOverworld().getTimeOfDay();
        try (var s = new ChallengeScenario(c)) {
            server.getOverworld().setTimeOfDay(119_950);
            var source = server.getCommandSource().withLevel(2);
            for (String alias : List.of("fdc", "cursecycle")) {
                s.state.setNextChallengeId("gills");
                c.assertEquals(1, server.getCommandManager().getDispatcher().execute(alias + " activate no_sprint", source),
                        "Direct activation replaces and restarts");
                c.assertTrue(s.state.getNextChallengeId() == null, "Old upcoming selection cleared");
                c.assertEquals(List.of("no_sprint"), s.state.getActiveChallengeIds(), "Direct choice active");
                c.assertEquals(119_950L, server.getOverworld().getTimeOfDay(), "Day/time unchanged");
            }
            var joined = s.player();
            s.channel(joined).runPendingTasks();
            var payloads = s.channel(joined).outboundMessages().stream()
                    .filter(CustomPayloadS2CPacket.class::isInstance).map(CustomPayloadS2CPacket.class::cast)
                    .map(CustomPayloadS2CPacket::payload).filter(CurseCycleSyncPayload.class::isInstance)
                    .map(CurseCycleSyncPayload.class::cast).toList();
            c.assertFalse(payloads.isEmpty(), "Real join sends the HUD state");
            c.assertEquals(120_000L, payloads.getLast().ticksUntilNext(), "Join uses the restarted countdown");

            var manager = server.getOverworld().getPersistentStateManager();
            manager.save();
            var disk = manager.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var restored = CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager());
            c.assertEquals(119_950L, restored.getCycleOriginTicks(), "Real disk retains new schedule origin");
            manager.set(CurseCycleState.STATE_KEY, restored);
            long serial = restored.getActivationSerial();
            server.getOverworld().setTimeOfDay(120_001);
            CycleManager.updateSchedule(server, CurseCycleMod.getConfig());
            c.assertEquals(serial, restored.getActivationSerial(), "Reload does not trigger another activation");
            c.assertEquals(119_949L, CycleManager.ticksUntilNext(restored, CurseCycleMod.getConfig(), 120_001),
                    "Countdown continues from command, not old boundary");
        } finally {
            server.getOverworld().setTimeOfDay(originalTime);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "manual_controls")
    public void configuredGameDaysAnnounceThenReplaceTheHudStateOnEveryAutomaticChange(TestContext c) {
        var server = c.getWorld().getServer();
        var config = CurseCycleMod.getConfig();
        double oldInterval = config.challengeIntervalDays;
        double oldNotice = config.noticeLeadTimeDays;
        long oldTime = server.getOverworld().getTimeOfDay();
        try (var scenario = new ChallengeScenario(c)) {
            config.challengeIntervalDays = 3;
            config.noticeLeadTimeDays = 1;
            long origin = 1234;
            server.getOverworld().setTimeOfDay(origin);
            scenario.state.restartCycleAt(origin);
            var player = scenario.player();
            scenario.activate("no_sprint");
            for (int cycle = 0; cycle < 2; cycle++) {
                String current = cycle == 0 ? "no_sprint" : "no_jumping";
                String upcoming = cycle == 0 ? "no_jumping" : "no_sprint";
                scenario.state.getCurrentPool().clear();
                scenario.state.getCurrentPool().add(upcoming);
                long finalDay = origin + (cycle * 3L + 2) * 24_000L;
                server.getOverworld().setTimeOfDay(finalDay - 1);
                CycleManager.updateSchedule(server, config);
                c.assertTrue(scenario.state.getNextChallengeId() == null, "No wheel before the final game day");
                server.getOverworld().setTimeOfDay(finalDay);
                CycleManager.updateSchedule(server, config);
                c.assertEquals(upcoming, scenario.state.getNextChallengeId(), "Wheel picks the next challenge one day early");
                CycleManager.syncState(server, scenario.state, config);
                var preview = latestSync(scenario, player);
                c.assertEquals(List.of(current), preview.activeChallengeIds(), "Current challenge remains active until the change");
                c.assertEquals(upcoming, preview.nextChallengeId(), "HUD shows the upcoming challenge");
                c.assertEquals(1L, com.cursecycle.client.gui.ChallengeCountdown.daysRemaining(preview.ticksUntilNext()),
                        "HUD has one game day remaining");

                long boundary = origin + (cycle + 1L) * 3 * 24_000L;
                server.getOverworld().setTimeOfDay(boundary);
                CycleManager.updateSchedule(server, config);
                // Read activation's own packet, without a follow-up sync that could hide stale previews.
                var refreshed = latestSync(scenario, player);
                c.assertEquals(List.of(upcoming), refreshed.activeChallengeIds(), "Replace the old challenge rather than appending");
                c.assertEquals("", refreshed.nextChallengeId(), "Consumed preview disappears in the same packet");
                c.assertEquals(72_000L, refreshed.ticksUntilNext(), "Fresh configured interval instead of an accumulated timer");
                c.assertEquals(3L, com.cursecycle.client.gui.ChallengeCountdown.daysRemaining(refreshed.ticksUntilNext()),
                        "Counter refreshes to three days on both changes");
            }
        } finally {
            config.challengeIntervalDays = oldInterval;
            config.noticeLeadTimeDays = oldNotice;
            server.getOverworld().setTimeOfDay(oldTime);
        }
        c.complete();
    }

    private static CurseCycleSyncPayload latestSync(ChallengeScenario scenario, ServerPlayerEntity player) {
        scenario.channel(player).runPendingTasks();
        // A test may inspect the activation inside ServerWorld.tick, before vanilla enables flushing.
        scenario.channel(player).flush();
        var payloads = scenario.channel(player).outboundMessages().stream()
                .filter(CustomPayloadS2CPacket.class::isInstance).map(CustomPayloadS2CPacket.class::cast)
                .map(CustomPayloadS2CPacket::payload).filter(CurseCycleSyncPayload.class::isInstance)
                .map(CurseCycleSyncPayload.class::cast).toList();
        if (payloads.isEmpty()) throw new AssertionError("HUD state was not sent");
        return payloads.getLast();
    }

    private static final class StopProbe extends Challenge {
        final Map<UUID, Integer> stops = new HashMap<>();
        StopProbe() { super("test_easy", "manual cleanup probe", "", "", ChallengeDifficulty.EASY, ChallengeScope.GLOBAL); }
        @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
            stops.merge(player.getUuid(), 1, Integer::sum);
        }
    }
}
