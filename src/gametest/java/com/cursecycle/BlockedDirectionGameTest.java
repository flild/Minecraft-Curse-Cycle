package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.BlockedDirectionChallenge;
import com.cursecycle.network.CurseCycleSyncPayload;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class BlockedDirectionGameTest implements FabricGameTest {
    private static final String ID = BlockedDirectionChallenge.ID;

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "blocked_direction")
    public void sharedChoicePersistenceAndLifecycle(TestContext context) throws Exception {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer();
            var first = s.player();
            var second = s.player();
            var channel = s.channel(first);
            s.activate(ID);
            String choice = BlockedDirectionChallenge.reconcile(server).id();
            long serial = s.state.getActivationSerial();
            assertSync(context, channel, List.of(ID), choice);
            assertSync(context, s.channel(second), List.of(ID), choice);
            var late = s.player();
            assertSync(context, s.channel(late), List.of(ID), choice);
            for (var mode : GameMode.values()) {
                first.changeGameMode(mode);
                CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
                assertSync(context, channel, List.of(ID), choice);
            }
            first.changeGameMode(GameMode.SURVIVAL);
            first.setHealth(0);
            first = s.respawn(first, false);
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertSync(context, channel, List.of(ID), choice);
            for (var dimension : List.of(World.NETHER, World.END, World.OVERWORLD)) {
                first.teleport(server.getWorld(dimension), .5, 80, .5, 90, 0);
                first.onTeleportationDone();
                CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
                assertSync(context, channel, List.of(ID), choice);
            }
            context.assertEquals(serial, s.state.getActivationSerial(), "Lifecycle does not begin an activation");
            var persistence = server.getOverworld().getPersistentStateManager();
            persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    SharedConstants.getGameVersion().getSaveVersion().getId());
            var restored = CurseCycleState.createFromNbt(saved.getCompound("data"), context.getWorld().getRegistryManager());
            context.assertEquals(serial, restored.getActivationSerial(), "Serial saved to disk");
            context.assertEquals(choice, BlockedDirectionChallenge.reconcile(restored, bound -> {
                throw new AssertionError("Loading must not reroll");
            }).id(), "Actual enum saved to disk");
            persistence.set(CurseCycleState.STATE_KEY, restored);
            CycleManager.syncState(server, restored, CurseCycleMod.getConfig());
            assertSync(context, channel, List.of(ID), choice);
            var uuid = second.getUuid();
            disconnect(second);
            second = s.player(uuid);
            assertSync(context, s.channel(second), List.of(ID), choice);
            CycleManager.activateChallenge(server, restored, "double_challenge:blocked_direction,inverted_keyboard");
            context.assertTrue(restored.getActivationSerial() > serial, "New activation owns a new choice");
            assertSync(context, channel, List.of(ID, "inverted_keyboard"), BlockedDirectionChallenge.reconcile(server).id());
            CycleManager.activateChallenge(server, restored, "double_challenge:blocked_direction,no_sprint");
            assertSync(context, channel, List.of(ID, "no_sprint"), BlockedDirectionChallenge.reconcile(server).id());
            first.setSprinting(true);
            context.assertFalse(first.isSprinting(), "Companion restriction remains active");
            disconnect(second);
            CycleManager.activateChallenge(server, restored, "no_sprint");
            context.assertFalse(restored.getChallengeData().contains(ID), "Ended choice deleted");
            assertSync(context, channel, List.of("no_sprint"), "");
            second = s.player(uuid);
            assertSync(context, s.channel(second), List.of("no_sprint"), "");
            second.setSprinting(true);
            context.assertFalse(second.isSprinting(), "Stopping preserves no_sprint");
            var source = server.getCommandSource().withLevel(2);
            for (var command : List.of("fdc list", "fdc activate blocked_direction", "fdc active"))
                context.assertTrue(server.getCommandManager().getDispatcher().execute(command, source) > 0,
                        "Command succeeds: " + command);
            assertSync(context, s.channel(second), List.of(ID), BlockedDirectionChallenge.reconcile(server).id());
            context.assertEquals(1L, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count(), "Single registration");
        }
        context.waitAndRun(5, context::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "blocked_direction_disk")
    public void repeatedActivationsAndActualDiskPreserveChoiceAndForeignData(TestContext context) throws Exception {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer();
            var player = s.player(); var uuid = player.getUuid();
            var cargo = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 17);
            cargo.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("foreign-direction-review"));
            player.getInventory().clear(); player.getInventory().setStack(8, cargo.copy());
            player.experienceLevel = 7; player.experienceProgress = .625F; player.setHealth(17);
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE, 1200, 0));
            for (int cycle = 0; cycle < 32; cycle++) {
                long before = s.state.getActivationSerial();
                s.activate("double_challenge:blocked_direction,blocked_direction");
                context.assertEquals(before + 1, s.state.getActivationSerial(), "One new serial per activation");
                String choice = BlockedDirectionChallenge.reconcile(server).id();
                assertSync(context, s.channel(player), List.of(ID), choice);
                var saved = s.state.getChallengeData().getCompound(ID).copy();
                s.state.setDirty(false);
                for (int repeat = 0; repeat < 32; repeat++) {
                    context.assertEquals(choice, BlockedDirectionChallenge.reconcile(s.state, bound -> {
                        throw new AssertionError("Current activation must not draw again");
                    }).id(), "Stable selection");
                }
                context.assertFalse(s.state.isDirty(), "Unchanged reconcile does not dirty state");
                context.assertEquals(saved, s.state.getChallengeData().getCompound(ID), "No growing choice history");
                s.activate("double_challenge:blocked_direction,no_sprint");
                player.setSprinting(true); context.assertFalse(player.isSprinting(), "Both active during pair");
                s.activate("no_sprint");
                context.assertFalse(s.state.getChallengeData().contains(ID), "End deletes own enum/serial");
                assertSync(context, s.channel(player), List.of("no_sprint"), "");
            }
            s.activate("double_challenge:blocked_direction,no_sprint");
            s.state.getChallengeData().putString("foreign", "preserve"); s.state.markDirty();
            String choice = BlockedDirectionChallenge.reconcile(server).id();
            long serial = s.state.getActivationSerial();
            disconnect(player);
            context.assertTrue(server.getPlayerManager().getPlayerList().isEmpty(), "Empty server save");
            var loaded = reload(context);
            context.assertEquals(serial, loaded.getActivationSerial(), "Disk preserves serial");
            context.assertEquals(choice, BlockedDirectionChallenge.reconcile(loaded, bound -> {
                throw new AssertionError("Actual disk load must not reroll");
            }).id(), "Disk preserves actual enum");
            context.assertEquals("preserve", loaded.getChallengeData().getString("foreign"), "Foreign world data preserved");
            player = s.player(uuid); assertForeign(context, player, cargo);
            assertSync(context, s.channel(player), List.of(ID, "no_sprint"), choice);
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            disconnect(player); loaded = reload(context);
            context.assertFalse(loaded.getChallengeData().contains(ID), "Ended disk has no choice");
            player = s.player(uuid); assertForeign(context, player, cargo);
            assertSync(context, s.channel(player), List.of("no_sprint"), "");
            CycleManager.activateChallenge(server, loaded, ID);
            disconnect(player); loaded = reload(context);
            CycleManager.activateChallenge(server, loaded, "no_sprint"); reload(context);
            player = s.player(uuid); assertForeign(context, player, cargo);
            assertSync(context, s.channel(player), List.of("no_sprint"), "");
            player.setSprinting(true); context.assertFalse(player.isSprinting(), "Offline end preserves companion");
            disconnect(player);
        }
        context.complete();
    }

    private static CurseCycleState reload(TestContext context) throws Exception {
        var manager = context.getWorld().getServer().getOverworld().getPersistentStateManager();
        CurseCycleState.getServerState(context.getWorld().getServer()).markDirty(); manager.save();
        var disk = manager.readNbt(CurseCycleState.STATE_KEY, null,
                SharedConstants.getGameVersion().getSaveVersion().getId());
        var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), context.getWorld().getRegistryManager());
        manager.set(CurseCycleState.STATE_KEY, loaded);
        return loaded;
    }

    private static void assertForeign(TestContext context, net.minecraft.server.network.ServerPlayerEntity player,
                                      net.minecraft.item.ItemStack cargo) {
        context.assertTrue(net.minecraft.item.ItemStack.areEqual(cargo, player.getInventory().getStack(8)), "Named items preserved");
        context.assertEquals(7, player.experienceLevel, "XP level preserved");
        context.assertEquals(.625F, player.experienceProgress, "XP progress preserved");
        context.assertEquals(17F, player.getHealth(), "HP preserved");
        context.assertTrue(player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE), "Foreign effect preserved");
    }

    private static void disconnect(net.minecraft.server.network.ServerPlayerEntity player) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                .onPlayDisconnect(player.networkHandler, player.getServer());
        player.getServer().getPlayerManager().remove(player);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "blocked_direction")
    public void emptyServerPersonalRecipientsAndDisabledCleanup(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer();
            // Earlier batches may leave embedded players; isolate the genuinely empty-server path.
            for (var previous : new ArrayList<>(server.getPlayerManager().getPlayerList()))
                server.getPlayerManager().remove(previous);
            context.assertTrue(server.getPlayerManager().getPlayerList().isEmpty(), "Empty server");
            s.activate(ID);
            var savedChoice = s.state.getChallengeData().getCompound(ID).copy();
            context.assertTrue(BlockedDirectionChallenge.Direction.fromId(savedChoice.getString("direction")) != null, "Choice before join");
            var owner = s.player();
            assertSync(context, s.channel(owner), List.of(ID), savedChoice.getString("direction"));
            context.assertEquals(savedChoice, s.state.getChallengeData().getCompound(ID), "Join does not reroll");
            var other = s.player();
            s.activate("no_sprint");
            s.state.getPersonalActiveChallenges().put(owner.getUuid(), new ArrayList<>(List.of(ID, ID)));
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            String choice = BlockedDirectionChallenge.reconcile(server).id();
            assertSync(context, s.channel(owner), List.of("no_sprint", ID), choice);
            assertSync(context, s.channel(other), List.of("no_sprint"), "");
            s.state.getActiveChallengeIds().add(ID);
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertSync(context, s.channel(owner), List.of("no_sprint", ID), choice);
            assertSync(context, s.channel(other), List.of("no_sprint", ID), choice);
            var challenge = ChallengeRegistry.get(ID);
            try {
                challenge.setEnabled(false);
                CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
                assertSync(context, s.channel(owner), List.of("no_sprint"), "");
                assertSync(context, s.channel(other), List.of("no_sprint"), "");
                context.assertFalse(s.state.getChallengeData().contains(ID), "Disabled choice cleared");
            } finally { challenge.setEnabled(true); }
            s.state.getActiveChallengeIds().remove(ID);
            s.state.getPersonalActiveChallenges().clear();
            s.state.getChallengeData().put(ID, savedChoice);
            CycleManager.updateSchedule(server, CurseCycleMod.getConfig());
            context.assertFalse(s.state.getChallengeData().contains(ID), "Stale loaded choice reconciled without onStop");
        }
        context.complete();
    }

    private static void assertSync(TestContext context, EmbeddedChannel channel, List<String> ids, String direction) {
        channel.runPendingTasks(); channel.flush();
        CurseCycleSyncPayload last = null;
        Object packet;
        while ((packet = channel.readOutbound()) != null)
            if (packet instanceof CustomPayloadS2CPacket custom && custom.payload() instanceof CurseCycleSyncPayload sync) last = sync;
        context.assertTrue(last != null, "Real S2C packet reaches recipient");
        context.assertEquals(ids, last.activeChallengeIds(), "Effective enabled recipient IDs");
        context.assertEquals(direction, last.blockedDirection(), "Authoritative choice");
        var buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            CurseCycleSyncPayload.CODEC.encode(buffer, last);
            var decoded = CurseCycleSyncPayload.CODEC.decode(buffer);
            context.assertEquals(ids, decoded.activeChallengeIds(), "IDs survive codec");
            context.assertEquals(direction, decoded.blockedDirection(), "Choice survives codec");
            context.assertEquals(0, buffer.readableBytes(), "Whole packet decoded");
        } finally { buffer.release(); }
    }
}
