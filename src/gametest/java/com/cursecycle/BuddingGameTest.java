package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.*;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.*;

public class BuddingGameTest implements FabricGameTest {
    private static final String ID = BuddingChallenge.ID;

    /** Synchronous cases isolate activation and clean their entities, children and ordinary death drops. */
    private static final class Arena implements AutoCloseable {
        final TestContext c;
        final ChallengeScenario s;
        final List<Entity> entities = new ArrayList<>();
        final Map<ServerWorld, Set<UUID>> before = new IdentityHashMap<>();
        final List<Runnable> restoreTracking = new ArrayList<>();
        final long timeOfDay;
        Arena(TestContext c) {
            this.c = c;
            timeOfDay = c.getWorld().getTimeOfDay();
            s = new ChallengeScenario(c);
            for (var w : c.getWorld().getServer().getWorlds()) {
                var ids = new HashSet<UUID>();
                w.iterateEntities().forEach(e -> ids.add(e.getUuid()));
                before.put(w, ids);
            }
            s.activate(ID);
        }
        <T extends MobEntity> T spawn(EntityType<T> type) { return spawn(c.getWorld(), type); }
        <T extends MobEntity> T spawn(ServerWorld world, EntityType<T> type) {
            var originalPos = c.getAbsolutePos(new BlockPos(2, 3, 2));
            var pos = originalPos.withY(Math.max(world.getBottomY() + 3, originalPos.getY()));
            var chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // Fixture only.
            if (world != c.getWorld()) {
                // A loaded foreign chunk without players is not yet visible in the entity lookup.
                try {
                    var field = ServerWorld.class.getDeclaredField("entityManager"); field.setAccessible(true);
                    var manager = (net.minecraft.server.world.ServerEntityManager<?>) field.get(world);
                    var status = net.minecraft.world.entity.EntityTrackingStatus.fromLevelType(chunk.getLevelType());
                    manager.updateTrackingStatus(chunk.getPos(), net.minecraft.world.entity.EntityTrackingStatus.TRACKED);
                    restoreTracking.add(() -> manager.updateTrackingStatus(chunk.getPos(), status));
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
            var mob = type.create(world);
            mob.refreshPositionAndAngles(pos, 0, 0);
            mob.setAiDisabled(true);
            c.assertTrue(world.spawnEntity(mob), "Fixture spawned " + type);
            entities.add(mob);
            return mob;
        }
        List<? extends ZombieEntity> children(ServerWorld world) {
            return world.getEntitiesByType(EntityType.ZOMBIE,
                    e -> e.isAlive() && !before.get(world).contains(e.getUuid())
                            && ((BuddingMark) e).cursecycle$isBuddingChild());
        }
        List<? extends ZombieEntity> children() { return children(c.getWorld()); }
        void kill(LivingEntity mob) {
            // SquidEntity.damage can return false even after a fatal hit without an attacker.
            mob.damage(mob.getWorld().getDamageSources().genericKill(), Float.MAX_VALUE);
            c.assertFalse(mob.isAlive(), "Confirmed vanilla death: " + mob.getType());
        }
        void clearChildren() { children().forEach(Entity::discard); }
        @Override public void close() {
            entities.forEach(Entity::discard);
            for (var entry : before.entrySet()) {
                var cleanup = new ArrayList<Entity>();
                entry.getKey().iterateEntities().forEach(e -> {
                    if (!entry.getValue().contains(e.getUuid()) && !(e instanceof net.minecraft.server.network.ServerPlayerEntity))
                        cleanup.add(e);
                });
                cleanup.forEach(Entity::discard);
            }
            restoreTracking.forEach(Runnable::run);
            s.close();
            c.getWorld().setTimeOfDay(timeOfDay);
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "budding")
    public void confirmedDeathCreatesExactlyTwoOnce(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.s.player();
            var source = a.spawn(EntityType.SKELETON);
            c.assertTrue(source.damage(c.getWorld().getDamageSources().playerAttack(p), 1000), "Real player lethal hit");
            c.assertFalse(source.isAlive(), "Source died");
            c.assertEquals(2, a.children().size(), "Exactly two births from real AFTER_DEATH");
            for (var child : a.children()) {
                c.assertTrue(child.isBaby(), "Guaranteed baby age");
                c.assertEquals(source.getPos(), child.getPos(), "Death position");
                c.assertFalse(child.hasVehicle(), "No chicken jockey");
            }
            BuddingChallenge.died(source);
            source.onDeath(c.getWorld().getDamageSources().genericKill());
            c.assertEquals(2, a.children().size(), "Duplicate delivery/death does not multiply births");
            c.assertEquals(2L, a.children().stream().map(Entity::getUuid).distinct().count(), "Distinct UUIDs");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "budding")
    public void environmentDeathsAndOrdinaryBabyQualify(TestContext c) {
        try (var a = new Arena(c)) {
            for (var damage : List.of(c.getWorld().getDamageSources().fall(), c.getWorld().getDamageSources().onFire(),
                    c.getWorld().getDamageSources().lava())) {
                var source = a.spawn(EntityType.SKELETON);
                source.damage(damage, 1000);
                c.assertFalse(source.isAlive(), "Environment death");
                c.assertEquals(2, a.children().size(), "No player credit needed: " + damage.getName());
                a.clearChildren();
            }
            var ordinary = a.spawn(EntityType.ZOMBIE);
            ordinary.setBaby(true);
            c.assertFalse(((BuddingMark) ordinary).cursecycle$isBuddingChild(), "Age does not imply provenance");
            a.kill(ordinary);
            c.assertEquals(2, a.children().size(), "Natural baby still buds");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "budding")
    public void nonfatalDamageTotemAndDiscardDoNotBud(TestContext c) {
        try (var a = new Arena(c)) {
            var source = a.spawn(EntityType.COW);
            BuddingChallenge.died(source);
            source.damage(c.getWorld().getDamageSources().generic(), 1);
            c.assertEquals(0, a.children().size(), "Alive source does not trigger");
            source.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            source.timeUntilRegen = 0;
            source.damage(c.getWorld().getDamageSources().generic(), 1000);
            c.assertTrue(source.isAlive(), "Real totem prevents death");
            c.assertEquals(0, a.children().size(), "Totem does not bud");
            source.discard();
            c.assertEquals(0, a.children().size(), "Discard is not death");
            a.kill(a.spawn(EntityType.SKELETON));
            c.assertEquals(2, a.children().size(), "Lethal positive control");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "budding")
    public void peacefulAndHostileMobsQualifyButArmorStandsDoNot(TestContext c) {
        try (var a = new Arena(c)) {
            // All MobEntity subclasses qualify, regardless of spawn group or anger.
            for (var type : List.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
                    EntityType.WOLF, EntityType.CAT, EntityType.VILLAGER, EntityType.IRON_GOLEM,
                    EntityType.BAT, EntityType.COD, EntityType.SQUID, EntityType.AXOLOTL, EntityType.ALLAY,
                    EntityType.SLIME, EntityType.MAGMA_CUBE, EntityType.GHAST, EntityType.PHANTOM,
                    EntityType.SHULKER, EntityType.HOGLIN, EntityType.ZOMBIE, EntityType.CREEPER, EntityType.SPIDER,
                    EntityType.ENDERMAN, EntityType.PIGLIN, EntityType.ZOMBIFIED_PIGLIN,
                    EntityType.ZOGLIN, EntityType.WITHER)) {
                var source = a.spawn(type);
                a.kill(source);
                c.assertEquals(2, a.children().size(), "Two births for " + type);
                for (var child : a.children()) {
                    c.assertTrue(child.isBaby(), "Baby from " + type);
                    c.assertEquals(source.getPos(), child.getPos(), "Death position for " + type);
                }
                BuddingChallenge.died(source);
                c.assertEquals(2, a.children().size(), "No duplicate births for " + type);
                a.children().forEach(a::kill);
                c.assertEquals(0, a.children().size(), "Children from " + type + " cannot chain");
                source.discard();
            }
            var stand = EntityType.ARMOR_STAND.create(c.getWorld());
            stand.refreshPositionAndAngles(c.getAbsolutePos(new BlockPos(2, 3, 2)), 0, 0);
            a.entities.add(stand);
            c.assertTrue(c.getWorld().spawnEntity(stand), "Armor stand fixture spawned");
            a.kill(stand);
            c.assertEquals(0, a.children().size(), "Armor stand is not a mob");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "budding")
    public void childNbtStateReloadAndReactivationNeverChain(TestContext c) {
        try (var a = new Arena(c)) {
            a.kill(a.spawn(EntityType.SKELETON));
            var children = a.children();
            var saved = new NbtCompound();
            children.getFirst().saveNbt(saved);
            c.assertTrue(saved.getBoolean(BuddingChallenge.CHILD_NBT), "Persistent own NBT marker");
            var uuid = children.getFirst().getUuid();
            children.getFirst().discard();
            // Real entity deserialization and world load, in the same JVM.
            var loaded = (ZombieEntity) EntityType.loadEntityWithPassengers(saved, c.getWorld(), e -> e);
            a.entities.add(loaded);
            c.assertTrue(c.getWorld().tryLoadEntity(loaded), "Vanilla world load");
            c.assertEquals(uuid, loaded.getUuid(), "UUID survives");
            c.assertTrue(loaded.isBaby() && ((BuddingMark) loaded).cursecycle$isBuddingChild(), "Age and origin survive");
            a.s.activate("test_easy");
            c.assertTrue(loaded.isAlive() && children.get(1).isAlive(), "Ending keeps children");
            a.s.activate(ID);
            var stateNbt = a.s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
            c.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY,
                    CurseCycleState.createFromNbt(stateNbt, c.getWorld().getRegistryManager()));
            a.kill(loaded);
            a.kill(children.get(1));
            c.assertEquals(0, a.children().size(), "No chain across entity/state reload and new activation");
            var ordinary = a.spawn(EntityType.ZOMBIE);
            var normalNbt = new NbtCompound(); ordinary.saveNbt(normalNbt);
            c.assertFalse(normalNbt.contains(BuddingChallenge.CHILD_NBT), "Ordinary zombie never marked");
            ordinary.discard();
            var normalLoaded = (ZombieEntity) EntityType.loadEntityWithPassengers(normalNbt, c.getWorld(), e -> e);
            a.entities.add(normalLoaded);
            c.assertTrue(c.getWorld().tryLoadEntity(normalLoaded), "Ordinary world load");
            a.kill(normalLoaded);
            c.assertEquals(2, a.children().size(), "Unmarked loaded zombie positive control");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "budding")
    public void conversionRetainsOnlyChildOrigin(TestContext c) {
        try (var a = new Arena(c)) {
            a.kill(a.spawn(EntityType.SKELETON));
            var children = a.children();
            a.s.activate("test_easy");
            var converted = children.getFirst().convertTo(EntityType.DROWNED, true);
            a.entities.add(converted);
            c.assertTrue(converted != null && ((BuddingMark) converted).cursecycle$isBuddingChild(), "Conversion retains origin while inactive");
            a.s.activate(ID);
            a.kill(converted);
            a.kill(children.get(1));
            c.assertEquals(0, a.children().size(), "Converted child cannot chain");
            var ordinary = a.spawn(EntityType.ZOMBIE).convertTo(EntityType.DROWNED, true);
            a.entities.add(ordinary);
            c.assertFalse(((BuddingMark) ordinary).cursecycle$isBuddingChild(), "Ordinary conversion is unmarked");
            a.kill(ordinary);
            c.assertEquals(2, a.children().size(), "Ordinary converted mob buds");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "budding")
    public void stopDisableResetAndDuplicatePair(TestContext c) {
        var challenge = ChallengeRegistry.get(ID);
        try (var a = new Arena(c)) {
            a.s.activate("test_easy");
            a.kill(a.spawn(EntityType.SKELETON));
            a.kill(a.spawn(EntityType.COW));
            c.assertEquals(0, a.children().size(), "No births after stop");
            a.s.activate(ID);
            challenge.setEnabled(false);
            a.kill(a.spawn(EntityType.SKELETON));
            a.kill(a.spawn(EntityType.COW));
            c.assertEquals(0, a.children().size(), "Disabled means no births");
            challenge.setEnabled(true);
            a.s.activate("double_challenge:budding,budding");
            a.kill(a.spawn(EntityType.COW));
            c.assertEquals(2, a.children().size(), "Duplicate effective ID still only two");
            a.clearChildren();
            var source = c.getWorld().getServer().getCommandSource().withLevel(2);
            c.assertTrue(c.getWorld().getServer().getCommandManager().getDispatcher().execute("fdc reset", source) > 0, "Real reset");
            a.kill(a.spawn(EntityType.SKELETON));
            a.kill(a.spawn(EntityType.COW));
            c.assertEquals(0, a.children().size(), "No births after reset");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError(e);
        } finally { challenge.setEnabled(true); }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "budding")
    public void globalWorldDeathsAcrossDimensionsAndModes(TestContext c) {
        try (var a = new Arena(c)) {
            // No attached player is necessary for a GLOBAL mob-world effect.
            a.kill(a.spawn(EntityType.SKELETON));
            c.assertEquals(2, a.children().size(), "Global without players");
            a.clearChildren();
            var p = a.s.player();
            for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
                p.changeGameMode(mode);
                a.kill(a.spawn(EntityType.SKELETON));
                c.assertEquals(2, a.children().size(), "World consequence independent of observer mode");
                a.clearChildren();
            }
            p.changeGameMode(GameMode.SURVIVAL);
            var other = a.s.player();
            a.kill(p);
            c.assertEquals(0, a.children().size(), "Player death is excluded");
            a.s.respawn(p, false);
            a.kill(a.spawn(EntityType.SKELETON));
            c.assertEquals(2, a.children().size(), "Multiple players and respawn do not duplicate births");
            a.clearChildren();
            other.getServer().getPlayerManager().remove(other);
            for (var key : List.of(World.NETHER, World.END)) {
                var world = c.getWorld().getServer().getWorld(key);
                a.kill(a.spawn(world, EntityType.SKELETON));
                c.assertEquals(2, a.children(world).size(), "Births in death dimension " + key);
                c.assertEquals(0, a.children().size(), "No overworld duplicate");
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "budding")
    public void pairsUseExistingBirthAndPhysicsHooks(TestContext c) {
        try (var a = new Arena(c)) {
            a.s.activate("double_challenge:budding,netherite_mobs");
            a.kill(a.spawn(EntityType.SKELETON));
            c.assertEquals(2, a.children().size(), "Netherite pair two births");
            for (var child : a.children()) {
                c.getWorld().tickEntity(child);
                c.assertTrue(NetheriteMobsChallenge.isIssued(child.getEquippedStack(EquipmentSlot.CHEST)), "Existing birth kit");
                a.kill(child);
            }
            c.assertEquals(0, a.children().size(), "Armored children still cannot bud");
            a.s.activate("double_challenge:budding,low_gravity");
            a.kill(a.spawn(EntityType.SKELETON));
            c.assertEquals(2, a.children().size(), "Physics pair two births");
            for (var child : a.children()) {
                c.assertTrue(child.getAttributes().hasModifierForAttribute(
                        net.minecraft.entity.attribute.EntityAttributes.GENERIC_GRAVITY, LowGravityChallenge.GRAVITY_MODIFIER),
                        "Existing ENTITY_LOAD physics hook");
                a.kill(child);
            }
            c.assertEquals(0, a.children().size(), "Physics children still cannot bud");
        }
        c.complete();
    }
}
