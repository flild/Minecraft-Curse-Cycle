package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.SimonSaysChallenge;
import com.cursecycle.challenge.impl.SimonSaysChallenge.Action;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.network.SimonSaysStatePayload;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.*;

/** Short tests shift private deadlines, never the shared server clock. One test uses real server ticks. */
public class SimonSaysGameTest implements FabricGameTest {
    private static final String ID = SimonSaysChallenge.ID;
    private static Object get(Object object, String name) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void set(Object object, String name, Object value) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Object session(ServerPlayerEntity p) {
        try {
            var f = SimonSaysChallenge.class.getDeclaredField("SESSIONS"); f.setAccessible(true);
            var map = (Map<?, ?>) ((Map<?, ?>) f.get(null)).get(p.getServer());
            return map == null ? null : map.get(p.getUuid());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static ServerPlayerEntity ready(ChallengeScenario s) {
        var p = s.player();
        for (int i = 0; i < 61; i++) p.tick();
        p.setHealth(20); p.getHungerManager().setFoodLevel(18); p.timeUntilRegen = 0;
        p.setNoGravity(true); p.setPosition(p.getX(), 200, p.getZ()); p.setOnGround(true);
        p.getInventory().setStack(0, new ItemStack(Items.STONE));
        p.getInventory().setStack(1, new ItemStack(Items.DIRT));
        s.activate(ID);
        return p;
    }
    private static long age(ServerPlayerEntity p) {
        return Integer.toUnsignedLong(p.getServer().getTicks() - (Integer) get(session(p), "start"));
    }
    private static Object command(ServerPlayerEntity p, Action action) {
        var s = session(p);
        set(s, "action", null); set(s, "next", age(p));
        set(s, "lastTick", p.getServer().getTicks() - 1);
        SimonSaysChallenge.tick(p.getServer()); // Real selection, snapshot, scheduling and HUD delivery.
        if (get(s, "action") == null) throw new AssertionError("Command not issued");
        set(s, "action", action); // Deterministic coverage of each possible randomly chosen action.
        SimonSaysChallenge.sync(p);
        return s;
    }
    private static void step(ServerPlayerEntity p) {
        var s = session(p);
        set(s, "issued", (Long) get(s, "issued") - 1);
        set(s, "lastTick", p.getServer().getTicks() - 1);
        SimonSaysChallenge.tick(p.getServer());
    }
    private static void deadline(ServerPlayerEntity p, int elapsed) {
        set(session(p), "issued", age(p) - elapsed + 1);
        step(p);
    }
    private static void pending(TestContext c, Object s, boolean expected) {
        c.assertEquals(expected, get(s, "action") != null, "Pending command");
    }
    private static List<SimonSaysStatePayload> packets(EmbeddedChannel channel) {
        channel.runPendingTasks(); channel.flush();
        var result = new ArrayList<SimonSaysStatePayload>(); Object packet;
        while ((packet = channel.readOutbound()) != null)
            if (packet instanceof CustomPayloadS2CPacket custom && custom.payload() instanceof SimonSaysStatePayload state)
                result.add(state);
        return result;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_jump", tickLimit = 40)
    public void successfulJumpOnlyAfterCommandAndWithinInclusiveDeadline(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s);
            p.jump(); var phase = command(p, Action.JUMP); step(p); pending(c, phase, true);
            p.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH).setBaseValue(0);
            p.jump(); pending(c, phase, true);
            p.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH).setBaseValue(.42);
            deadline(p, 100); p.jump(); pending(c, phase, false);
            c.assertEquals(20F, p.getHealth(), "Jump on last allowed tick succeeds");
            phase = command(p, Action.JUMP); deadline(p, 101); p.jump();
            c.assertEquals(16F, p.getHealth(), "Late jump does not undo penalty");
            for (int i = 0; i < 5; i++) step(p);
            c.assertEquals(16F, p.getHealth(), "Exactly one penalty");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_crouch", tickLimit = 40)
    public void heldCrouchMustBeReleasedAndNewVanillaInputSucceeds(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); p.setSneaking(true); var phase = command(p, Action.CROUCH);
            step(p); pending(c, phase, true);
            p.networkHandler.onClientCommand(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
            step(p); pending(c, phase, true);
            p.networkHandler.onClientCommand(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
            deadline(p, 100); pending(c, phase, false);
            deadline(p, 101); c.assertEquals(20F, p.getHealth(), "No penalty after success");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_stop", tickLimit = 40)
    public void stopRequiresHalfSecondAfterIssueAndResetsOnMovement(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); var phase = command(p, Action.STOP);
            for (int i = 0; i < 9; i++) step(p);
            pending(c, phase, true);
            p.setPosition(p.getX() + .1, p.getY(), p.getZ()); step(p);
            for (int i = 0; i < 9; i++) step(p);
            pending(c, phase, true); step(p); pending(c, phase, false);
            c.assertEquals(20F, p.getHealth(), "Standing still succeeds without damage");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_swap", tickLimit = 40)
    public void swapRequiresDifferentSlotAndItemNotConsumptionOrShuffle(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s);
            p.getInventory().setStack(2, new ItemStack(Items.STONE));
            var phase = command(p, Action.SWAP);
            p.getInventory().getStack(0).decrement(1); step(p); pending(c, phase, true);
            p.getInventory().setStack(0, new ItemStack(Items.STONE));
            p.networkHandler.onUpdateSelectedSlot(new UpdateSelectedSlotC2SPacket(2));
            step(p); pending(c, phase, true);
            p.networkHandler.onUpdateSelectedSlot(new UpdateSelectedSlotC2SPacket(1));
            step(p); pending(c, phase, false);
            c.assertEquals(20F, p.getHealth(), "Server-observed item change succeeds");
            p.getInventory().clear();
            c.assertFalse(SimonSaysChallenge.availableActions(p).contains(Action.SWAP), "Empty hotbar cannot swap item");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_damage", tickLimit = 40)
    public void penaltyBypassesDefencesAndCooldownButUsesAbsorption(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); command(p, Action.CROUCH);
            p.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            p.playerTick();
            c.assertTrue(p.getArmor() > 0, "Armor control has actual equipped attributes");
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 4));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0));
            p.timeUntilRegen = 20; p.setAbsorptionAmount(2);
            c.assertEquals(2F, p.getAbsorptionAmount(), "Absorption control is present before damage");
            deadline(p, 100); c.assertEquals(20F, p.getHealth(), "Not expired at exact deadline");
            deadline(p, 101);
            c.assertEquals(18F, p.getHealth(), "Four damage after defences and absorption");
            c.assertEquals(0F, p.getAbsorptionAmount(), "Absorption consumed normally");
            SimonSaysChallenge.tick(p.getServer()); step(p);
            c.assertEquals(18F, p.getHealth(), "Repeated dispatch cannot repeat penalty");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_pairs", tickLimit = 40)
    public void incompatibleCompanionsModesAndPersonalScope(TestContext c) {
        var challenge = ChallengeRegistry.get(ID);
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s);
            s.activate("double_challenge:simon_says,no_jumping");
            c.assertFalse(SimonSaysChallenge.availableActions(p).contains(Action.JUMP), "No forbidden jump");
            s.activate("double_challenge:simon_says,auto_jump");
            c.assertFalse(SimonSaysChallenge.availableActions(p).contains(Action.STOP), "No forced jump stop");
            s.activate("double_challenge:simon_says,adhd_no_standing_still");
            c.assertFalse(SimonSaysChallenge.availableActions(p).contains(Action.STOP), "No conflicting standing command");
            s.activate("no_sprint"); s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID);
            SimonSaysChallenge.sync(p); c.assertTrue(session(p) != null, "PERSONAL owner has session");
            var other = s.player(); SimonSaysChallenge.sync(other);
            c.assertTrue(session(other) == null, "Other player has no personal session");
            for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL)) {
                p.changeGameMode(mode); SimonSaysChallenge.tick(p.getServer());
                c.assertEquals(mode == GameMode.ADVENTURE || mode == GameMode.SURVIVAL,
                        session(p) != null, "Mode eligibility " + mode);
            }
            command(p, Action.CROUCH); challenge.setEnabled(false); SimonSaysChallenge.tick(p.getServer());
            c.assertTrue(session(p) == null, "Disabled sessions cleaned by unconditional tick");
        } finally { challenge.setEnabled(true); } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_lifecycle", tickLimit = 80)
    public void reconnectRespawnWorldReloadAndActivationRejectOldSession(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); var phase = command(p, Action.JUMP);
            SimonSaysChallenge.sync(p); c.assertTrue(session(p) == phase, "Normal sync preserves phase");
            s.state.beginActivation(); SimonSaysChallenge.jumped(p); pending(c, phase, true);
            SimonSaysChallenge.tick(p.getServer()); c.assertTrue(session(p) != phase, "Activation replaces phase");
            var uuid = p.getUuid(); var old = p; phase = command(p, Action.JUMP);
            p.getServer().getPlayerManager().remove(p); p = s.player(uuid);
            SimonSaysChallenge.sync(p); var fresh = session(p);
            SimonSaysChallenge.jumped(old); c.assertTrue(session(p) == fresh, "Old connection cannot complete new session");
            c.assertTrue(fresh != phase, "Reconnect gets a new interval");
            command(p, Action.CROUCH); p = s.respawn(p, false);
            c.assertTrue(session(p) != fresh, "Respawn replaces phase");
            phase = session(p); var dest = p.getServer().getWorld(World.NETHER);
            p.teleport(dest, 0, 200, 0, 0, 0); SimonSaysChallenge.sync(p);
            c.assertTrue(session(p) != phase, "World change replaces phase");
            phase = session(p);
            var manager = p.getServer().getOverworld().getPersistentStateManager();
            var loaded = CurseCycleState.createFromNbt(s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager()), c.getWorld().getRegistryManager());
            manager.set(CurseCycleState.STATE_KEY, loaded); SimonSaysChallenge.sync(p);
            c.assertTrue(session(p) != phase, "Loaded state identity invalidates old session even at same serial");
            com.cursecycle.state.CycleManager.activateChallenge(p.getServer(), loaded, "no_sprint");
            SimonSaysChallenge.tick(p.getServer());
            c.assertTrue(session(p) == null, "Ending challenge removes session");
            com.cursecycle.state.CycleManager.activateChallenge(p.getServer(), loaded, ID);
            SimonSaysChallenge.sync(p);
            c.assertTrue(session(p) != null, "Reactivation starts a fresh interval");
            p.getServer().getCommandManager().executeWithPrefix(p.getServer().getCommandSource(), "fdc reset");
            c.assertTrue(session(p) == null, "Reset immediately clears session");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_players", tickLimit = 40)
    public void globalPlayersHaveIndependentCommandsAndNoCatchUpPenalties(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); var other = s.player();
            for (int i = 0; i < 61; i++) other.tick();
            other.setHealth(20); other.getHungerManager().setFoodLevel(18);
            var first = command(p, Action.CROUCH); var second = command(other, Action.CROUCH);
            c.assertTrue(first != second, "GLOBAL players have separate sessions");
            p.setSneaking(true); step(p);
            pending(c, first, false); pending(c, second, true);
            deadline(other, 10000); pending(c, second, false);
            c.assertEquals(16F, other.getHealth(), "Large skipped window still imposes only one penalty");
            c.assertEquals(20F, p.getHealth(), "Another player's miss does not damage successful player");
            long next = (Long) get(second, "next");
            SimonSaysChallenge.sync(other); SimonSaysChallenge.sync(other);
            c.assertEquals(next, get(second, "next"), "Repeated sync cannot postpone next command");
            step(other); c.assertEquals(16F, other.getHealth(), "No old misses replayed");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_hud", tickLimit = 40)
    public void dedicatedHudContainsWaitingCommandCountdownResultsAndClear(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s);
            var waiting = packets(s.channel(p)).getLast();
            c.assertEquals(SimonSaysStatePayload.WAITING, waiting.phase(), "Timer before first command");
            c.assertTrue(waiting.remaining() >= 1200 && waiting.remaining() <= 1800, "Full initial interval");
            command(p, Action.CROUCH);
            var messages = packets(s.channel(p));
            var initial = messages.getLast();
            c.assertEquals(new SimonSaysStatePayload(SimonSaysStatePayload.COMMAND, "crouch", 100, 100), initial,
                    "Command and full five-second bar");
            var buf = new RegistryByteBuf(Unpooled.buffer(), p.getRegistryManager());
            try {
                SimonSaysStatePayload.CODEC.encode(buf, initial);
                c.assertEquals(initial, SimonSaysStatePayload.CODEC.decode(buf), "HUD codec roundtrip");
                c.assertEquals(0, buf.readableBytes(), "HUD codec fully consumed");
            } finally { buf.release(); }
            deadline(p, 5); messages = packets(s.channel(p));
            c.assertEquals(95, messages.getLast().remaining(), "Quarter-second server clock samples");
            SimonSaysChallenge.sync(p);
            c.assertEquals(95, packets(s.channel(p)).getLast().remaining(), "Sync cannot extend deadline");
            p.setSneaking(true); step(p);
            c.assertEquals(SimonSaysStatePayload.SUCCESS, packets(s.channel(p)).getLast().phase(), "Success feedback");
            set(session(p), "resultUntil", age(p)); SimonSaysChallenge.sync(p);
            c.assertEquals(SimonSaysStatePayload.WAITING, packets(s.channel(p)).getLast().phase(), "Result returns to next timer");
            command(p, Action.CROUCH); deadline(p, 101);
            c.assertEquals(SimonSaysStatePayload.FAILURE, packets(s.channel(p)).getLast().phase(), "Failure feedback");
            command(p, Action.CROUCH); packets(s.channel(p));
            s.activate("no_sprint");
            c.assertEquals(SimonSaysStatePayload.HIDDEN, packets(s.channel(p)).getLast().phase(), "End clears pending HUD");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_turn", tickLimit = 40)
    public void turnUsesRelativeDirectionAndWrapsYaw(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); p.setYaw(350); var phase = command(p, Action.TURN);
            p.setYaw(350 + 360); step(p); pending(c, phase, true);
            p.setYaw(350 + 149); step(p); pending(c, phase, true);
            p.setYaw(350 - 150); deadline(p, 100); pending(c, phase, false);
            c.assertEquals(20F, p.getHealth(), "Turn across zero succeeds at inclusive threshold");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_look", tickLimit = 40)
    public void lookCommandsRequireEnteringTargetAfterIssue(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); p.setPitch(-80); var phase = command(p, Action.LOOK_UP);
            step(p); pending(c, phase, true);
            p.setPitch(-59); step(p); pending(c, phase, true);
            p.setPitch(-60); step(p); pending(c, phase, false);
            p.setPitch(80); phase = command(p, Action.LOOK_DOWN);
            step(p); pending(c, phase, true);
            p.setPitch(59); step(p); pending(c, phase, true);
            p.setPitch(60); deadline(p, 100); pending(c, phase, false);
            phase = command(p, Action.LOOK_UP); p.setPitch(90); deadline(p, 101);
            pending(c, phase, false); c.assertEquals(16F, p.getHealth(), "Wrong direction does not succeed");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_swing", tickLimit = 40)
    public void vanillaSwingPacketOnlyCompletesItsCurrentCommand(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s);
            p.networkHandler.onHandSwing(new HandSwingC2SPacket(Hand.MAIN_HAND));
            var phase = command(p, Action.SWING); step(p); pending(c, phase, true);
            deadline(p, 100);
            p.networkHandler.onHandSwing(new HandSwingC2SPacket(Hand.OFF_HAND));
            pending(c, phase, false);
            phase = command(p, Action.CROUCH); step(p);
            p.networkHandler.onHandSwing(new HandSwingC2SPacket(Hand.MAIN_HAND));
            pending(c, phase, true);
            phase = command(p, Action.SWING); deadline(p, 101);
            p.networkHandler.onHandSwing(new HandSwingC2SPacket(Hand.MAIN_HAND));
            pending(c, phase, false); c.assertEquals(16F, p.getHealth(), "Late swing cannot undo penalty");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_double", tickLimit = 40)
    public void doubleActionsRequireTwoFreshTransitionsAndSeparateJumpTicks(TestContext c) {
        var s = new ChallengeScenario(c); var p = ready(s);
        try {
            p.setSneaking(true); var phase = command(p, Action.DOUBLE_CROUCH); step(p); pending(c, phase, true);
            p.setSneaking(false); step(p); p.setSneaking(true); step(p); pending(c, phase, true);
            p.setSneaking(false); step(p); p.setSneaking(true); step(p); pending(c, phase, false);
            p.setSneaking(false); phase = command(p, Action.DOUBLE_JUMP); step(p);
            p.jump(); p.jump(); pending(c, phase, true);
            c.runAtTick(2, () -> {
                try {
                    p.jump(); pending(c, session(p), false);
                    c.assertEquals(20F, p.getHealth(), "Second later jump succeeds without penalty");
                    c.complete();
                } finally { s.close(); }
            });
        } catch (Throwable error) { s.close(); throw error; }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_walk", tickLimit = 40)
    public void walkingCountsHorizontalStepsButNotTeleportOrVerticalMovement(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); var phase = command(p, Action.WALK);
            p.setPosition(p.getX() + 100, p.getY(), p.getZ()); step(p); pending(c, phase, true);
            p.setPosition(p.getX(), p.getY() + 10, p.getZ()); step(p); pending(c, phase, true);
            for (int i = 0; i < 5; i++) { p.setPosition(p.getX() + .5, p.getY(), p.getZ()); step(p); }
            pending(c, phase, true);
            p.setPosition(p.getX() + .5, p.getY(), p.getZ()); step(p); pending(c, phase, false);
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_sprint", tickLimit = 40)
    public void sprintNeedsNewStartAndMovementAndMustBePossible(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); p.setSprinting(true); var phase = command(p, Action.SPRINT);
            p.setPosition(p.getX() + .2, p.getY(), p.getZ()); step(p); pending(c, phase, true);
            p.setSprinting(false); step(p); p.setSprinting(true); step(p); pending(c, phase, true);
            p.setPosition(p.getX() + .2, p.getY(), p.getZ()); step(p); pending(c, phase, false);
            s.activate("double_challenge:simon_says,no_sprint");
            c.assertFalse(SimonSaysChallenge.availableActions(p).contains(Action.SPRINT), "Forbidden sprint is excluded");
            s.activate("double_challenge:simon_says,no_jumping");
            c.assertFalse(SimonSaysChallenge.availableActions(p).contains(Action.DOUBLE_JUMP), "Both jump tasks are excluded");
            s.activate(ID); p.getHungerManager().setFoodLevel(6);
            c.assertFalse(SimonSaysChallenge.availableActions(p).contains(Action.SPRINT), "Too hungry to sprint");
            c.assertEquals(13, Action.values().length, "Thirteen commands total");
        } c.complete();
    }

    private static void chat(ServerPlayerEntity p, String message) {
        p.getServer().getPlayerManager().broadcast(SignedMessage.ofUnsigned(p.getUuid(), message), p,
                MessageType.params(MessageType.CHAT, p));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_chat", tickLimit = 40)
    public void acceptedChatCompletesOnlySendersCurrentTaskWithinDeadline(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); var other = s.player();
            chat(p, "before task");
            var phase = command(p, Action.CHAT); step(p); pending(c, phase, true);
            SimonSaysChallenge.chatted(p, "  \t"); pending(c, phase, true);
            chat(other, "someone else"); pending(c, phase, true);
            p.getServer().getCommandManager().executeWithPrefix(p.getCommandSource(), "me command");
            pending(c, phase, true);
            deadline(p, 100); chat(p, "Привет!"); pending(c, phase, false);
            c.assertEquals(20F, p.getHealth(), "Accepted chat succeeds on last tick");
            phase = command(p, Action.CROUCH); step(p); chat(p, "wrong task"); pending(c, phase, true);
            phase = command(p, Action.CHAT); deadline(p, 101); chat(p, "too late");
            pending(c, phase, false); c.assertEquals(16F, p.getHealth(), "Late chat cannot undo penalty");
            phase = command(p, Action.CHAT); step(p); s.state.beginActivation();
            chat(p, "old activation"); pending(c, phase, true);
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "simon_real_clock", tickLimit = 1830)
    public void realServerIntervalAndTimeOfDayIndependence(TestContext c) {
        var s = new ChallengeScenario(c); var p = ready(s); var phase = session(p);
        long scheduled = (Long) get(phase, "next");
        c.assertTrue(scheduled >= 1200 && scheduled <= 1800, "First random delay lies in required bounds");
        c.runAtTick(1100, () -> {
            c.getWorld().setTimeOfDay(c.getWorld().getTimeOfDay() + 600);
            c.assertTrue(get(phase, "action") == null, "No command before 60 seconds despite time command");
        });
        c.runAtTick(1810, () -> {
            try {
                long issued = (Long) get(phase, "issued"), next = (Long) get(phase, "next");
                c.assertTrue(issued >= 1200 && issued <= 1800, "Real command starts within 60–90 server seconds");
                c.assertTrue(next - issued >= 1200 && next - issued <= 1800, "Next command interval measured from issue");
                c.assertTrue(session(p) == phase, "Periodic sync never resets live session");
                c.complete();
            } finally { s.close(); }
        });
    }
}
