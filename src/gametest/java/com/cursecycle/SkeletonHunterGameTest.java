package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.HunterMark;
import com.cursecycle.challenge.impl.NetheriteMobSpawn;
import com.cursecycle.challenge.impl.NetheriteMobsChallenge;
import com.cursecycle.challenge.impl.SkeletonHunterChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

import java.util.*;

/** Real gameplay hooks, equipment, safe relocation and persistence without changing the shared clock. */
public class SkeletonHunterGameTest implements FabricGameTest {
    private static final String ID = SkeletonHunterChallenge.ID;
    private static final int FLAGS = Block.FORCE_STATE | Block.NOTIFY_LISTENERS;
    private static Object field(Object value, String name) {
        try { var f = value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void set(Object value, String name, Object data) {
        try { var f = value.getClass().getDeclaredField(name); f.setAccessible(true); f.set(value, data); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static NbtCompound record(ServerPlayerEntity player) {
        return CurseCycleState.getServerState(player.getServer()).getChallengeData().getCompound(ID)
                .getCompound(player.getUuidAsString());
    }
    private static Object pursuit(ServerPlayerEntity player) {
        try {
            var f = SkeletonHunterChallenge.class.getDeclaredField("RUNTIMES"); f.setAccessible(true);
            return ((Map<?, ?>) field(((Map<?, ?>) f.get(null)).get(player.getServer()), "pursuits")).get(player.getUuid());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static long now(ServerPlayerEntity p) { return p.getServer().getOverworld().getTime(); }
    private static SkeletonEntity hunter(ServerPlayerEntity player) {
        var rec = record(player);
        if (!rec.containsUuid("hunter")) return null;
        for (var world : player.getServer().getWorlds()) {
            if (world.getEntity(rec.getUuid("hunter")) instanceof SkeletonEntity skeleton) return skeleton;
        }
        return null;
    }
    private static Vec3d arena(TestContext c, ServerWorld world) {
        var center = c.getAbsolutePos(BlockPos.ORIGIN);
        int cx = center.getX(), cz = center.getZ(), y = 210;
        // Fixture alone loads chunks. Production queries must leave unloaded areas untouched.
        for (int x = (cx - 25) >> 4; x <= (cx + 25) >> 4; x++)
            for (int z = (cz - 25) >> 4; z <= (cz + 25) >> 4; z++) world.getChunk(x, z);
        for (var pos : BlockPos.iterate(cx - 24, y - 1, cz - 24, cx + 24, y + 3, cz + 24))
            world.setBlockState(pos, pos.getY() == y - 1 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), FLAGS);
        return new Vec3d(cx + .5, y, cz + .5);
    }
    private static ServerPlayerEntity ready(TestContext c, ChallengeScenario s) {
        var player = s.player();
        var pos = arena(c, c.getWorld());
        player.setPosition(pos); player.setNoGravity(true);
        for (int i = 0; i < 61; i++) player.tick();
        s.activate(ID);
        return player;
    }
    private static void check(ServerPlayerEntity player) { SkeletonHunterChallenge.check(player); }
    private static List<Object> packets(ChallengeScenario s, ServerPlayerEntity p) {
        s.channel(p).runPendingTasks(); s.channel(p).flush();
        var result = new ArrayList<Object>(); Object packet;
        while ((packet = s.channel(p).readOutbound()) != null) result.add(packet);
        return result;
    }
    private static boolean warning(Object packet) {
        return packet instanceof GameMessageS2CPacket message
                && message.content().getContent() instanceof net.minecraft.text.TranslatableTextContent text
                && text.getKey().equals("text.cursecycle.skeleton_hunter.warning");
    }
    private static long teleportSounds(List<Object> packets) {
        return packets.stream().filter(packet -> packet instanceof PlaySoundS2CPacket sound
                && sound.getSound().value().getId().equals(SoundEvents.ENTITY_ENDERMAN_TELEPORT.getId())).count();
    }
    private static void assertKit(TestContext c, SkeletonEntity mob) {
        c.assertTrue(mob.getMainHandStack().isOf(Items.BOW), "Bow equipped");
        var registry = c.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        c.assertEquals(SkeletonHunterChallenge.BOW_POWER, EnchantmentHelper.getLevel(
                registry.getEntry(Enchantments.POWER).orElseThrow(), mob.getMainHandStack()), "Power level");
        c.assertEquals(SkeletonHunterChallenge.BOW_PUNCH, EnchantmentHelper.getLevel(
                registry.getEntry(Enchantments.PUNCH).orElseThrow(), mob.getMainHandStack()), "Punch level");
        c.assertEquals(SkeletonHunterChallenge.BOW_FLAME, EnchantmentHelper.getLevel(
                registry.getEntry(Enchantments.FLAME).orElseThrow(), mob.getMainHandStack()), "Flame level");
        c.assertEquals(SkeletonHunterChallenge.BOW_UNBREAKING, EnchantmentHelper.getLevel(
                registry.getEntry(Enchantments.UNBREAKING).orElseThrow(), mob.getMainHandStack()), "Unbreaking level");
        c.assertTrue(mob.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.DIAMOND_HELMET), "Diamond helmet");
        c.assertTrue(mob.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.DIAMOND_CHESTPLATE), "Diamond chestplate");
        c.assertTrue(mob.getEquippedStack(EquipmentSlot.LEGS).isOf(Items.DIAMOND_LEGGINGS), "Diamond leggings");
        c.assertTrue(mob.getEquippedStack(EquipmentSlot.FEET).isOf(Items.DIAMOND_BOOTS), "Diamond boots");
    }
    private static void cleanup(ChallengeScenario s) {
        s.activate("test_easy");
        for (var world : s.context.getWorld().getServer().getWorlds())
            world.getEntitiesByType(EntityType.ARROW, e -> e.getY() > 200).forEach(Entity::discard);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunter_ownership")
    public void onePerEligibleOwnerAndPersonalAssignments(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(c, s); var q = s.player(); q.setPosition(p.getPos());
            c.assertTrue(hunter(p) != null, "Initial appearance is immediate");
            c.assertTrue(packets(s, p).stream().noneMatch(SkeletonHunterGameTest::warning), "No initial warning packet");
            check(p); check(q);
            var a = hunter(p); var b = hunter(q);
            c.assertTrue(a != null && b != null && a != b, "Distinct hunter per GLOBAL player");
            c.assertEquals(SkeletonHunterChallenge.MAX_HEALTH, a.getHealth(), "Configured health");
            assertKit(c, a);
            c.assertTrue(a.getMainHandStack().isOf(Items.BOW), "Vanilla bow");
            c.assertTrue(a.isPersistent() && !a.canPickUpLoot(), "Persistent; no equipment pickup");
            for (int i = 0; i < 20; i++) { SkeletonHunterChallenge.check(p); SkeletonHunterChallenge.check(q); }
            c.assertTrue(a == hunter(p) && b == hunter(q), "Repeated checks do not duplicate");
            s.activate("test_easy");
            c.assertTrue(a.isRemoved() && b.isRemoved(), "Stop removes both loaded hunters");
            s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID);
            check(p); SkeletonHunterChallenge.check(q);
            c.assertTrue(hunter(p) != null && hunter(q) == null, "PERSONAL only affects assigned owner");
            ChallengeRegistry.get(ID).onStop(p.getServer(), p);
            s.state.getPersonalActiveChallengeIds(p.getUuid()).clear();
            p.changeGameMode(GameMode.CREATIVE); s.activate(ID); SkeletonHunterChallenge.check(p);
            c.assertTrue(hunter(p) == null, "Creative excluded");
            p.changeGameMode(GameMode.SPECTATOR); SkeletonHunterChallenge.check(p);
            c.assertTrue(hunter(p) == null, "Spectator excluded");
            cleanup(s);
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunter_ai")
    public void actualNavigationBowTargetRestrictionAndDaylight(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(c, s); check(p); var mob = hunter(p);
            var q = s.player(); q.setPosition(p.getPos().add(4, 0, 0));
            mob.setPosition(p.getPos().add(22, 0, 0));
            mob.setTarget(p); mob.setTarget(q); mob.setTarget(null);
            c.assertTrue(mob.getTarget() == p, "Vanilla targets cannot steal or clear owner");
            double before = mob.squaredDistanceTo(p);
            for (int i = 0; i < 100; i++) c.getWorld().tickEntity(mob);
            c.assertTrue(mob.squaredDistanceTo(p) < before - 16, "Real vanilla navigation approaches owner beyond bow range");
            mob.setPosition(p.getPos().add(10, 0, 0)); mob.setOnGround(true);

            for (int i = 0; i < 120; i++) c.getWorld().tickEntity(mob);
            var arrows = c.getWorld().getEntitiesByType(EntityType.ARROW, e -> e.getOwner() == mob);
            c.assertFalse(arrows.isEmpty(), "Real bow AI fires owned arrows");
            long daylight = c.getWorld().getTimeOfDay();
            c.getWorld().setTimeOfDay(6000); c.getWorld().calculateAmbientDarkness();
            mob.equipStack(EquipmentSlot.HEAD, net.minecraft.item.ItemStack.EMPTY);
            for (int i = 0; i < 60; i++) mob.tickMovement();
            c.assertFalse(mob.isOnFire(), "Owned hunter does not burn in daylight without helmet");
            c.getWorld().setTimeOfDay(daylight); c.getWorld().calculateAmbientDarkness();
            p.changeGameMode(GameMode.CREATIVE); c.getWorld().tickEntity(mob);
            c.assertTrue(mob.getTarget() == null && mob.getNavigation().isIdle(), "Ineligible owner suspends AI");
            var ordinary = EntityType.SKELETON.create(c.getWorld());
            ordinary.setTarget(q); c.assertTrue(ordinary.getTarget() == q, "Ordinary skeleton targets unchanged");
            cleanup(s);
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunter_death")
    public void playerKillImmediatelyReplacesOnceWithNoEquipmentDrops(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(c, s); var mob = hunter(p); var id = mob.getUuid();
            var existingDrops = new HashSet<>(c.getWorld().getEntitiesByType(EntityType.ITEM, e -> true));
            for (var slot : EquipmentSlot.values()) mob.setEquipmentDropChance(slot, 2);
            packets(s, p);
            mob.damage(c.getWorld().getDamageSources().playerAttack(p), Float.MAX_VALUE);
            c.assertFalse(mob.isAlive(), "Real player kill");
            c.assertTrue(c.getWorld().getEntitiesByType(EntityType.ITEM,
                    e -> !existingDrops.contains(e) && (e.getStack().isOf(Items.BOW)
                            || e.getStack().isOf(Items.DIAMOND_HELMET) || e.getStack().isOf(Items.DIAMOND_CHESTPLATE)
                            || e.getStack().isOf(Items.DIAMOND_LEGGINGS) || e.getStack().isOf(Items.DIAMOND_BOOTS)))
                    .isEmpty(), "Bow and every armor piece never drop even with guaranteed drop chance");
            var replacement = hunter(p);
            c.assertTrue(replacement != null && !replacement.getUuid().equals(id), "Replacement exists before damage callback returns");
            assertKit(c, replacement);
            c.assertFalse(record(p).contains("respawnAt"), "No saved death delay");
            SkeletonHunterChallenge.died(mob); check(p); check(p);
            c.assertTrue(replacement == hunter(p), "Repeated death/check callbacks do not duplicate");
            var received = packets(s, p);
            c.assertTrue(received.stream().noneMatch(SkeletonHunterGameTest::warning), "No replacement warning");
            c.assertEquals(1L, teleportSounds(received), "One actual arrival sound packet");
            cleanup(s);
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunter_relocation")
    public void instantSoundedRelocationHasStrictRadiusAndRequiresSafePoint(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(c, s); var mob = hunter(p); var id = mob.getUuid();
            mob.setHealth(11); mob.getMainHandStack().setDamage(7);
            mob.getEquippedStack(EquipmentSlot.CHEST).setDamage(17);
            mob.setPosition(p.getPos().add(SkeletonHunterChallenge.TELEPORT_DISTANCE, 0, 0)); check(p);
            c.assertTrue(mob == hunter(p), "No teleport at exact distance threshold");
            packets(s, p);
            mob.setPosition(p.getPos().add(SkeletonHunterChallenge.TELEPORT_DISTANCE + .01, 0, 0)); check(p);
            var moved = hunter(p);
            c.assertTrue(moved != mob && mob.isRemoved(), "Teleports on first check beyond threshold");
            c.assertEquals(id, moved.getUuid(), "Same logical entity UUID");
            c.assertEquals(11F, moved.getHealth(), "Relocation preserves health");
            c.assertEquals(7, moved.getMainHandStack().getDamage(), "Relocation preserves bow wear");
            c.assertEquals(17, moved.getEquippedStack(EquipmentSlot.CHEST).getDamage(), "Relocation preserves armor wear");
            c.assertTrue(moved.distanceTo(p) >= SkeletonHunterChallenge.TELEPORT_MIN_RADIUS
                    && moved.distanceTo(p) <= 16, "Whole 3D destination is within sixteen blocks");
            assertKit(c, moved);
            var received = packets(s, p);
            c.assertEquals(1L, teleportSounds(received), "Actual teleport sound sent to nearby owner");
            c.assertTrue(received.stream().noneMatch(SkeletonHunterGameTest::warning), "No teleport warning packet");
            moved.setPosition(p.getPos().add(20, 0, 0)); check(p);
            set(pursuit(p), "anchor", moved.getPos());
            set(pursuit(p), "progress", now(p) - SkeletonHunterChallenge.STUCK_TICKS);
            check(p); c.assertTrue(hunter(p) != moved, "Stuck transfer also has no warning delay");
            var latest = hunter(p); latest.setPosition(p.getPos().add(0, 60, 0));
            var center = BlockPos.ofFloored(p.getPos());
            for (var pos : BlockPos.iterate(center.add(-24, -5, -24), center.add(24, 6, 24)))
                c.getWorld().setBlockState(pos, Blocks.STONE.getDefaultState(), FLAGS);
            packets(s, p); check(p);
            c.assertTrue(hunter(p) == latest && !latest.isRemoved(), "No unsafe fallback through walls");
            c.assertEquals(0L, teleportSounds(packets(s, p)), "No sound for failed teleport");
            arena(c, c.getWorld()); check(p);
            c.assertTrue(hunter(p) != latest, "Retry succeeds once a safe point exists");
            cleanup(s);
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunter_unloaded")
    public void unloadedSnapshotTransfersSameHunterAndRejectsLateDiskCopy(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(c, s); check(p); var old = hunter(p); old.setHealth(9);
            // Real entity-manager UNLOAD callback captures NBT, not a call to the death handler.
            var disk = old.writeNbt(new NbtCompound()); disk.putString("id", "minecraft:skeleton");
            var uuid = old.getUuid();
            old.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            c.assertTrue(hunter(p) == null && record(p).containsUuid("hunter"), "Missing loaded UUID remains assigned");
            c.assertFalse(record(p).contains("respawnAt"), "Unload does not start death delay");
            check(p); var restored = hunter(p);
            c.assertTrue(restored != null, "Unloaded hunter returns on first check");
            c.assertEquals(uuid, restored.getUuid(), "Restored entity keeps UUID");
            c.assertEquals(9F, restored.getHealth(), "Unload callback captured latest health");
            var other = p.getServer().getWorld(World.NETHER); arena(c, other);
            var stale = (SkeletonEntity) EntityType.loadEntityWithPassengers(disk, other, e -> e);
            stale.setPosition(p.getPos()); other.tryLoadEntity(stale);
            c.assertTrue(stale.isRemoved(), "Stale token rejected on real load in another world before AI");
            c.assertTrue(restored == hunter(p), "Authoritative copy survives");
            cleanup(s);
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunter_persistence")
    public void worldStateAndEntityNbtReloadWithoutDuplicationOrDelays(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(c, s); check(p); var old = hunter(p); old.setHealth(13);
            var uuid = old.getUuid();
            var disk = old.writeNbt(new NbtCompound()); disk.putString("id", "minecraft:skeleton");
            old.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var state = s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
            var loaded = CurseCycleState.createFromNbt(state, c.getWorld().getRegistryManager());
            c.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY, loaded);
            var entity = (SkeletonEntity) EntityType.loadEntityWithPassengers(disk, c.getWorld(), e -> e);
            c.assertTrue(c.getWorld().tryLoadEntity(entity), "Vanilla chunk-load entry point");
            c.assertFalse(entity.isRemoved(), "Matching marker accepted after state read");
            SkeletonHunterChallenge.check(p); c.assertTrue(hunter(p) == entity, "No duplicate after reload");
            c.assertEquals(uuid, hunter(p).getUuid(), "Persistent entity UUID");
            entity.setPosition(p.getPos().add(0, 60, 0));
            var again = CurseCycleState.createFromNbt(loaded.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager()), c.getWorld().getRegistryManager());
            c.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY, again);
            SkeletonHunterChallenge.check(p);
            c.assertTrue(hunter(p) != entity && entity.isRemoved(), "State reload still permits immediate relocation");
            c.assertEquals(uuid, hunter(p).getUuid(), "Reload keeps hunter identity");
            c.assertEquals(13F, hunter(p).getHealth(), "Reload keeps hunter health");
            cleanup(s);
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunter_lifecycle")
    public void dimensionsRespawnDisconnectDisableResetAndLateLoadCleanup(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(c, s); check(p); var original = hunter(p); var id = original.getUuid();
            var other = p.getServer().getWorld(World.END); var pos = arena(c, other);
            p.teleport(other, pos.x, pos.y, pos.z, 0, 0); SkeletonHunterChallenge.check(p);
            c.assertTrue(original.isRemoved(), "Dimension change follows on first check");
            c.assertTrue(hunter(p).getWorld() == other && hunter(p).getUuid().equals(id), "Same hunter follows dimensions");
            var transferred = hunter(p);
            var next = s.respawn(p, false); next.setPosition(pos); next.setNoGravity(true);
            SkeletonHunterChallenge.check(next);
            c.assertTrue(hunter(next) != null && hunter(next).getUuid().equals(id), "Respawn keeps same hunter UUID");
            transferred = hunter(next);
            SkeletonHunterChallenge.forget(next);
            var manager = next.getServer().getPlayerManager(); manager.remove(next);
            c.assertTrue(SkeletonHunterChallenge.target(transferred) == null, "Offline owner suspends pursuit");
            var joined = s.player(idForOwner(transferred)); joined.setPosition(pos); joined.setNoGravity(true);
            SkeletonHunterChallenge.check(joined);
            c.assertTrue(hunter(joined) == transferred, "Reconnection retains saved owner assignment");
            ChallengeRegistry.get(ID).setEnabled(false);
            ChallengeRegistry.get(ID).onStop(joined.getServer(), joined);
            c.assertTrue(transferred.isRemoved(), "Explicit stop also removes a disabled challenge's mob");
            ChallengeRegistry.get(ID).setEnabled(true); s.activate(ID); check(joined);
            var beforeReset = hunter(joined); var disk = beforeReset.writeNbt(new NbtCompound());
            disk.putString("id", "minecraft:skeleton"); beforeReset.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            CurseCycleState.getServerState(joined.getServer()).reset();
            var stale = EntityType.loadEntityWithPassengers(disk, other, e -> e); other.tryLoadEntity(stale);
            c.assertTrue(stale.isRemoved(), "Reset invalidates unloaded marker");
            cleanup(s);
        } finally { ChallengeRegistry.get(ID).setEnabled(true); }
        c.complete();
    }
    private static UUID idForOwner(SkeletonEntity mob) { return ((HunterMark) mob).cursecycle$hunterOwner(); }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunter_netherite")
    public void netheritePairKeepsIndependentMarkersAndWornArmorOnTransfer(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(c, s); s.activate("double_challenge:skeleton_hunter,netherite_mobs");
            check(p); var mob = hunter(p);
            ((NetheriteMobSpawn) mob).cursecycle$finishBirth();
            var chest = mob.getEquippedStack(EquipmentSlot.CHEST);
            c.assertTrue(NetheriteMobsChallenge.isIssued(chest), "Shared birth pipeline equips the new hunter");
            chest.setDamage(17); mob.setHealth(12); mob.setPosition(p.getPos().add(0, 60, 0)); check(p);
            var moved = hunter(p);
            c.assertTrue(NetheriteMobsChallenge.isIssued(moved.getEquippedStack(EquipmentSlot.CHEST)), "Armor origin marker survives transfer");
            ((NetheriteMobSpawn) moved).cursecycle$finishBirth();
            c.assertEquals(17, moved.getEquippedStack(EquipmentSlot.CHEST).getDamage(), "Relocation never grants a fresh kit");
            c.assertEquals(12F, moved.getHealth(), "Pair preserves health");
            cleanup(s);
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunter_cleanup", tickLimit = 70)
    public void automaticDisableAndResetCleanupWithoutLifecycleDispatch(TestContext c) {
        var s = new ChallengeScenario(c); var p = ready(c, s); check(p); var disabled = hunter(p);
        var ordinary = EntityType.SKELETON.create(c.getWorld()); ordinary.setPosition(p.getPos());
        ordinary.setAiDisabled(true); ordinary.setPersistent(); c.getWorld().spawnEntity(ordinary);
        ChallengeRegistry.get(ID).setEnabled(false);
        var resetHunter = new SkeletonEntity[1];
        c.runAtTick(25, () -> {
            try {
                c.assertTrue(disabled.isRemoved() && !ordinary.isRemoved(), "Periodic disabled cleanup affects owned mob only");
                ChallengeRegistry.get(ID).setEnabled(true); s.activate(ID); check(p);
                resetHunter[0] = hunter(p); s.state.reset();
            } catch (RuntimeException e) {
                ChallengeRegistry.get(ID).setEnabled(true); ordinary.discard(); cleanup(s); s.close(); throw e;
            }
        });
        c.runAtTick(50, () -> {
            try {
                c.assertTrue(resetHunter[0].isRemoved() && !ordinary.isRemoved(), "Periodic reset cleanup without onStop");
                c.complete();
            } finally { ordinary.discard(); cleanup(s); s.close(); }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunter_peaceful")
    public void peacefulPausesOwnedSkeletonWithoutLosingItsAssignment(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(c, s); check(p); var mob = hunter(p); var difficulty = c.getWorld().getDifficulty();
            try {
                p.getServer().setDifficulty(Difficulty.PEACEFUL, true);
                mob.checkDespawn(); c.getWorld().tickEntity(mob); SkeletonHunterChallenge.check(p);
                c.assertFalse(mob.isRemoved(), "Peaceful does not silently delete the owned hunter");
                c.assertTrue(mob.getTarget() == null && mob.getNavigation().isIdle(), "Peaceful suspends AI");
                c.assertTrue(hunter(p) == mob && !record(p).contains("respawnAt"), "No death delay or replacement");
                var ordinary = EntityType.SKELETON.create(c.getWorld()); ordinary.checkDespawn();
                c.assertTrue(ordinary.isRemoved(), "Ordinary peaceful despawn preserved");
            } finally { p.getServer().setDifficulty(difficulty, true); }
            cleanup(s);
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunter_legacy")
    public void oldDeathDeadlineAndOldEquipmentUpgradeWithoutHealing(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(c, s); var mob = hunter(p);
            mob.setHealth(9); mob.equipStack(EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(Items.BOW));
            for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
                mob.equipStack(slot, net.minecraft.item.ItemStack.EMPTY);
            record(p).remove("armed"); check(p);
            c.assertEquals(9F, mob.getHealth(), "Old hunter upgrade does not heal"); assertKit(c, mob);
            mob.getEquippedStack(EquipmentSlot.CHEST).setDamage(23); check(p);
            c.assertEquals(23, mob.getEquippedStack(EquipmentSlot.CHEST).getDamage(), "Upgrade happens once, no free repairs");
            mob.discard(); record(p).remove("hunter"); record(p).remove("token");
            record(p).remove("snapshot"); record(p).remove("armed");
            record(p).putLong("respawnAt", now(p) + 3600);
            check(p);
            c.assertTrue(hunter(p) != null, "Former three-minute deadline is ignored");
            c.assertFalse(record(p).contains("respawnAt"), "Legacy deadline removed from saved state");
            cleanup(s);
        } c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hunter_real_clock", tickLimit = 80)
    public void realServerTickRelocatesWithoutWarningDelay(TestContext c) {
        var s = new ChallengeScenario(c); var p = ready(c, s); var first = hunter(p);
        c.assertTrue(first != null, "Activation spawns immediately");
        first.setAiDisabled(true);
        var replacement = new SkeletonEntity[1];
        c.runAtTick(1, () -> {
            first.damage(c.getWorld().getDamageSources().genericKill(), Float.MAX_VALUE);
            replacement[0] = hunter(p);
            c.assertTrue(replacement[0] != null && replacement[0] != first, "Death replaced in same real server tick");
            replacement[0].setAiDisabled(true);
            replacement[0].setPosition(p.getPos().add(0, 60, 0));
        });
        c.runAtTick(25, () -> {
            try {
                var moved = hunter(p);
                c.assertTrue(moved != null && moved != replacement[0], "Periodic server check relocates within twenty ticks");
                c.assertEquals(replacement[0].getUuid(), moved.getUuid(), "Periodic relocation preserves UUID");
                c.assertTrue(moved.distanceTo(p) <= 16, "Actual tick relocation is within sixteen blocks");
                c.assertTrue(packets(s, p).stream().noneMatch(SkeletonHunterGameTest::warning), "No delayed warning on real clock");
                c.complete();
            } finally { cleanup(s); s.close(); }
        });
    }
}
