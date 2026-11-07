package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.HeartbeatChallenge;
import com.cursecycle.network.HeartbeatPressPayload;
import com.cursecycle.network.HeartbeatStatePayload;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.*;

public class HeartbeatGameTest implements FabricGameTest {
    private static final String ID = HeartbeatChallenge.ID;
    private static final Map<UUID, List<Float>> REQUESTS = new HashMap<>();
    static {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (source.isOf(HeartbeatChallenge.DAMAGE_TYPE) && REQUESTS.containsKey(entity.getUuid()))
                REQUESTS.get(entity.getUuid()).add(amount);
            return true;
        });
    }
    private static List<HeartbeatStatePayload> packets(EmbeddedChannel channel) {
        channel.runPendingTasks(); channel.flush();
        var result = new ArrayList<HeartbeatStatePayload>(); Object packet;
        while ((packet = channel.readOutbound()) != null)
            if (packet instanceof CustomPayloadS2CPacket custom && custom.payload() instanceof HeartbeatStatePayload state)
                result.add(state);
        return result;
    }
    private static HeartbeatStatePayload sample(ChallengeScenario s, ServerPlayerEntity p) {
        return sample(s, p, s.channel(p));
    }
    private static HeartbeatStatePayload sample(ChallengeScenario s, ServerPlayerEntity p, EmbeddedChannel channel) {
        packets(channel); HeartbeatChallenge.sync(p);
        var found = packets(channel);
        s.context.assertFalse(found.isEmpty(), "Real S2C phase sample");
        var payload = found.getLast();
        var buf = new RegistryByteBuf(Unpooled.buffer(), p.getRegistryManager());
        try {
            HeartbeatStatePayload.CODEC.encode(buf, payload);
            s.context.assertEquals(payload, HeartbeatStatePayload.CODEC.decode(buf), "S2C codec roundtrip");
            s.context.assertEquals(0, buf.readableBytes(), "S2C fully consumed");
        } finally { buf.release(); }
        return payload;
    }
    private static void press(ChallengeScenario s, ServerPlayerEntity p, HeartbeatStatePayload phase) {
        var buf = new RegistryByteBuf(Unpooled.buffer(), p.getRegistryManager());
        try {
            HeartbeatPressPayload.CODEC.encode(buf, new HeartbeatPressPayload(phase.activation(), phase.session()));
            HeartbeatChallenge.receive(p, HeartbeatPressPayload.CODEC.decode(buf));
            s.context.assertEquals(0, buf.readableBytes(), "C2S fully consumed");
        } finally { buf.release(); }
    }
    private static ServerPlayerEntity ready(ChallengeScenario s) {
        var p = s.player();
        for (int i = 0; i < 61; i++) p.playerTick();
        p.setHealth(20); p.getHungerManager().setFoodLevel(18); p.timeUntilRegen = 0;
        p.setNoGravity(true); p.setPosition(p.getX(), 200, p.getZ()); p.fallDistance = 0;
        REQUESTS.put(p.getUuid(), new ArrayList<>());
        return p;
    }
    private static void health(TestContext c, float expected, ServerPlayerEntity p, String why) {
        c.assertTrue(Math.abs(expected - p.getHealth()) < .001F, why + " actual=" + p.getHealth());
    }
    private static void requests(TestContext c, ServerPlayerEntity p, int count) {
        var requests = REQUESTS.get(p.getUuid());
        c.assertEquals(count, requests.size(), "One request per missed window: " + p.getName().getString());
        c.assertTrue(requests.stream().allMatch(v -> v == 2F), "Every request is exactly two health units");
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "heartbeat_timing", tickLimit = 200)
    public void realServerWindowsDamageAndIndependentPhases(TestContext c) {
        var s = new ChallengeScenario(c); var server = c.getWorld().getServer();
        var lower = ready(s); var upper = ready(s); var early = ready(s);
        var armor = ready(s); var resistance = ready(s); var absorption = ready(s); var cooldown = ready(s); var totem = ready(s);
        armor.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE)); armor.playerTick();
        resistance.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 1000, 0));
        absorption.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1000, 0));
        absorption.setAbsorptionAmount(4);
        totem.setHealth(1); totem.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        s.state.getPersonalActiveChallenges().put(early.getUuid(), new ArrayList<>(List.of("damage_slot_drop")));
        early.getInventory().setStack(0, new ItemStack(Items.AMETHYST_BLOCK, 64));
        var dropped = new ArrayList<ItemEntity>();
        s.activate(ID);
        var one = sample(s, lower); var two = sample(s, upper); var three = sample(s, early);
        c.assertEquals(0L, one.age(), "Phase begins at activation tick");
        c.assertEquals(120L, one.target(), "100 preparation ticks plus first interval");
        var damage = lower.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(HeartbeatChallenge.DAMAGE_TYPE);
        c.assertTrue(damage.isIn(DamageTypeTags.BYPASSES_ARMOR), "Armor bypass tag");
        c.assertFalse(damage.isIn(DamageTypeTags.BYPASSES_RESISTANCE), "Resistance is vanilla");
        c.assertFalse(damage.isIn(DamageTypeTags.BYPASSES_COOLDOWN), "Damage immunity is vanilla");
        c.assertEquals(0F, damage.value().exhaustion(), "No additional hunger penalty");
        ServerPlayerEntity[] late = new ServerPlayerEntity[1];
        HeartbeatStatePayload[] latePhase = new HeartbeatStatePayload[1];
        int start = server.getTicks();
        for (int tick = 1; tick <= 147; tick++) {
            final int t = tick;
            c.waitAndRun(t, () -> {
                try {
                    c.assertEquals(t, server.getTicks() - start, "Actual server tick");
                    if (t == 10) { late[0] = ready(s); latePhase[0] = sample(s, late[0]); }
                    if (t == 114) press(s, early, three);
                    if (t == 115) { press(s, lower, one); press(s, lower, one); }
                    if (t == 125) {
                        press(s, upper, new HeartbeatStatePayload(two.activation() - 1, two.session(), 0, 120, false));
                        press(s, upper, one);
                        c.assertFalse(sample(s, upper).accepted(), "Old activation and another player's token rejected inside open window");
                        press(s, upper, two); press(s, late[0], latePhase[0]);
                        cooldown.damage(cooldown.getDamageSources().generic(), 2);
                        for (var p : List.of(lower, upper, early, armor, resistance, absorption, cooldown, totem)) requests(c, p, 0);
                        c.assertTrue(sample(s, lower).accepted(), "target-5 accepted");
                        c.assertTrue(sample(s, upper).accepted(), "target+5 accepted");
                        c.assertFalse(sample(s, early).accepted(), "target-6 ignored");
                    }
                    if (t == 126) press(s, early, three);
                    HeartbeatChallenge.tick(server); HeartbeatChallenge.tick(server);
                    if (t == 126) {
                        requests(c, lower, 0); requests(c, upper, 0); requests(c, early, 1);
                        health(c, 18, early, "First miss at target+6");
                        c.assertTrue(early.getInventory().getStack(0).isEmpty(), "Confirmed heartbeat damage triggers damage_slot_drop");
                        dropped.addAll(early.getServerWorld().getEntitiesByClass(ItemEntity.class, early.getBoundingBox().expand(3),
                                item -> item.getStack().isOf(Items.AMETHYST_BLOCK)));
                        c.assertEquals(1, dropped.size(), "One real dropped stack");
                        c.assertEquals(64, dropped.getFirst().getStack().getCount(), "Whole stack dropped once");
                        health(c, 18, armor, "Armor ignored");
                        health(c, 18.4F, resistance, "Resistance I applies");
                        health(c, 20, absorption, "Absorption before HP");
                        c.assertEquals(2F, absorption.getAbsorptionAmount(), "Only two absorption consumed");
                        health(c, 18, cooldown, "Recent equal damage suppresses heartbeat request");
                        health(c, 1, totem, "Totem prevents death");
                        c.assertTrue(totem.getMainHandStack().isEmpty(), "Vanilla totem consumed");
                        for (var p : List.of(armor, resistance, absorption, cooldown, totem)) requests(c, p, 1);
                    }
                    if (t == 135) press(s, early, three);
                    if (t == 136) { requests(c, late[0], 0); health(c, 20, late[0], "Late join has independent phase"); }
                    if (t == 146) {
                        requests(c, lower, 1); requests(c, upper, 1); requests(c, early, 1);
                        health(c, 18, lower, "One accepted beat does not cover next");
                        health(c, 18, upper, "Extra press cannot reserve next beat");
                        health(c, 18, early, "Next window accepted after early/late rejection");
                    }
                    if (t == 147) {
                        s.activate("no_sprint"); lower.setSprinting(true);
                        c.assertFalse(lower.isSprinting(), "Replacement challenge still enforces sprint restriction");
                        health(c, 18, lower, "Stopping heartbeat does not refund health");
                        c.assertTrue(early.getInventory().getStack(0).isEmpty(), "Stopping heartbeat does not restore items");
                        dropped.forEach(ItemEntity::discard); cleanup(s); c.complete();
                    }
                } catch (Throwable error) { dropped.forEach(ItemEntity::discard); cleanup(s); throw error; }
            });
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "heartbeat_lifecycle")
    public void tokensModesRespawnDimensionsPersistenceAndCommands(TestContext c) throws Exception {
        try (var s = new ChallengeScenario(c)) {
            var server = c.getWorld().getServer(); var p = ready(s); var other = ready(s);
            var firstChannel = s.channel(p);
            s.activate(ID); var phase = sample(s, p);
            ChallengeRegistry.get(ID).onStart(server, p);
            c.assertEquals(phase.session(), sample(s, p).session(), "Duplicate start retains preparation");
            for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
                p.changeGameMode(mode); HeartbeatChallenge.tick(server);
                c.assertTrue(packets(firstChannel).stream().anyMatch(v -> v.session() == 0), "Excluded mode clears immediately");
                press(s, p, phase); p.changeGameMode(GameMode.SURVIVAL);
                var fresh = sample(s, p); fresh(c, phase, fresh); phase = fresh;
            }
            p.changeGameMode(GameMode.ADVENTURE);
            c.assertEquals(phase.session(), sample(s, p).session(), "Survival to Adventure retains rhythm");
            p.setHealth(0); HeartbeatChallenge.tick(server);
            p = s.respawn(p, false);
            var respawn = sample(s, p, firstChannel); fresh(c, phase, respawn); phase = respawn;
            for (var dimension : List.of(World.NETHER, World.END, World.OVERWORLD)) {
                p.teleport(server.getWorld(dimension), .5, 200, .5, 0, 0); p.onTeleportationDone();
                var fresh = sample(s, p, firstChannel); fresh(c, phase, fresh); phase = fresh;
            }
            var uuid = other.getUuid(); var stale = sample(s, other);
            server.getPlayerManager().remove(other); other = s.player(uuid);
            var rejoined = sample(s, other); fresh(c, stale, rejoined);
            packets(s.channel(other)); press(s, other, stale);
            c.assertTrue(packets(s.channel(other)).isEmpty(), "Prior connection token rejected");
            s.activate("double_challenge:heartbeat,no_sprint");
            other.setSprinting(true); c.assertFalse(other.isSprinting(), "Companion restriction remains");
            var current = sample(s, other); fresh(c, rejoined, current);
            press(s, other, new HeartbeatStatePayload(current.activation() - 1, current.session(), 0, 120, false));
            c.assertFalse(sample(s, other).accepted(), "Old activation rejected");
            var persistence = server.getOverworld().getPersistentStateManager(); persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            var restored = CurseCycleState.createFromNbt(saved.getCompound("data"), c.getWorld().getRegistryManager());
            c.assertEquals(List.of(ID, "no_sprint"), restored.getActiveChallengeIds(), "Active IDs on real disk");
            c.assertFalse(restored.getChallengeData().contains(ID), "Phase not persisted");
            persistence.set(CurseCycleState.STATE_KEY, restored); HeartbeatChallenge.clear(server);
            fresh(c, current, sample(s, other));
            server.getPlayerManager().remove(other);
            CycleManager.activateChallenge(server, restored, "no_sprint"); persistence.save();
            other = s.player(uuid); HeartbeatChallenge.tick(server);
            c.assertTrue(packets(s.channel(other)).stream().noneMatch(v -> v.session() != 0), "Ended offline cannot restart phase");
            var source = server.getCommandSource().withLevel(2);
            for (var command : List.of("fdc list", "fdc activate heartbeat", "fdc active"))
                c.assertTrue(server.getCommandManager().getDispatcher().execute(command, source) > 0, "Command " + command);
            c.assertEquals(1L, ChallengeRegistry.getAll().stream().filter(ch -> ch.getId().equals(ID)).count(), "Registered exactly once");
            CycleManager.activateChallenge(server, restored, "no_sprint");
            restored.getPersonalActiveChallenges().put(other.getUuid(), new ArrayList<>(List.of(ID, ID)));
            var personal = sample(s, other); c.assertTrue(personal.session() != 0, "PERSONAL phase");
            restored.getActiveChallengeIds().add(ID);
            c.assertEquals(personal.session(), sample(s, other).session(), "GLOBAL/PERSONAL duplicate keeps one phase");
            var challenge = ChallengeRegistry.get(ID);
            try {
                challenge.setEnabled(false); HeartbeatChallenge.tick(server);
                c.assertTrue(packets(s.channel(other)).stream().anyMatch(v -> v.session() == 0), "Disabled ID releases phase");
            } finally { challenge.setEnabled(true); }
            fresh(c, personal, sample(s, other));
        } finally { HeartbeatChallenge.clear(c.getWorld().getServer()); REQUESTS.clear(); }
        c.waitAndRun(5, c::complete);
    }
    private static void fresh(TestContext c, HeartbeatStatePayload old, HeartbeatStatePayload fresh) {
        c.assertTrue(old.session() != fresh.session() && fresh.session() != 0, "New phase token");
        c.assertEquals(0L, fresh.age(), "Fresh preparation");
        c.assertEquals(120L, fresh.target(), "First target after preparation");
        c.assertFalse(fresh.accepted(), "No inherited accepted beat");
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "heartbeat_one_heart_review", tickLimit = 180)
    public void realMissWithOneHeartAndIndependentCleanup(TestContext c) {
        // Reuse the existing real-tick pair scenario rather than its historical result.
        new OneHeartGameTest().realMissedHeartbeatAndIndependentCleanup(c);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "heartbeat_repeated_review")
    public void repeatedActivationRejectsStaleTokensAndReleasesSessions(TestContext c) throws Exception {
        var server = c.getWorld().getServer();
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); var q = ready(s);
            long lastToken = 0;
            for (int i = 0; i < 32; i++) {
                s.activate("double_challenge:heartbeat,no_sprint");
                var phase = sample(s, p);
                c.assertTrue(phase.session() != lastToken, "Reactivation replaces previous token");
                lastToken = phase.session();
                var other = sample(s, q);
                p.setSprinting(true); q.setSprinting(true);
                c.assertFalse(p.isSprinting() || q.isSprinting(), "Both players retain companion restriction");
                for (int j = 0; j < 100; j++) press(s, p, phase);
                c.assertFalse(sample(s, p).accepted(), "Early packet flood cannot reserve future window");
                s.activate("no_sprint");
                packets(s.channel(p)); press(s, p, phase); press(s, q, other);
                HeartbeatChallenge.tick(server);
                c.assertTrue(packets(s.channel(p)).isEmpty(), "Ended phase cannot acknowledge stale presses");
                p.setSprinting(true); c.assertFalse(p.isSprinting(), "Ending heartbeat leaves no_sprint active");
                c.assertEquals(20F, p.getHealth(), "Start/stop and early presses never damage or heal");
                var field = HeartbeatChallenge.class.getDeclaredField("SESSIONS"); field.setAccessible(true);
                c.assertFalse(((Map<?, ?>) field.get(null)).containsKey(server), "All session/player/world references released");
            }
        } finally { HeartbeatChallenge.clear(server); REQUESTS.clear(); }
        c.complete();
    }
    private static void cleanup(ChallengeScenario s) {
        HeartbeatChallenge.clear(s.context.getWorld().getServer()); REQUESTS.clear(); s.close();
    }
}
