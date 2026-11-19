package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.BloodTributeChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.*;

public class BloodTributeGameTest implements FabricGameTest {
    private static final String ID = BloodTributeChallenge.ID;
    private static NbtCompound record(ServerPlayerEntity p) {
        return CurseCycleState.getServerState(p.getServer()).getChallengeData().getCompound(ID).getCompound(p.getUuidAsString());
    }
    /** Synchronous boundary tests emulate separate ticks by clearing only the nonpersistent duplicate guard. */
    private static void step(ServerPlayerEntity p) {
        BloodTributeChallenge.clear(p.getServer()); BloodTributeChallenge.tick(p.getServer());
    }
    private static void debt(ServerPlayerEntity p, int remaining, int penalty) {
        record(p).putInt("remaining", remaining); record(p).putInt("penaltyRemaining", penalty);
    }
    private static void ready(ServerPlayerEntity p, TestContext c) {
        for (int i = 0; i < 61; i++) p.tick();
        var pos = c.getAbsolutePos(new BlockPos(2, 3, 2));
        p.setPosition(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        p.setNoGravity(true); p.setOnGround(true); p.setHealth(20); p.timeUntilRegen = 0;
        p.getHungerManager().setFoodLevel(17); p.getHungerManager().setSaturationLevel(0);
        // onPlayerConnect fires JOIN before this fixture switches the default GameTest mode to Survival.
        ChallengeRegistry.get(ID).onStart(p.getServer(), p);
    }
    private static List<TranslatableTextContent> messages(ChallengeScenario s, ServerPlayerEntity p) {
        s.channel(p).runPendingTasks(); Object packet;
        var result = new ArrayList<TranslatableTextContent>();
        while ((packet = s.channel(p).readOutbound()) != null)
            if (packet instanceof GameMessageS2CPacket m && !m.overlay()
                    && m.content().getContent() instanceof TranslatableTextContent t) result.add(t);
        return result;
    }
    private static long count(List<TranslatableTextContent> list, String suffix) {
        return list.stream().filter(t -> t.getKey().equals("text.cursecycle.blood_tribute." + suffix)).count();
    }
    private static final class Arena implements AutoCloseable {
        final TestContext c;
        final ChallengeScenario s;
        final Set<UUID> before = new HashSet<>();
        final List<Entity> entities = new ArrayList<>();
        final ServerPlayerEntity p;
        final boolean pvp;
        Arena(TestContext c) {
            this.c = c; s = new ChallengeScenario(c);
            pvp = c.getWorld().getServer().isPvpEnabled(); c.getWorld().getServer().setPvpEnabled(true);
            c.getWorld().iterateEntities().forEach(e -> before.add(e.getUuid()));
            p = s.player(); ready(p, c); s.activate(ID);
        }
        <T extends MobEntity> T mob(EntityType<T> type) {
            var mob = type.create(c.getWorld());
            mob.setAiDisabled(true); mob.setNoGravity(true);
            mob.setPosition(p.getX(), p.getY(), p.getZ() + 3); mob.setHealth(.25F);
            c.assertTrue(c.getWorld().spawnEntity(mob), "Fixture spawned " + type);
            entities.add(mob); return mob;
        }
        void kill(MobEntity mob, DamageSource source) {
            c.assertTrue(mob.damage(source, 100), "Lethal hit accepted");
            c.assertFalse(mob.isAlive(), "AFTER_DEATH really fired");
        }
        @Override public void close() {
            entities.forEach(Entity::discard);
            var cleanup = new ArrayList<Entity>();
            c.getWorld().iterateEntities().forEach(e -> {
                if (!before.contains(e.getUuid()) && !(e instanceof ServerPlayerEntity)) cleanup.add(e);
            });
            cleanup.forEach(Entity::discard); s.close(); BloodTributeChallenge.clear(p.getServer());
            c.getWorld().getServer().setPvpEnabled(pvp);
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tribute_melee")
    public void personalFinalBlowPeacefulMobAndTotem(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.p; var other = a.s.player(); ready(other, c);
            debt(p, 7, 0); debt(other, 8, 0);
            var cow = a.mob(EntityType.COW); cow.setHealth(10);
            p.attack(cow);
            c.assertTrue(cow.isAlive() && cow.getHealth() < 10, "Actual nonlethal player attack");
            c.assertEquals(7, record(p).getInt("remaining"), "Damage alone is insufficient");
            cow.timeUntilRegen = 0;
            a.kill(cow, p.getDamageSources().playerAttack(other));
            c.assertEquals(7, record(p).getInt("remaining"), "Earlier attacker does not get kill credit");
            c.assertEquals(3600, record(other).getInt("remaining"), "Final blow belongs to other player");
            cow = a.mob(EntityType.COW); cow.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            cow.damage(p.getDamageSources().playerAttack(p), 100);
            c.assertTrue(cow.isAlive() && cow.getOffHandStack().isEmpty(), "Totem prevented real death");
            c.assertEquals(7, record(p).getInt("remaining"), "Totem never satisfies obligation");
            cow.timeUntilRegen = 0; cow.clearStatusEffects(); cow.setAbsorptionAmount(0); cow.setHealth(.01F); p.attack(cow);
            c.assertFalse(cow.isAlive(), "Real melee killed peaceful mob");
            c.assertEquals(3600, record(p).getInt("remaining"), "Peaceful mob counts");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tribute_projectiles")
    public void actualArrowAndTridentCountForTheirPlayer(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.p; var other = a.s.player(); ready(other, c); other.setPosition(p.getPos().add(8, 0, 0));
            debt(p, 3, 0); debt(other, 4, 0);
            var cow = a.mob(EntityType.COW);
            var arrow = new ArrowEntity(c.getWorld(), p, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            a.entities.add(arrow); arrow.setPosition(cow.getX(), cow.getY() + .5, cow.getZ() - 1);
            arrow.setVelocity(0, 0, 2); c.getWorld().spawnEntity(arrow); arrow.tick();
            c.assertFalse(cow.isAlive(), "Actual owned arrow impact killed mob");
            c.assertEquals(3600, record(p).getInt("remaining"), "Arrow owner gets interval");
            c.assertEquals(4, record(other).getInt("remaining"), "Other player unchanged");
            debt(p, 2, 0); cow = a.mob(EntityType.COW);
            var trident = new TridentEntity(c.getWorld(), p, new ItemStack(Items.TRIDENT));
            a.entities.add(trident); trident.setPosition(cow.getX(), cow.getY() + .5, cow.getZ() - 1);
            trident.setVelocity(0, 0, 2); c.getWorld().spawnEntity(trident); trident.tick();
            c.assertFalse(cow.isAlive(), "Actual trident impact killed mob");
            c.assertEquals(3600, record(p).getInt("remaining"), "Trident owner gets interval");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tribute_indirect")
    public void oldCreditPetsTrapsExplosionsAndNonMobsDoNotCount(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.p; debt(p, 9, 0); var wolf = a.mob(EntityType.WOLF);
            wolf.setOwner(p); wolf.setTamed(true, false);
            for (var source : List.of(p.getDamageSources().onFire(), p.getDamageSources().lava(),
                    p.getDamageSources().fall(), p.getDamageSources().thorns(p),
                    p.getDamageSources().explosion(p, p), p.getDamageSources().mobAttack(wolf))) {
                var cow = a.mob(EntityType.COW); cow.setHealth(10); cow.setAttacker(p);
                a.kill(cow, source);
                c.assertEquals(9, record(p).getInt("remaining"), "Indirect death rejected: " + source.getName());
            }
            var stand = EntityType.ARMOR_STAND.create(c.getWorld()); a.entities.add(stand);
            BloodTributeChallenge.died(stand, p.getDamageSources().playerAttack(p));
            var other = a.s.player(); ready(other, c);
            other.damage(p.getDamageSources().playerAttack(p), 100);
            c.assertFalse(other.isAlive(), "PvP death control");
            c.assertEquals(9, record(p).getInt("remaining"), "Players and stands are not mobs");
            var cow = a.mob(EntityType.COW); cow.discard();
            c.assertEquals(9, record(p).getInt("remaining"), "Discard is not death");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tribute_boundaries")
    public void warningDeadlineCadenceDedupAndKillStopsPenalty(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.p; messages(a.s, p); debt(p, 601, 0); step(p);
            var packets = messages(a.s, p);
            c.assertEquals(1L, count(packets, "warning"), "One pre-deadline warning");
            c.assertTrue(packets.stream().anyMatch(t -> t.getKey().endsWith(".warning") && t.getArgs()[0].equals(30)), "Thirty seconds left");
            step(p); c.assertEquals(0L, count(messages(a.s, p), "warning"), "Warning not spammed");
            debt(p, 1, 0); step(p);
            c.assertEquals(19F, p.getHealth(), "First half-heart at deadline");
            c.assertEquals(40, record(p).getInt("penaltyRemaining"), "Next penalty in two seconds");
            c.assertEquals(1L, count(messages(a.s, p), "overdue"), "Overdue feedback once");
            BloodTributeChallenge.tick(p.getServer());
            c.assertEquals(40, record(p).getInt("penaltyRemaining"), "Repeated same-tick call does no work");
            for (int i = 0; i < 39; i++) step(p);
            c.assertEquals(19F, p.getHealth(), "No second penalty before forty ticks"); step(p);
            c.assertEquals(18F, p.getHealth(), "Exactly one penalty at next boundary");
            a.kill(a.mob(EntityType.COW), p.getDamageSources().playerAttack(p));
            c.assertEquals(3600, record(p).getInt("remaining"), "Kill clears debt immediately");
            for (int i = 0; i < 80; i++) step(p);
            c.assertEquals(18F, p.getHealth(), "No further penalty or healing after payment");
            c.assertFalse(record(p).getBoolean("warned"), "New interval can warn again");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tribute_defences")
    public void penaltyBypassesDefencesButRespectsAbsorptionAndTotem(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.p;
            p.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE)); p.playerTick();
            c.assertTrue(p.getArmor() > 0, "Real armor attributes applied");
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 500, 4));
            p.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD)); p.setCurrentHand(Hand.OFF_HAND);
            for (int i = 0; i < 6; i++) p.playerTick();
            c.assertTrue(p.isBlocking(), "Real blocking shield");
            p.timeUntilRegen = 20; debt(p, 0, 0); step(p);
            c.assertEquals(19F, p.getHealth(), "Armor, resistance, shield and cooldown do not cancel penalty");
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 500, 0)); p.setAbsorptionAmount(2);
            debt(p, 0, 0); step(p); c.assertEquals(1F, p.getAbsorptionAmount(), "Absorption pays first");
            c.assertEquals(19F, p.getHealth(), "Absorbed penalty preserves HP");
            p.stopUsingItem(); p.setAbsorptionAmount(0); p.setHealth(1);
            p.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING)); debt(p, 0, 0); step(p);
            c.assertTrue(p.isAlive() && p.getOffHandStack().isEmpty(), "Penalty can consume real totem");
            c.assertEquals(0, record(p).getInt("remaining"), "Totem does not forgive debt");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tribute_reconnect")
    public void reconnectAndOfflineTimePreserveDeadlineAndDebt(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.p; var uuid = p.getUuid(); debt(p, 123, 0); record(p).putBoolean("warned", true);
            ChallengeRegistry.get(ID).onPlayerLeave(p.getServer(), p); p.getServer().getPlayerManager().remove(p);
            for (int i = 0; i < 100; i++) step(p);
            c.assertEquals(123, record(p).getInt("remaining"), "Offline deadline paused");
            var joined = a.s.player(uuid); ready(joined, c);
            c.assertEquals(123, record(joined).getInt("remaining"), "JOIN gives no new interval");
            step(joined); c.assertEquals(122, record(joined).getInt("remaining"), "Countdown resumes");
            debt(joined, 0, 7);
            ChallengeRegistry.get(ID).onPlayerLeave(joined.getServer(), joined); joined.getServer().getPlayerManager().remove(joined);
            for (int i = 0; i < 100; i++) step(joined);
            var again = a.s.player(uuid); ready(again, c);
            c.assertEquals(0, record(again).getInt("remaining"), "Overdue survives reconnect");
            c.assertEquals(7, record(again).getInt("penaltyRemaining"), "Offline time creates no missed damage queue");
            for (int i = 0; i < 7; i++) step(again);
            c.assertEquals(19F, again.getHealth(), "Exactly one penalty after saved seven active ticks");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tribute_nbt")
    public void stateNbtReloadPreservesWarningAndPenaltyPhase(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.p; debt(p, 123, 0); record(p).putBoolean("warned", true);
            reload(c, p); c.assertEquals(123, record(p).getInt("remaining"), "State round-trip preserves remainder");
            c.assertTrue(record(p).getBoolean("warned"), "Warned flag persisted");
            debt(p, 0, 3); record(p).putBoolean("overdue", true); reload(c, p);
            for (int i = 0; i < 2; i++) step(p);
            c.assertEquals(20F, p.getHealth(), "Reload does not accelerate penalty"); step(p);
            c.assertEquals(19F, p.getHealth(), "Reload gives no fresh grace");
            c.assertEquals(40, record(p).getInt("penaltyRemaining"), "Phase persisted and advanced");
            c.assertTrue(CurseCycleState.getServerState(p.getServer()).isDirty(), "Updates mark persistent state dirty");
        } c.complete();
    }
    private static void reload(TestContext c, ServerPlayerEntity p) {
        var state = CurseCycleState.getServerState(p.getServer());
        var saved = state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
        c.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY,
                CurseCycleState.createFromNbt(saved, c.getWorld().getRegistryManager()));
        BloodTributeChallenge.clear(p.getServer());
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tribute_lifecycle")
    public void deathRespawnDimensionAndExcludedModesPreserveDebt(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.p; debt(p, 0, 0); p.setHealth(1); step(p);
            c.assertFalse(p.isAlive(), "Penalty caused real player death");
            for (int i = 0; i < 10; i++) step(p);
            c.assertEquals(40, record(p).getInt("penaltyRemaining"), "Dead time paused");
            var next = a.s.respawn(p, false); ready(next, c); step(next);
            c.assertEquals(0, record(next).getInt("remaining"), "Respawn does not forgive debt");
            c.assertEquals(39, record(next).getInt("penaltyRemaining"), "Respawn resumes saved phase");
            for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
                next.changeGameMode(mode); step(next);
                c.assertEquals(39, record(next).getInt("penaltyRemaining"), "Excluded mode pauses debt");
            }
            next.changeGameMode(GameMode.ADVENTURE); step(next);
            c.assertEquals(38, record(next).getInt("penaltyRemaining"), "Adventure participates");
            var world = next.getServer().getWorld(World.NETHER);
            next.teleport(world, 0, 100, 0, 0, 0); step(next);
            c.assertEquals(37, record(next).getInt("penaltyRemaining"), "Dimension change keeps saved phase");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tribute_cleanup")
    public void stopDisableResetAndNewActivationClearOnlyOwnState(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.p; debt(p, 0, 0); step(p); a.s.state.getChallengeData().putInt("fixture", 42);
            ChallengeRegistry.get(ID).onStop(p.getServer(), p);
            c.assertFalse(a.s.state.getChallengeData().getCompound(ID).contains(p.getUuidAsString()), "Stop removes own player record");
            c.assertEquals(42, a.s.state.getChallengeData().getInt("fixture"), "Other data retained");
            a.s.activate("test_easy"); step(p); c.assertEquals(19F, p.getHealth(), "End never heals old damage");
            a.s.activate(ID); debt(p, 0, 0); step(p);
            var challenge = ChallengeRegistry.get(ID); challenge.setEnabled(false);
            try { step(p); c.assertFalse(a.s.state.getChallengeData().contains(ID), "Disabled even without onStop clears debt"); }
            finally { challenge.setEnabled(true); }
            a.s.activate(ID); c.assertEquals(3600, record(p).getInt("remaining"), "Repeated ID has fresh activation");
            a.s.state.reset(); step(p); c.assertFalse(a.s.state.getChallengeData().contains(ID), "Reset ends obligation");
            c.assertEquals(18F, p.getHealth(), "Reset never restores spent HP");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tribute_pairs")
    public void effectiveIdsDeduplicateAndPairsUseExistingDamagePath(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.p; var other = a.s.player(); ready(other, c);
            a.s.state.getActiveChallengeIds().clear(); a.s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID);
            debt(p, 2, 0); step(p);
            c.assertEquals(1, record(p).getInt("remaining"), "Personal-only countdown");
            c.assertFalse(a.s.state.getChallengeData().getCompound(ID).contains(other.getUuidAsString()), "Unassigned online player removed");
            a.s.activate("double_challenge:blood_tribute,pacifist");
            a.s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID); debt(p, 2, 0); step(p);
            c.assertEquals(1, record(p).getInt("remaining"), "GLOBAL plus PERSONAL processed once");
            var cow = a.mob(EntityType.COW); a.kill(cow, p.getDamageSources().playerAttack(p));
            c.assertEquals(3600, record(p).getInt("remaining"), "Pacifist pair still pays tribute");
            c.assertEquals(19.75F, p.getHealth(), "Pacifist actual return still applies");
            a.s.state.getPersonalActiveChallengeIds(p.getUuid()).clear();
            a.s.activate("double_challenge:blood_tribute,damage_slot_drop");
            p.getInventory().setStack(0, new ItemStack(Items.STONE, 3)); debt(p, 0, 0); step(p);
            c.assertTrue(p.getInventory().isEmpty(), "Penalty uses shared confirmed damage reaction");
            a.s.activate("double_challenge:blood_tribute,no_healing"); debt(p, 0, 0); step(p);
            float hp = p.getHealth(); a.kill(a.mob(EntityType.COW), p.getDamageSources().playerAttack(p));
            c.assertEquals(hp, p.getHealth(), "Paying debt does not heal, including NoHealing pair");
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tribute_real_clock", tickLimit = 3680)
    public void realThreeMinuteClockWarningAndTwoSecondPenalty(TestContext c) {
        var a = new Arena(c); var p = a.p; int began = p.getServer().getTicks();
        messages(a.s, p); long daylight = c.getWorld().getTimeOfDay();
        c.runAtTick(2900, () -> {
            c.getWorld().setTimeOfDay(c.getWorld().getTimeOfDay() + 600);
            c.assertEquals(3600 - (p.getServer().getTicks() - began), record(p).getInt("remaining"), "Exact active server ticks despite daylight jump");
            c.assertFalse(record(p).getBoolean("warned"), "No warning before last thirty seconds");
        });
        c.runAtTick(3010, () -> {
            c.assertEquals(1L, count(messages(a.s, p), "warning"), "Actual tick event sends warning");
            c.assertEquals(20F, p.getHealth(), "Warning does no damage");
        });
        c.runAtTick(3590, () -> c.assertEquals(20F, p.getHealth(), "No damage before three minutes"));
        c.runAtTick(3605, () -> c.assertEquals(19F, p.getHealth(), "First penalty after full real interval"));
        c.runAtTick(3645, () -> {
            try {
                c.assertEquals(18F, p.getHealth(), "Second penalty exactly two seconds later");
                c.assertEquals(1L, count(messages(a.s, p), "overdue"), "One overdue message");
                c.complete();
            } finally { a.close(); c.getWorld().setTimeOfDay(daylight); }
        });
    }
}
