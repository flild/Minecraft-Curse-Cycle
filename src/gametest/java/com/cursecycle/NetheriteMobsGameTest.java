package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.NetheriteMobsChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.*;

public class NetheriteMobsGameTest implements FabricGameTest {
    private static final String ID = NetheriteMobsChallenge.ID;
    private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET);

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "netherite_mobs")
    public void birthsIncludingPostSpawnInitialization(TestContext c) throws Exception {
        var cleanup = new ArrayList<Entity>();
        var w = c.getWorld();
        long originalTimeOfDay = w.getTimeOfDay();
        try (var s = new ChallengeScenario(c)) {
            // A real first tick may wear an undead mob's helmet in sunlight before we inspect it.
            w.setTimeOfDay(18000);
            w.calculateAmbientDarkness();
            var old = spawn(c, w, EntityType.ZOMBIE, cleanup);
            finish(w, old);
            old.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            s.activate(ID);
            for (var type : List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.COW,
                    EntityType.VILLAGER, EntityType.SLIME, EntityType.WITHER, EntityType.ENDER_DRAGON)) {
                var mob = spawn(c, w, type, cleanup);
                var weapon = new ItemStack(Items.DIAMOND_SWORD);
                mob.equipStack(EquipmentSlot.MAINHAND, weapon);
                mob.setHealth(7);
                finish(w, mob);
                kit(c, mob);
                c.assertTrue(mob.getMainHandStack() == weapon, "Weapon unchanged " + type);
                c.assertEquals(7F, mob.getHealth(), "Health unchanged " + type);
                var helmet = mob.getEquippedStack(EquipmentSlot.HEAD);
                helmet.setDamage(13);
                finish(w, mob);
                c.assertTrue(helmet == mob.getEquippedStack(EquipmentSlot.HEAD), "Idempotent birth");
                c.assertEquals(13, helmet.getDamage(), "No durability refill");
                // Do not leave armed targets (especially villagers/Thorns) for later birth cases.
                mob.discard();
            }
            c.assertTrue(old.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.GOLDEN_HELMET), "Old mob untouched");
            old.discard();
            for (var reason : List.of(SpawnReason.NATURAL, SpawnReason.SPAWN_EGG, SpawnReason.SPAWNER)) {
                var mob = EntityType.ZOMBIE.spawn(w, c.getAbsolutePos(new BlockPos(2, 2, 2)), reason);
                mob.setAiDisabled(true);
                cleanup.add(mob);
                finish(w, mob);
                kit(c, mob);
                mob.discard();
            }
            var command = w.getServer().getCommandSource().withWorld(w).withLevel(2);
            c.assertTrue(w.getServer().getCommandManager().getDispatcher().execute(
                    "summon minecraft:cow ~ ~ ~ {Tags:[\"netherite_mobs_test\"]}", command) > 0, "Real summon command");
            var summoned = w.getEntitiesByType(EntityType.COW, e -> e.getCommandTags().contains("netherite_mobs_test"));
            c.assertEquals(1, summoned.size(), "One summoned mob");
            cleanup.addAll(summoned);
            finish(w, summoned.getFirst());
            kit(c, summoned.getFirst());
            var cow = spawn(c, w, EntityType.COW, cleanup);
            var mate = spawn(c, w, EntityType.COW, cleanup);
            cow.breed(w, mate);
            var babies = w.getEntitiesByType(EntityType.COW, Entity::isAlive).stream().filter(e -> e.isBaby()).toList();
            c.assertFalse(babies.isEmpty(), "Real breeding creates baby");
            cleanup.addAll(babies);
            for (var baby : babies) { finish(w, baby); kit(c, baby); }
            var zombie = spawn(c, w, EntityType.ZOMBIE, cleanup);
            finish(w, zombie);
            var converted = zombie.convertTo(EntityType.DROWNED, true);
            cleanup.add(converted);
            // Infection uses this ordering: convertTo/spawn, THEN initialize/equip.
            converted.initialize(w, w.getLocalDifficulty(converted.getBlockPos()), SpawnReason.CONVERSION, null);
            converted.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            finish(w, converted);
            kit(c, converted);
            for (var dimension : List.of(World.NETHER, World.END)) {
                var other = w.getServer().getWorld(dimension);
                var mob = spawn(c, other, EntityType.COW, cleanup);
                finish(other, mob);
                kit(c, mob);
            }
            var vehicle = spawn(c, w, EntityType.COW, cleanup);
            var rider = spawn(c, w, EntityType.ZOMBIE, cleanup);
            rider.startRiding(vehicle, true);
            w.tickEntity(vehicle);
            kit(c, rider);
        } finally {
            cleanup.forEach(Entity::discard);
            w.setTimeOfDay(originalTimeOfDay);
            w.calculateAmbientDarkness();
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "netherite_mobs")
    public void dropsWearConversionAndEntityNbt(TestContext c) throws Exception {
        var cleanup = new ArrayList<Entity>();
        try (var s = new ChallengeScenario(c)) {
            var w = c.getWorld();
            var player = s.player();
            s.activate(ID);
            var mob = spawn(c, w, EntityType.ZOMBIE, cleanup);
            mob.damage(w.getDamageSources().generic(), 0);
            kit(c, mob);
            var helmet = mob.getEquippedStack(EquipmentSlot.HEAD);
            helmet.setDamage(helmet.getMaxDamage() - 1);
            c.assertTrue(mob.tryEquip(new ItemStack(Items.NETHERITE_HELMET)).isEmpty(), "Pickup cannot replace marked armor");
            c.assertTrue(mob.getEquippedStack(EquipmentSlot.HEAD) == helmet, "Worn armor retained");
            c.assertFalse(mob.tryEquip(new ItemStack(Items.DIAMOND_SWORD)).isEmpty(), "Ordinary weapon pickup works");
            // Remove Unbreaking only in fixture to make real vanilla break deterministic.
            helmet.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
            mob.timeUntilRegen = 0;
            mob.damage(w.getDamageSources().playerAttack(player), 8);
            c.assertTrue(mob.getEquippedStack(EquipmentSlot.HEAD).isEmpty(), "Actual armor break");
            c.assertTrue(mob.getHealth() > 12, "Issued armor and protection really reduce damage");
            finish(w, mob);
            c.assertTrue(mob.getEquippedStack(EquipmentSlot.HEAD).isEmpty(), "Broken armor never regenerates");
            c.assertFalse(mob.tryEquip(new ItemStack(Items.IRON_HELMET)).isEmpty(), "Empty slot takes normal armor");
            var saved = new NbtCompound();
            mob.saveNbt(saved);
            var uuid = mob.getUuid();
            mob.discard();
            var loaded = (MobEntity) EntityType.loadEntityWithPassengers(saved, w, e -> e);
            cleanup.add(loaded);
            c.assertTrue(w.tryLoadEntity(loaded), "Load via vanilla load entry point");
            finish(w, loaded);
            c.assertEquals(uuid, loaded.getUuid(), "Same UUID");
            c.assertTrue(loaded.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.IRON_HELMET), "Load never grants fresh kit");
            c.assertTrue(NetheriteMobsChallenge.isIssued(loaded.getEquippedStack(EquipmentSlot.CHEST)), "Origin survives entity NBT");
            s.activate("no_sprint");
            var converted = loaded.convertTo(EntityType.DROWNED, true);
            cleanup.add(converted);
            finish(w, converted);
            c.assertTrue(NetheriteMobsChallenge.isIssued(converted.getEquippedStack(EquipmentSlot.CHEST)), "Conversion retains origin after ending");
            c.assertTrue(converted.dropStack(converted.getEquippedStack(EquipmentSlot.CHEST).copy()) == null, "Marked replacement drop denied");
            var ordinary = converted.dropStack(new ItemStack(Items.ROTTEN_FLESH));
            cleanup.add(ordinary);
            c.assertTrue(ordinary != null, "Ordinary loot positive control");
            for (var slot : EquipmentSlot.values()) converted.setEquipmentDropChance(slot, 2);
            converted.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            var looting = new ItemStack(Items.NETHERITE_SWORD);
            looting.addEnchantment(w.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.LOOTING).orElseThrow(), 3);
            player.equipStack(EquipmentSlot.MAINHAND, looting);
            converted.timeUntilRegen = 0;
            converted.damage(w.getDamageSources().playerAttack(player), 10000);
            c.assertFalse(converted.isAlive(), "Actual Looting III death");
            var drops = w.getEntitiesByClass(ItemEntity.class, converted.getBoundingBox().expand(3), e -> true);
            cleanup.addAll(drops);
            c.assertTrue(drops.stream().noneMatch(e -> NetheriteMobsChallenge.isIssued(e.getStack())), "No marked death loot");
            c.assertTrue(drops.stream().anyMatch(e -> e.getStack().isOf(Items.DIAMOND_SWORD)), "Normal guaranteed weapon drops");
            var fresh = spawn(c, w, EntityType.COW, cleanup);
            finish(w, fresh);
            c.assertTrue(fresh.getEquippedStack(EquipmentSlot.CHEST).isEmpty(), "No grants after ending");
            s.activate(ID);
            // A birth saved before its first tick must not be lost or reevaluated on restart.
            var pending = spawn(c, w, EntityType.COW, cleanup);
            var pendingNbt = new NbtCompound();
            pending.saveNbt(pendingNbt);
            pending.discard();
            s.activate("no_sprint");
            var pendingLoaded = (MobEntity) EntityType.loadEntityWithPassengers(pendingNbt, w, e -> e);
            cleanup.add(pendingLoaded);
            c.assertTrue(w.tryLoadEntity(pendingLoaded), "Load unfinished birth");
            finish(w, pendingLoaded);
            kit(c, pendingLoaded);
            // Existing mobs from saves predating this mod have no birth marker either.
            var legacy = EntityType.COW.create(w);
            var legacyNbt = new NbtCompound();
            legacy.saveNbt(legacyNbt);
            legacyNbt.remove("cursecycle:mob_birth_recorded");
            legacyNbt.remove("cursecycle:netherite_armor_pending");
            s.activate(ID);
            var legacyLoaded = (MobEntity) EntityType.loadEntityWithPassengers(legacyNbt, w, e -> e);
            cleanup.add(legacyLoaded);
            c.assertTrue(w.tryLoadEntity(legacyLoaded), "Load legacy saved mob during active challenge");
            finish(w, legacyLoaded);
            c.assertTrue(legacyLoaded.getEquippedStack(EquipmentSlot.CHEST).isEmpty(), "Legacy save never becomes a new spawn");
            var curing = spawn(c, w, EntityType.ZOMBIE_VILLAGER, cleanup);
            finish(w, curing);
            curing.setCustomName(net.minecraft.text.Text.literal("netherite-cure-test"));
            s.activate("no_sprint");
            var cure = net.minecraft.entity.mob.ZombieVillagerEntity.class.getDeclaredMethod("finishConversion", ServerWorld.class);
            cure.setAccessible(true);
            cure.invoke(curing, w);
            var cured = w.getEntitiesByType(EntityType.VILLAGER,
                    e -> e.hasCustomName() && e.getCustomName().getString().equals("netherite-cure-test"));
            c.assertEquals(1, cured.size(), "Real cure creates one villager");
            cleanup.addAll(cured);
            finish(w, cured.getFirst());
            kit(c, cured.getFirst());
            s.activate(ID);
            var slime = spawn(c, w, EntityType.SLIME, cleanup);
            slime.setSize(4, true);
            finish(w, slime);
            slime.setHealth(0);
            slime.remove(Entity.RemovalReason.KILLED);
            var children = w.getEntitiesByType(EntityType.SLIME, e -> e.isAlive() && e != slime);
            c.assertFalse(children.isEmpty(), "Actual slime split");
            cleanup.addAll(children);
            for (var child : children) { finish(w, child); kit(c, child); }
        } finally { cleanup.stream().filter(Objects::nonNull).forEach(Entity::discard); }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "netherite_mobs")
    public void globalLifecyclePersistenceAndCommands(TestContext c) throws Exception {
        var cleanup = new ArrayList<Entity>();
        try (var s = new ChallengeScenario(c)) {
            var w = c.getWorld();
            var server = w.getServer();
            var player = s.player();
            s.activate("double_challenge:netherite_mobs,no_sprint");
            for (var mode : GameMode.values()) {
                player.changeGameMode(mode);
                var mob = spawn(c, w, EntityType.COW, cleanup);
                finish(w, mob);
                kit(c, mob);
                c.assertTrue(player.getEquippedStack(EquipmentSlot.CHEST).isEmpty(), "Players never outfitted");
            }
            player.changeGameMode(GameMode.SURVIVAL);
            player.setSprinting(true);
            c.assertFalse(player.isSprinting(), "Double companion works");
            var late = s.player();
            player.setHealth(0);
            player = s.respawn(player, false);
            for (var dimension : List.of(World.NETHER, World.END, World.OVERWORLD)) {
                player.teleport(server.getWorld(dimension), .5, 80, .5, 0, 0);
                player.onTeleportationDone();
                var mob = spawn(c, server.getWorld(dimension), EntityType.COW, cleanup);
                finish(server.getWorld(dimension), mob);
                kit(c, mob);
            }
            server.getPlayerManager().remove(late);
            late = s.player(late.getUuid());
            server.getPlayerManager().remove(late);
            var persistence = server.getOverworld().getPersistentStateManager();
            persistence.save();
            var disk = persistence.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            var restored = CurseCycleState.createFromNbt(disk.getCompound("data"), w.getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, restored);
            c.assertTrue(NetheriteMobsChallenge.active(w), "Disk restores active GLOBAL rule");
            var mob = spawn(c, w, EntityType.COW, cleanup);
            finish(w, mob);
            kit(c, mob);
            CycleManager.activateChallenge(server, restored, "no_sprint");
            persistence.save();
            disk = persistence.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            restored = CurseCycleState.createFromNbt(disk.getCompound("data"), w.getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, restored);
            c.assertFalse(NetheriteMobsChallenge.active(w), "Offline ending persists");
            c.assertTrue(NetheriteMobsChallenge.isIssued(mob.getEquippedStack(EquipmentSlot.CHEST)), "Old kit remains");
            restored.getPersonalActiveChallenges().put(player.getUuid(), new ArrayList<>(List.of(ID)));
            c.assertFalse(NetheriteMobsChallenge.active(w), "PERSONAL ID cannot enable GLOBAL world rule");
            for (var command : List.of("fdc list", "fdc activate netherite_mobs", "fdc active"))
                c.assertTrue(server.getCommandManager().getDispatcher().execute(command, server.getCommandSource().withLevel(2)) > 0, "Command " + command);
            var challenge = ChallengeRegistry.get(ID);
            try {
                challenge.setEnabled(false);
                var disabled = spawn(c, w, EntityType.COW, cleanup);
                finish(w, disabled);
                c.assertTrue(disabled.getEquippedStack(EquipmentSlot.CHEST).isEmpty(), "Disabled gate");
            } finally { challenge.setEnabled(true); }
        } finally { cleanup.forEach(Entity::discard); }
        c.waitAndRun(5, c::complete);
    }

    private static <T extends MobEntity> T spawn(TestContext c, ServerWorld w, EntityType<T> type, List<Entity> cleanup) {
        var mob = type.create(w);
        mob.refreshPositionAndAngles(c.getAbsolutePos(new BlockPos(2, 3, 2)), 0, 0);
        mob.setAiDisabled(true);
        c.assertTrue(w.spawnEntity(mob), "Spawn " + type);
        cleanup.add(mob);
        return mob;
    }

    private static void finish(ServerWorld w, Entity mob) {
        mob.setNoGravity(true);
        w.tickEntity(mob); // Actual transformed universal tick entry point, not a direct helper call.
    }

    private static void kit(TestContext c, MobEntity mob) {
        var items = List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
        for (int i = 0; i < ARMOR.size(); i++) {
            var slot = ARMOR.get(i);
            var stack = mob.getEquippedStack(slot);
            c.assertTrue(stack.isOf(items.get(i)), "Netherite " + slot + " on " + mob.getType());
            c.assertTrue(NetheriteMobsChallenge.isIssued(stack), "Origin " + slot);
            c.assertEquals(0, stack.getDamage(), "Full initial durability " + slot + " on " + mob.getType());
            Map<RegistryKey<Enchantment>, Integer> expected = new HashMap<>(Map.of(Enchantments.PROTECTION, 4,
                    Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1, Enchantments.THORNS, 3));
            if (slot == EquipmentSlot.HEAD) expected.putAll(Map.of(Enchantments.RESPIRATION, 3, Enchantments.AQUA_AFFINITY, 1));
            if (slot == EquipmentSlot.LEGS) expected.put(Enchantments.SWIFT_SNEAK, 3);
            if (slot == EquipmentSlot.FEET) expected.putAll(Map.of(Enchantments.FEATHER_FALLING, 4, Enchantments.DEPTH_STRIDER, 3, Enchantments.SOUL_SPEED, 3));
            var actual = stack.get(DataComponentTypes.ENCHANTMENTS);
            c.assertEquals(expected.size(), actual.getEnchantments().size(), "Exact enchantment count " + slot);
            for (var entry : expected.entrySet())
                c.assertEquals(entry.getValue(), actual.getLevel(mob.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(entry.getKey()).orElseThrow()), "Exact enchantment " + entry.getKey());
        }
    }
}
