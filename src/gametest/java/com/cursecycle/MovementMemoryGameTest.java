package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.MovementMemoryChallenge;
import com.cursecycle.network.MovementMemoryPayload;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

public class MovementMemoryGameTest implements FabricGameTest {
    private static final String ID = MovementMemoryChallenge.ID;
    private static void request(ChallengeScenario s, ServerPlayerEntity p, long seq, boolean hold) {
        var packet = new MovementMemoryPayload(s.state.getActivationSerial(), seq, hold);
        var buffer = new RegistryByteBuf(Unpooled.buffer(), p.getRegistryManager());
        try {
            MovementMemoryPayload.CODEC.encode(buffer, packet);
            MovementMemoryChallenge.receive(p, MovementMemoryPayload.CODEC.decode(buffer));
            s.context.assertEquals(0, buffer.readableBytes(), "C2S request codec consumed");
        } finally { buffer.release(); }
    }
    private static List<MovementMemoryPayload> packets(EmbeddedChannel channel) {
        channel.runPendingTasks(); channel.flush();
        var found = new ArrayList<MovementMemoryPayload>(); Object packet;
        while ((packet = channel.readOutbound()) != null)
            if (packet instanceof CustomPayloadS2CPacket custom && custom.payload() instanceof MovementMemoryPayload memory) found.add(memory);
        return found;
    }
    private static void last(TestContext c, EmbeddedChannel channel, long seq, boolean hold) {
        var packets = packets(channel);
        c.assertFalse(packets.isEmpty(), "Real S2C response");
        c.assertEquals(seq, packets.getLast().sequence(), "Sequence");
        c.assertEquals(hold, packets.getLast().holding(), "Authoritative interval state");
        var buffer = new RegistryByteBuf(Unpooled.buffer(), c.getWorld().getRegistryManager());
        try {
            MovementMemoryPayload.CODEC.encode(buffer, packets.getLast());
            c.assertEquals(packets.getLast(), MovementMemoryPayload.CODEC.decode(buffer), "S2C codec round trip");
        } finally { buffer.release(); }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "movement_memory_timing", tickLimit = 80)
    public void exactServerIntervalAndIndependentPlayers(TestContext c) {
        var s = new ChallengeScenario(c); var server = c.getWorld().getServer();
        var first = s.player(); var second = s.player(); s.activate(ID);
        var one = s.channel(first); var two = s.channel(second);
        last(c, one, -1, false); last(c, two, -1, false);
        c.assertTrue(MovementMemoryChallenge.eligible(first), "Survival is eligible in normal air");
        request(s, first, 1, true); last(c, one, 1, true);
        int start = server.getTicks();
        c.waitAndRun(10, () -> {
            try {
                c.assertEquals(10, server.getTicks() - start, "Ten real server ticks");
                request(s, first, 1, true); // Duplicate cannot move the deadline.
                request(s, second, 1, true); last(c, two, 1, true);
            } catch (Throwable failure) { s.close(); throw failure; }
        });
        c.waitAndRun(19, () -> {
            try {
                c.assertEquals(19, server.getTicks() - start, "Nineteenth server tick");
                c.assertTrue(packets(one).stream().noneMatch(p -> p.sequence() == 1), "No early expiry or duplicate acknowledgement");
            } catch (Throwable failure) { s.close(); throw failure; }
        });
        // Callbacks run before END_SERVER_TICK; run the same idempotent handler at tick 20.
        c.waitAndRun(20, () -> {
            try {
                c.assertEquals(20, server.getTicks() - start, "Twentieth server tick");
                MovementMemoryChallenge.tick(server); last(c, one, 1, false);
                c.assertTrue(packets(two).stream().noneMatch(p -> p.sequence() == 1 && !p.holding()), "Other player still holds");
            } catch (Throwable failure) { s.close(); throw failure; }
        });
        c.waitAndRun(21, () -> {
            try {
                MovementMemoryChallenge.tick(server);
                c.assertTrue(packets(one).stream().noneMatch(p -> p.sequence() == 1), "Expiry sent exactly once");
                request(s, second, 2, false); last(c, two, 2, false);
                request(s, first, 2, true); last(c, one, 2, true);
                s.activate("double_challenge:movement_memory,no_sprint");
                first.setSprinting(true); c.assertFalse(first.isSprinting(), "no_sprint remains effective");
                packets(one);
                MovementMemoryChallenge.receive(first, new MovementMemoryPayload(s.state.getActivationSerial() - 1, 100, true));
                c.assertTrue(packets(one).isEmpty(), "Old activation rejected");
            } finally { s.close(); }
            c.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "movement_memory_lifecycle")
    public void lifecyclePersonalAndPersistence(TestContext c) throws Exception {
        try (var s = new ChallengeScenario(c)) {
            var server = c.getWorld().getServer(); var first = s.player(); var channel = s.channel(first);
            var other = s.player(); s.activate("no_sprint");
            s.state.getPersonalActiveChallenges().put(first.getUuid(), new ArrayList<>(List.of(ID, ID)));
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig()); last(c, channel, -1, false);
            c.assertTrue(packets(s.channel(other)).isEmpty(), "Personal context only reaches its owner");
            request(s, first, 1, true); last(c, channel, 1, true);
            request(s, other, 1, true); last(c, s.channel(other), 1, false);
            long sequence = 2;
            for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL)) {
                first.changeGameMode(mode); request(s, first, sequence, true);
                last(c, channel, sequence++, mode == GameMode.ADVENTURE || mode == GameMode.SURVIVAL);
            }
            request(s, first, 30, true); last(c, channel, 30, true);
            first.teleport(server.getWorld(World.NETHER), .5, 80, .5, 0, 0); first.onTeleportationDone();
            MovementMemoryChallenge.tick(server); last(c, channel, 30, false);
            first.teleport(c.getWorld(), .5, 80, .5, 0, 0); first.onTeleportationDone();
            request(s, first, 31, true); last(c, channel, 31, true);
            first.setHealth(0); MovementMemoryChallenge.tick(server); last(c, channel, 31, false);
            first = s.respawn(first, false);
            request(s, first, 32, true); last(c, channel, 32, true);
            var challenge = ChallengeRegistry.get(ID);
            try { challenge.setEnabled(false); MovementMemoryChallenge.tick(server); last(c, channel, 32, false); }
            finally { challenge.setEnabled(true); }
            var saved = s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
            var restored = CurseCycleState.createFromNbt(saved, c.getWorld().getRegistryManager());
            c.assertTrue(restored.getPersonalActiveChallengeIds(first.getUuid()).contains(ID), "Activity saved");
            c.assertFalse(restored.getChallengeData().contains(ID), "Direction/interval not saved");
            var uuid = first.getUuid(); server.getPlayerManager().remove(first);
            s.state.getPersonalActiveChallenges().clear(); s.activate("no_sprint");
            first = s.player(uuid); c.assertTrue(packets(s.channel(first)).isEmpty(), "Ended offline does not restore memory");
            var source = server.getCommandSource().withLevel(4);
            for (var command : List.of("fdc list", "fdc activate movement_memory", "fdc active"))
                c.assertTrue(server.getCommandManager().getDispatcher().execute(command, source) > 0, command);
            last(c, s.channel(first), -1, false);
        }
        c.waitAndRun(5, c::complete);
    }
}
