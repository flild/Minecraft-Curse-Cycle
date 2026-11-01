package com.cursecycle;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.event.ChallengeEventManager;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tests the shared dispatch/persistence contract, without relying on a challenge's own dedup. */
public class FoundationGameTest implements FabricGameTest {
    private static final String ID = "test_easy";

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "foundation")
    public void overlappingScopesDispatchEachLifecycleAndTickOnce(TestContext c) {
        var original = ChallengeRegistry.get(ID);
        var probe = new Probe();
        try (var s = new ChallengeScenario(c)) {
            ChallengeRegistry.register(probe);
            s.activate(ID);
            s.state.getActiveChallengeIds().add(ID); // Also tolerate duplicate saved IDs.
            UUID uuid = UUID.randomUUID();
            s.state.getPersonalActiveChallengeIds(uuid).addAll(List.of(ID, ID));
            var player = s.player(uuid);
            c.assertEquals(1L, probe.count("join", uuid), "Join once for the effective union");

            CycleManager.onServerTick(player.getServer(), CurseCycleMod.getConfig());
            c.assertEquals(1L, probe.count("tick", uuid), "One tick, not one per scope/list entry");
            var eventPlayer = player;
            ChallengeEventManager.dispatchToActive(player.getServer(), player,
                    challenge -> challenge.onItemUse(eventPlayer.getServer(), eventPlayer, net.minecraft.item.ItemStack.EMPTY));
            c.assertEquals(1L, probe.count("event", uuid), "Event uses the same effective union");

            player = s.respawn(player, true);
            c.assertEquals(1L, probe.count("respawn", uuid), "Replacement callback once");
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.networkHandler, player.getServer());
            c.assertEquals(1L, probe.count("leave", uuid), "Leave once for the effective union");
        } finally {
            ChallengeRegistry.register(original);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "foundation")
    public void encodedDuplicatesAndResetDoNotRepeatStartOrStop(TestContext c) throws Exception {
        var original = ChallengeRegistry.get(ID);
        var probe = new Probe();
        try (var s = new ChallengeScenario(c)) {
            var player = s.player();
            var server = player.getServer();
            ChallengeRegistry.register(probe);
            s.activate("double_challenge:test_easy,test_easy");
            c.assertEquals(1L, probe.count("start", player.getUuid()), "Duplicate encoded ID starts once");
            c.assertEquals(List.of(ID), s.state.getActiveChallengeIds(), "Saved activation is unique");
            s.state.getActiveChallengeIds().add(ID);
            s.state.getPersonalActiveChallengeIds(player.getUuid()).addAll(List.of(ID, ID));
            long stopsBeforeReset = probe.count("stop", player.getUuid());
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "fdc reset");
            c.assertEquals(probe.count("stop", player.getUuid()) - stopsBeforeReset, 1L, "Reset stops effective ID once");
        } finally {
            ChallengeRegistry.register(original);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "foundation")
    public void checkingInactiveMechanicsDoesNotCreatePersonalAssignments(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var player = s.player();
            s.state.getPersonalActiveChallenges().clear();
            for (int i = 0; i < 100; i++) {
                ChallengeActivity.active(player, "no_sprint");
                com.cursecycle.challenge.impl.NoSprintChallenge.isNoSprintActiveForPlayer(player);
                com.cursecycle.challenge.impl.NoJumpingChallenge.isNoJumpingActiveForPlayer(player);
                CycleManager.onServerTick(player.getServer(), CurseCycleMod.getConfig());
            }
            c.assertTrue(s.state.getPersonalActiveChallenges().isEmpty(), "Read-only gates/ticks must not retain UUIDs");
            var saved = s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
            c.assertTrue(saved.getCompound("personalActiveChallenges").isEmpty(), "No empty assignments written to NBT");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "foundation")
    public void activationAndPersonalDataSurviveRealDiskReload(TestContext c) throws Exception {
        try (var s = new ChallengeScenario(c)) {
            s.activate("no_sprint");
            var uuid = UUID.randomUUID();
            s.state.getPersonalActiveChallengeIds(uuid).add("no_jumping");
            s.state.getChallengeData().putString("foundation", "saved");
            s.state.markDirty();
            long serial = s.state.getActivationSerial();
            var manager = c.getWorld().getServer().getOverworld().getPersistentStateManager();
            manager.save();
            var disk = manager.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var restored = CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager());
            c.assertEquals(serial, restored.getActivationSerial(), "Disk keeps activation identity");
            c.assertEquals(List.of("no_sprint"), restored.getActiveChallengeIds(), "Disk keeps global activation");
            c.assertEquals(List.of("no_jumping"), restored.getPersonalActiveChallengeIds(uuid), "Offline owner survives disk");
            c.assertEquals("saved", restored.getChallengeData().getString("foundation"), "Custom data really saved");
            manager.set(CurseCycleState.STATE_KEY, restored);
            CycleManager.activateChallenge(c.getWorld().getServer(), restored, "test_medium");
            c.assertEquals(serial + 1, restored.getActivationSerial(), "Replacement advances identity");
            c.assertTrue(restored.getChallengeData().isEmpty(), "Old activation data removed");
            c.assertEquals(List.of("no_jumping"), restored.getPersonalActiveChallengeIds(uuid), "Global change keeps personal assignment");
        }
        c.complete();
    }

    private static final class Probe extends Challenge {
        private final List<String> calls = new ArrayList<>();
        Probe() { super(ID, "probe", "", "", ChallengeDifficulty.EASY, ChallengeScope.GLOBAL); }
        long count(String event, UUID uuid) { return calls.stream().filter((event + ":" + uuid)::equals).count(); }
        private void record(String event, ServerPlayerEntity player) { calls.add(event + ":" + player.getUuid()); }
        @Override public void onStart(MinecraftServer server, ServerPlayerEntity p) { record("start", p); }
        @Override public void onStop(MinecraftServer server, ServerPlayerEntity p) { record("stop", p); }
        @Override public void onTick(MinecraftServer server, ServerPlayerEntity p) { record("tick", p); }
        @Override public void onPlayerJoin(MinecraftServer server, ServerPlayerEntity p) { record("join", p); }
        @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity p) { record("leave", p); }
        @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity old, ServerPlayerEntity p, boolean alive) {
            record("respawn", p);
        }
        @Override public void onItemUse(MinecraftServer server, ServerPlayerEntity p, net.minecraft.item.ItemStack stack) {
            record("event", p);
        }
    }
}
