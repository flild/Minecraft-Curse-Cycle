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
            c.assertTrue(player.hasStatusEffect(StatusEffects.POISON), "Previous challenge actually applied effects");
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
            c.assertTrue(first.hasStatusEffect(StatusEffects.POISON), "Personal effect really active");
            s.state.setNextChallengeId("double_challenge:no_sprint,no_jumping");
            c.assertEquals(1, first.getServer().getCommandManager().getDispatcher().execute(
                    "cursecycle activate_next", first.getServer().getCommandSource().withLevel(2)), "Long alias works");
            c.assertEquals(1, probe.stops.get(first.getUuid()), "Overlapping scopes stop once");
            c.assertEquals(1, probe.stops.get(second.getUuid()), "Global challenge stops on every player");
            c.assertEquals(List.of("no_sprint", "no_jumping"), s.state.getActiveChallengeIds(), "Both children started");
            c.assertTrue(s.state.getPersonalActiveChallenges().isEmpty(), "Offline and online personal assignments cleared");
            c.assertFalse(first.hasStatusEffect(StatusEffects.POISON), "Personal effects removed");
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

    private static final class StopProbe extends Challenge {
        final Map<UUID, Integer> stops = new HashMap<>();
        StopProbe() { super("test_easy", "manual cleanup probe", "", "", ChallengeDifficulty.EASY, ChallengeScope.GLOBAL); }
        @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
            stops.merge(player.getUuid(), 1, Integer::sum);
        }
    }
}
