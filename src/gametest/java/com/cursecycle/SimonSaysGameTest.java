package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.SimonSaysChallenge;
import com.cursecycle.challenge.impl.SimonSaysChallenge.Action;
import com.cursecycle.state.CurseCycleState;
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
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
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
    private static List<GameMessageS2CPacket> packets(EmbeddedChannel channel) {
        channel.runPendingTasks(); channel.flush();
        var result = new ArrayList<GameMessageS2CPacket>(); Object packet;
        while ((packet = channel.readOutbound()) != null)
            if (packet instanceof GameMessageS2CPacket overlay && overlay.overlay()) result.add(overlay);
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
    public void vanillaHudContainsCommandCountdownSuccessFailureAndClear(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); packets(s.channel(p)); command(p, Action.CROUCH);
            var messages = packets(s.channel(p));
            c.assertTrue(messages.stream().anyMatch(packet -> packet.content().getContent() instanceof TranslatableTextContent t
                    && t.getKey().equals("text.cursecycle.simon_says.command") && t.getArgs()[1].equals(5L)), "Initial command with five seconds: " + messages);
            deadline(p, 20); messages = packets(s.channel(p));
            c.assertTrue(messages.stream().anyMatch(packet -> packet.content().getContent() instanceof TranslatableTextContent t
                    && t.getKey().equals("text.cursecycle.simon_says.command") && t.getArgs()[1].equals(4L)), "Countdown is server-controlled");
            p.setSneaking(true); step(p);
            c.assertTrue(packets(s.channel(p)).stream().anyMatch(packet -> packet.content().getContent() instanceof TranslatableTextContent t
                    && t.getKey().equals("text.cursecycle.simon_says.success")), "Success feedback");
            command(p, Action.CROUCH); deadline(p, 101);
            c.assertTrue(packets(s.channel(p)).stream().anyMatch(packet -> packet.content().getContent() instanceof TranslatableTextContent t
                    && t.getKey().equals("text.cursecycle.simon_says.failure")), "Failure feedback");
            command(p, Action.CROUCH); packets(s.channel(p));
            s.activate("no_sprint");
            c.assertTrue(packets(s.channel(p)).stream().anyMatch(packet -> packet.content().getString().isEmpty()), "End clears pending HUD");
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
