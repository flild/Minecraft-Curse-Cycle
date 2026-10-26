package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AttackRecoilGameTest implements FabricGameTest {
    private static final Set<UUID> CANCELLED = new HashSet<>();
    static {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !CANCELLED.contains(entity.getUuid()));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "attack_recoil", tickLimit = 300)
    public void primaryDamageBeforeTotemAndFinalVelocity(TestContext context) {
        try (var arena = new Arena(context)) {
            var player = arena.player();
            var target = arena.cow(player, 0, 2);
            Vec3d initial = new Vec3d(0.2, 0.17, 0.1);
            player.setVelocity(initial);
            player.attack(target);
            vector(context, player.getVelocity(), initial, "Inactive attack");
            context.assertTrue(target.getHealth() < target.getMaxHealth(), "Vanilla hit control");
            arena.scenario.activate("attack_recoil");
            vector(context, player.getVelocity(), initial, "No activation impulse");
            target.timeUntilRegen = 0;
            float before = target.getHealth();
            player.attack(target);
            context.assertTrue(target.getHealth() < before, "Primary health reduced");
            vector(context, player.getVelocity(), initial.add(0, 0, -0.6), "Add impulse; preserve Y");
            context.assertTrue(player.velocityModified, "Vanilla synchronization scheduled");
            var packets = new ArrayList<net.minecraft.network.packet.Packet<?>>();
            new EntityTrackerEntry(context.getWorld(), player, 1, true, packets::add).tick();
            context.assertTrue(!player.velocityModified, "Tracker consumes sync flag");
            context.assertTrue(packets.stream().anyMatch(p -> p instanceof EntityVelocityUpdateS2CPacket), "Tracker emits velocity packet");

            target = arena.cow(player, 3, 4);
            player.setVelocity(initial);
            player.setSneaking(true);
            player.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
            player.attack(target);
            vector(context, player.getVelocity(), initial.add(-0.36, 0, -0.48), "Diagonal ignores sneak/resistance");
            player.setSneaking(false);
            target = arena.cow(player, 0, 0);
            player.setYaw(90);
            player.setPitch(90);
            player.setVelocity(initial);
            player.attack(target);
            vector(context, player.getVelocity(), initial.add(0.6, 0, 0), "Coincident XZ uses horizontal yaw even looking down");
            target.discard();
            player.setYaw(0);

            target = arena.cow(player, 0, 2);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 1));
            float absorption = target.getAbsorptionAmount();
            context.assertTrue(absorption > 0, "Absorption control");
            player.setVelocity(Vec3d.ZERO);
            player.attack(target);
            context.assertEquals(target.getMaxHealth(), target.getHealth(), "Only absorption lost");
            context.assertTrue(target.getAbsorptionAmount() < absorption, "Absorption reduced");
            vector(context, player.getVelocity(), new Vec3d(0, 0, -0.6), "Absorption-only hit");
            target = arena.cow(player, 0, 2);
            target.setHealth(0.1F);
            target.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.setVelocity(Vec3d.ZERO);
            player.attack(target);
            context.assertTrue(target.isAlive() && target.getOffHandStack().isEmpty() && target.getHealth() > 0.1F, "Totem restores above starting HP");
            vector(context, player.getVelocity(), new Vec3d(0, 0, -0.6), "Pre-totem damage qualifies");
            target = arena.cow(player, 0, 2);
            target.setHealth(0.1F);
            player.setVelocity(Vec3d.ZERO);
            player.attack(target);
            context.assertTrue(!target.isAlive(), "Lethal hit control");
            vector(context, player.getVelocity(), new Vec3d(0, 0, -0.6), "Lethal target qualifies");

            arena.scenario.activate("test_easy");
            arena.charge(player);
            target = arena.cow(player, 0, 2);
            player.setSprinting(true);
            player.setVelocity(initial);
            player.attack(target);
            Vec3d controlVelocity = player.getVelocity(), targetVelocity = target.getVelocity();
            float controlHealth = target.getHealth();
            arena.scenario.activate("attack_recoil");
            arena.charge(player);
            target = arena.cow(player, 0, 2);
            player.setSprinting(true);
            player.setVelocity(initial);
            player.attack(target);
            vector(context, player.getVelocity(), controlVelocity.add(0, 0, -0.6), "Impulse after sprint damping");
            vector(context, target.getVelocity(), targetVelocity, "Target knockback unchanged");
            context.assertEquals(controlHealth, target.getHealth(), "Target damage unchanged");
        }
        context.waitAndRun(5, context::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "attack_recoil_exclusions", tickLimit = 300)
    public void sweepShieldsFramesAndIndirectSources(TestContext context) {
        try (var arena = new Arena(context)) {
            var player = arena.player();
            arena.scenario.activate("attack_recoil");
            var target = arena.cow(player, 0, 2);
            player.attack(target);
            float hp = target.getHealth();
            player.setVelocity(Vec3d.ZERO);
            player.attack(target);
            context.assertEquals(hp, target.getHealth(), "i-frame control");
            vector(context, player.getVelocity(), Vec3d.ZERO, "i-frame rejection");
            target = arena.cow(player, 0, 2);
            CANCELLED.add(target.getUuid());
            try { player.attack(target); } finally { CANCELLED.remove(target.getUuid()); }
            context.assertEquals(target.getMaxHealth(), target.getHealth(), "Event cancellation");
            vector(context, player.getVelocity(), Vec3d.ZERO, "Cancelled hit");
            target = arena.cow(player, 0, 2);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 4));
            player.attack(target);
            context.assertEquals(target.getMaxHealth(), target.getHealth(), "Resistance V eliminates damage");
            vector(context, player.getVelocity(), Vec3d.ZERO, "Damage return without loss is insufficient");
            target = arena.cow(player, 0, 2);
            target.setInvulnerable(true);
            player.attack(target);
            vector(context, player.getVelocity(), Vec3d.ZERO, "Invulnerable target");
            player.attack(arena.entity(EntityType.ARMOR_STAND, player, 0, 2));
            vector(context, player.getVelocity(), Vec3d.ZERO, "Armor stand excluded");
            player.attack(arena.entity(EntityType.BOAT, player, 0, 2));
            player.swingHand(Hand.MAIN_HAND);
            vector(context, player.getVelocity(), Vec3d.ZERO, "Nonliving target and empty swing");

            target = arena.cow(player, 0, 2);
            var arrow = new net.minecraft.entity.projectile.ArrowEntity(context.getWorld(), player,
                    new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            for (var damageSource : List.of(player.getDamageSources().arrow(arrow, player), player.getDamageSources().onFire(),
                    player.getDamageSources().thorns(player), player.getDamageSources().explosion(player, player),
                    player.getDamageSources().playerAttack(player))) {
                target.timeUntilRegen = 0;
                target.setHealth(target.getMaxHealth());
                target.damage(damageSource, 1);
                vector(context, player.getVelocity(), Vec3d.ZERO, "Outside main attack: " + damageSource.getName());
            }
            var shield = arena.player();
            shield.teleport(context.getWorld(), player.getX(), player.getY(), player.getZ() + 2, 180, 0);
            shield.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
            shield.setCurrentHand(Hand.OFF_HAND);
            for (int i = 0; i < 6; i++) shield.playerTick();
            context.assertTrue(shield.isBlocking(), "Shield actively blocking");
            context.assertTrue(shield.shouldDamagePlayer(player), "PvP permits shield test hit");
            arena.scenario.activate("test_easy");
            arena.charge(player);
            player.setVelocity(Vec3d.ZERO);
            player.attack(shield);
            context.assertEquals(20F, shield.getHealth(), "Shield fully blocks");
            var shieldControl = player.getVelocity();
            arena.scenario.activate("attack_recoil");
            arena.charge(player);
            player.setVelocity(Vec3d.ZERO);
            player.attack(shield);
            context.assertEquals(20F, shield.getHealth(), "Active shield fully blocks");
            vector(context, player.getVelocity(), shieldControl, "Shield block adds no challenge recoil");
            shield.stopUsingItem();
            shield.discard();

            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            arena.charge(player);
            player.setOnGround(true);
            target = arena.cow(player, 0, 2);
            var secondary = arena.cow(player, 0.8, 2);
            player.setVelocity(Vec3d.ZERO);
            player.attack(target);
            context.assertTrue(secondary.getHealth() < secondary.getMaxHealth(), "Secondary sweep target damaged");
            vector(context, player.getVelocity(), new Vec3d(0, 0, -0.6), "Sweep gives one impulse");
            player.attack(arena.cow(player, 0, 2));
            vector(context, player.getVelocity(), new Vec3d(0, 0, -1.2), "Separate successful call in same tick");
        }
        context.waitAndRun(5, context::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "attack_recoil_lifecycle", tickLimit = 300)
    public void activityLifecycleAndPlayerDamageReactions(TestContext context) throws Exception {
        try (var arena = new Arena(context)) {
            var player = arena.player();
            var dispatcher = player.getServer().getCommandManager().getDispatcher();
            var source = player.getServer().getCommandSource();
            dispatcher.execute("fdc list", source);
            dispatcher.execute("fdc activate \"double_challenge:attack_recoil,no_sprint\"", source);
            dispatcher.execute("fdc active", source);
            context.assertTrue(arena.scenario.state.getActiveChallengeIds().contains("attack_recoil"), "Command activation");
            for (var mode : new GameMode[]{GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL}) {
                player.changeGameMode(mode);
                player.setSprinting(true);
                if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                    context.assertTrue(!player.isSprinting(), "No sprint works");
                player.setSprinting(false);
                player.setVelocity(Vec3d.ZERO);
                player.attack(arena.cow(player, 0, 2));
                vector(context, player.getVelocity(), mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE
                        ? new Vec3d(0, 0, -0.6) : Vec3d.ZERO, "Mode " + mode);
            }
            var challenge = ChallengeRegistry.get("attack_recoil");
            challenge.setEnabled(false);
            try {
                player.setVelocity(Vec3d.ZERO);
                player.attack(arena.cow(player, 0, 2));
                vector(context, player.getVelocity(), Vec3d.ZERO, "Disabled mechanic");
            } finally { challenge.setEnabled(true); }
            var boat = arena.entity(EntityType.BOAT, player, 0, 0);
            context.assertTrue(player.startRiding(boat, true), "Passenger control");
            player.setVelocity(Vec3d.ZERO);
            player.attack(arena.cow(player, 0, 2));
            vector(context, player.getVelocity(), Vec3d.ZERO, "Passenger excluded");
            vector(context, boat.getVelocity(), Vec3d.ZERO, "Vehicle not accelerated");
            context.assertTrue(player.hasVehicle(), "No dismount");
            player.stopRiding();
            boat.discard();

            var late = arena.player();
            late.attack(arena.cow(late, 0, 2));
            vector(context, late.getVelocity(), new Vec3d(0, 0, -0.6), "Late player independently affected");
            arena.scenario.state.getActiveChallengeIds().clear();
            arena.scenario.state.getPersonalActiveChallengeIds(player.getUuid()).add("attack_recoil");
            player.setVelocity(Vec3d.ZERO);
            player.attack(arena.cow(player, 0, 2));
            vector(context, player.getVelocity(), new Vec3d(0, 0, -0.6), "Personal activation");
            late.setVelocity(Vec3d.ZERO);
            late.attack(arena.cow(late, 0, 2));
            vector(context, late.getVelocity(), Vec3d.ZERO, "Personal activation does not leak");
            arena.scenario.state.getPersonalActiveChallengeIds(player.getUuid()).clear();
            arena.scenario.activate("attack_recoil");
            player.damage(player.getDamageSources().generic(), 100);
            context.assertTrue(!player.isAlive(), "Death control");
            player.setVelocity(Vec3d.ZERO);
            player.attack(arena.cow(player, 0, 2));
            vector(context, player.getVelocity(), Vec3d.ZERO, "Dead attacker excluded");
            player = arena.scenario.respawn(player, false);
            arena.ready(player);
            player.attack(arena.cow(player, 0, 2));
            vector(context, player.getVelocity(), new Vec3d(0, 0, -0.6), "Respawn has one impulse");
            player = arena.scenario.respawn(player, true);
            arena.ready(player);
            player.attack(arena.cow(player, 0, 2));
            vector(context, player.getVelocity(), new Vec3d(0, 0, -0.6), "Entity replacement has no stale marker");

            var persistence = context.getWorld().getPersistentStateManager();
            arena.scenario.state.markDirty();
            persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(saved.getCompound("data"), context.getWorld().getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, loaded);
            player.setVelocity(Vec3d.ZERO);
            player.attack(arena.cow(player, 0, 2));
            vector(context, player.getVelocity(), new Vec3d(0, 0, -0.6), "Disk load restores activation");
            CycleManager.activateChallenge(player.getServer(), loaded, "no_sprint");
            vector(context, player.getVelocity(), new Vec3d(0, 0, -0.6), "Ending retains velocity");
            player.setVelocity(Vec3d.ZERO);
            player.attack(arena.cow(player, 0, 2));
            vector(context, player.getVelocity(), Vec3d.ZERO, "No new recoil after ending");
            CycleManager.activateChallenge(player.getServer(), loaded, "double_challenge:attack_recoil,damage_slot_drop");
            late.getInventory().clear();
            late.getInventory().setStack(10, new ItemStack(Items.DIAMOND));
            late.getInventory().setStack(11, new ItemStack(Items.DIAMOND));
            late.teleport(context.getWorld(), player.getX(), player.getY(), player.getZ() + 2, 0, 0);
            late.timeUntilRegen = 0;
            player.setVelocity(Vec3d.ZERO);
            float hp = late.getHealth();
            player.attack(late);
            context.assertTrue(late.getHealth() < hp, "Player target actually damaged");
            int remaining = (late.getInventory().getStack(10).isEmpty() ? 0 : 1) + (late.getInventory().getStack(11).isEmpty() ? 0 : 1);
            context.assertEquals(1, remaining, "Player damage dispatches incoming drop once");
            vector(context, player.getVelocity(), new Vec3d(0, 0, -0.6), "Player target recoils once");
        }
        context.waitAndRun(5, context::complete);
    }

    private static void vector(TestContext context, Vec3d actual, Vec3d expected, String message) {
        context.assertTrue(actual.squaredDistanceTo(expected) < 1.0E-10, message + ": expected " + expected + ", got " + actual);
    }
    private static final class Arena implements AutoCloseable {
        final ChallengeScenario scenario;
        final List<Entity> entities = new ArrayList<>();
        final boolean pvp;
        Arena(TestContext context) {
            scenario = new ChallengeScenario(context);
            pvp = context.getWorld().getServer().isPvpEnabled();
            context.getWorld().getServer().setPvpEnabled(true);
        }
        ServerPlayerEntity player() {
            var player = scenario.player();
            ready(player);
            return player;
        }
        void ready(ServerPlayerEntity player) {
            for (int i = 0; i < 61; i++) player.tick();
            player.timeUntilRegen = 0;
            charge(player);
            var pos = scenario.context.getAbsolutePos(new BlockPos(2, 3, 2));
            player.teleport(scenario.context.getWorld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            player.setVelocity(Vec3d.ZERO);
        }
        void charge(ServerPlayerEntity player) {
            for (int i = 0; i < 30; i++) player.playerTick();
            player.setSprinting(false);
            player.setOnGround(false);
            player.fallDistance = 0;
        }
        net.minecraft.entity.passive.CowEntity cow(ServerPlayerEntity player, double x, double z) {
            var cow = entity(EntityType.COW, player, x, z);
            cow.setAiDisabled(true);
            return cow;
        }
        <T extends Entity> T entity(EntityType<T> type, ServerPlayerEntity player, double x, double z) {
            var entity = type.create(player.getServerWorld());
            entity.refreshPositionAndAngles(player.getX() + x, player.getY(), player.getZ() + z, 0, 0);
            entities.add(entity);
            player.getServerWorld().spawnEntity(entity);
            return entity;
        }
        @Override public void close() {
            entities.forEach(Entity::discard);
            scenario.close();
            scenario.context.getWorld().getServer().setPvpEnabled(pvp);
        }
    }
}
