package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.LowGravityChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.*;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.explosion.Explosion;

import java.util.*;

public class LowGravityGameTest implements FabricGameTest {
    private static final String ID = LowGravityChallenge.ID;
    private static final double DRAG = .9800000190734863;
    private static final Identifier FOREIGN = Identifier.of("test", "foreign_gravity");

    private static void near(TestContext c, double expected, double actual, String message) {
        c.assertTrue(Math.abs(expected - actual) < .00001, message + ": expected=" + expected + ", actual=" + actual);
    }
    private static void reset(LivingEntity entity, BlockPos base, Vec3d velocity) {
        entity.refreshPositionAndAngles(base.getX() + .5, base.getY() + .5, base.getZ() + .5, 0, 0);
        entity.setOnGround(false);
        entity.setVelocity(Vec3d.ZERO);
        entity.baseTick();
        entity.setVelocity(velocity);
    }
    private static double step(LivingEntity entity, BlockPos base, Vec3d velocity) {
        reset(entity, base, velocity);
        entity.travel(Vec3d.ZERO);
        return entity.getVelocity().y;
    }
    private static double gravity(LivingEntity entity) { return entity.getAttributeValue(EntityAttributes.GENERIC_GRAVITY); }
    private static void foreign(LivingEntity entity) {
        entity.getAttributeInstance(EntityAttributes.GENERIC_GRAVITY).setBaseValue(.04);
        entity.getAttributeInstance(EntityAttributes.GENERIC_GRAVITY).addPersistentModifier(new EntityAttributeModifier(
                FOREIGN, .5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static MobEntity pig(TestContext c, BlockPos base) {
        var pig = EntityType.PIG.create(c.getWorld());
        pig.setAiDisabled(true);
        reset(pig, base, Vec3d.ZERO);
        c.assertTrue(c.getWorld().spawnEntity(pig), "Real ENTITY_LOAD spawn");
        return pig;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "low_gravity_physics")
    public void realPlayerAndGroundMobTravelJumpAndForeignAttributes(TestContext c) throws Exception {
        var base = c.getAbsolutePos(new BlockPos(2, 15, 2));
        try (var s = new ChallengeScenario(c)) {
            var p = s.player(UUID.randomUUID(), true);
            var pig = pig(c, base);
            try {
                pig.setAiDisabled(false); // Vanilla travel skips movement entirely for NoAI mobs.
                near(c, -.08 * DRAG, step(p, base, Vec3d.ZERO), "Player control");
                near(c, -.08 * DRAG, step(pig, base, Vec3d.ZERO), "Mob control");
                s.activate(ID);
                pig.tick(); // Ordinary transformed tick updates an already loaded mob.
                for (LivingEntity entity : List.of(p, pig)) {
                    near(c, .02, gravity(entity), "Single gravity modifier");
                    near(c, -.02 * DRAG, step(entity, base, Vec3d.ZERO), "Slower fall");
                    reset(entity, base, Vec3d.ZERO); entity.setOnGround(true); entity.jump();
                    near(c, .42 * 1.25, entity.getVelocity().y, "Higher real jump impulse");
                    double y = entity.getVelocity().y;
                    near(c, (y - .02) * DRAG, step(entity, base, new Vec3d(.2, y, .3)), "Ascent acceleration once");
                    near(c, .2 * .91, entity.getVelocity().x, "Horizontal motion unchanged");
                    entity.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 200, 1));
                    reset(entity, base, Vec3d.ZERO); entity.setOnGround(true); entity.jump();
                    near(c, .42 * 1.25 + .2, entity.getVelocity().y, "Jump Boost remains additive");
                    entity.removeStatusEffect(StatusEffects.JUMP_BOOST);
                    foreign(entity);
                    near(c, .04 * 1.5 * .25, gravity(entity), "External base and modifier compose");
                    entity.setNoGravity(true);
                    near(c, 0, step(entity, base, Vec3d.ZERO), "NoGravity preserved");
                    entity.setNoGravity(false);
                }
                for (int i = 0; i < 20; i++) { LowGravityChallenge.reconcile(p); pig.tick(); }
                near(c, .015, gravity(pig), "Repeated ticks never compound");
                s.activate("test_easy"); pig.tick();
                near(c, .06, gravity(p), "End restores external player gravity");
                near(c, .06, gravity(pig), "End restores external mob gravity");
                var damage = LivingEntity.class.getDeclaredMethod("computeFallDamage", float.class, float.class);
                damage.setAccessible(true);
                int control = (int) damage.invoke(p, 8F, 1F); s.activate(ID);
                near(c, control, (int) damage.invoke(p, 8F, 1F), "Fall damage is vanilla");
            } finally { pig.discard(); }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "low_gravity_fluids")
    public void fluidsSlowFallingLevitationAndElytra(TestContext c) throws Exception {
        var base = c.getAbsolutePos(new BlockPos(3, 8, 3));
        var world = c.getWorld(); var blocks = new LinkedHashMap<BlockPos, BlockState>();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -2; y <= 3; y++) {
            var pos = base.add(x, y, z); blocks.put(pos, world.getBlockState(pos));
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        try (var s = new ChallengeScenario(c)) {
            var p = s.player(UUID.randomUUID(), true); s.activate(ID);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0));
            near(c, -.01 * DRAG, step(p, base, Vec3d.ZERO), "Slow Falling vanilla min, no second factor");
            near(c, (.2 - .02) * DRAG, step(p, base, new Vec3d(0, .2, 0)), "Slow Falling ascent");
            p.removeStatusEffect(StatusEffects.SLOW_FALLING);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 200, 1));
            near(c, .02 * DRAG, step(p, base, Vec3d.ZERO), "Levitation owns acceleration");
            p.removeStatusEffect(StatusEffects.LEVITATION);
            for (var fluid : List.of(Blocks.WATER, Blocks.LAVA)) {
                for (var pos : blocks.keySet()) world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                for (var pos : blocks.keySet()) world.setBlockState(pos, fluid.getDefaultState(), Block.NOTIFY_LISTENERS);
                near(c, fluid == Blocks.WATER ? -.003 : -.02 / 4,
                        step(p, base, Vec3d.ZERO), "Fluid gravity " + fluid);
                c.assertTrue(fluid == Blocks.WATER ? p.isTouchingWater() : p.isInLava(), "Actual fluid branch");
                if (fluid == Blocks.WATER) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0));
                    near(c, -.1 * .800000011920929 - .01 / 16,
                            step(p, base, new Vec3d(0, -.1, 0)), "Water Slow Falling");
                    p.removeStatusEffect(StatusEffects.SLOW_FALLING);
                    p.setSprinting(true); near(c, 0, step(p, base, Vec3d.ZERO), "Swimming sprint unchanged"); p.setSprinting(false);
                }
            }
            for (var pos : blocks.keySet()) world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            reset(p, base, Vec3d.ZERO); p.startFallFlying();
            c.assertTrue(p.isFallFlying(), "Elytra branch");
            p.travel(Vec3d.ZERO); near(c, -.02 * .25 * .9 * DRAG, p.getVelocity().y, "Elytra gravity"); p.stopFallFlying();
        } finally { blocks.forEach((pos, block) -> world.setBlockState(pos, block, Block.NOTIFY_LISTENERS)); }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "low_gravity_knockback")
    public void takeKnockbackKeepsResistanceCapsAndExistingVelocity(TestContext c) throws Exception {
        var base = c.getAbsolutePos(new BlockPos(2, 12, 2));
        try (var s = new ChallengeScenario(c)) {
            var p = s.player(); var pig = pig(c, base);
            try {
                s.activate(ID);
                for (LivingEntity entity : List.of(p, pig)) {
                    entity.setVelocity(.2, -.3, .4); entity.setOnGround(true);
                    entity.takeKnockback(.4, 1, 0);
                    near(c, -.5, entity.getVelocity().x, "Strength x1.5, previous velocity halved once");
                    near(c, .4, entity.getVelocity().y, "Grounded vertical cap preserved");
                    near(c, .2, entity.getVelocity().z, "Perpendicular old velocity not multiplied");
                    entity.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(.5);
                    entity.setVelocity(Vec3d.ZERO); entity.setOnGround(false); entity.takeKnockback(.4, 1, 0);
                    near(c, -.3, entity.getVelocity().x, "Resistance respected");
                    near(c, 0, entity.getVelocity().y, "Airborne vertical unchanged");
                    entity.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
                    entity.setVelocity(Vec3d.ZERO); entity.takeKnockback(.4, 1, 0);
                    near(c, 0, entity.getVelocity().length(), "Full resistance remains full");
                }
            } finally { pig.discard(); }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "low_gravity_projectile_explosion")
    public void punchAndExplosionScaleOnlyTheirImpulsesAndClientVector(TestContext c) throws Exception {
        var base = c.getAbsolutePos(new BlockPos(2, 15, 2));
        try (var s = new ChallengeScenario(c)) {
            var p = s.player(); var pig = pig(c, base);
            var bow = new ItemStack(Items.BOW);
            bow.addEnchantment(c.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT)
                    .entryOf(net.minecraft.enchantment.Enchantments.PUNCH), 1);
            var arrow = new ArrowEntity(c.getWorld(), p, new ItemStack(Items.ARROW), bow);
            var knock = PersistentProjectileEntity.class.getDeclaredMethod("knockback", LivingEntity.class,
                    net.minecraft.entity.damage.DamageSource.class); knock.setAccessible(true);
            try {
                arrow.setVelocity(0, 0, 2);
                for (LivingEntity target : List.of(p, pig)) {
                    s.activate("test_easy"); target.setVelocity(Vec3d.ZERO);
                    knock.invoke(arrow, target, p.getDamageSources().arrow(arrow, p)); var control = target.getVelocity();
                    c.assertTrue(control.length() > 0, "Real Punch impulse");
                    s.activate(ID); target.setVelocity(.2, .3, .4);
                    knock.invoke(arrow, target, p.getDamageSources().arrow(arrow, p));
                    near(c, 0, target.getVelocity().subtract(new Vec3d(.2, .3, .4)).subtract(control.multiply(1.5)).length(),
                            "Punch adds a single scaled impulse");
                }
                p.setInvulnerable(true); pig.setInvulnerable(true);
                s.activate("test_easy"); reset(p, base, Vec3d.ZERO); reset(pig, base.add(0, 0, 1), Vec3d.ZERO);
                var explosion = new Explosion(c.getWorld(), null, p.getX() - 1, p.getY(), p.getZ(), 2, false, Explosion.DestructionType.KEEP);
                explosion.collectBlocksAndDamageEntities(); var controlP = p.getVelocity(); var controlMob = pig.getVelocity();
                c.assertTrue(controlP.length() > 0 && controlMob.length() > 0, "Real explosion control");
                s.activate(ID); reset(p, base, new Vec3d(.2, .3, .4)); reset(pig, base.add(0, 0, 1), Vec3d.ZERO);
                explosion = new Explosion(c.getWorld(), null, p.getX() - 1, p.getY(), p.getZ(), 2, false, Explosion.DestructionType.KEEP);
                explosion.collectBlocksAndDamageEntities();
                near(c, 0, p.getVelocity().subtract(new Vec3d(.2, .3, .4)).subtract(controlP.multiply(1.5)).length(), "Explosion player");
                near(c, 0, pig.getVelocity().subtract(controlMob.multiply(1.5)).length(), "Explosion mob");
                near(c, 0, explosion.getAffectedPlayers().get(p).subtract(controlP.multiply(1.5)).length(), "S2C explosion vector matches");
            } finally { pig.discard(); arrow.discard(); }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "low_gravity_lifecycle")
    public void modesPersonalDisableStopAndDuplicateActivation(TestContext c) throws Exception {
        var challenge = ChallengeRegistry.get(ID);
        try (var s = new ChallengeScenario(c)) {
            var p = s.player(); var other = s.player(); var pig = pig(c, c.getAbsolutePos(new BlockPos(2, 10, 2)));
            try {
                s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID); p.playerTick(); other.playerTick(); pig.tick();
                near(c, .02, gravity(p), "PERSONAL owner"); near(c, .08, gravity(other), "Other player unaffected");
                near(c, .08, gravity(pig), "PERSONAL does not change world mobs");
                s.activate(ID); p.playerTick(); pig.tick(); near(c, .02, gravity(p), "GLOBAL plus PERSONAL once");
                near(c, .02, gravity(pig), "GLOBAL affects mob");
                for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL)) {
                    p.changeGameMode(mode); p.playerTick();
                    near(c, mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR ? .08 : .02, gravity(p), "Mode " + mode);
                }
                var boat = new BoatEntity(c.getWorld(), p.getX(), p.getY(), p.getZ());
                p.startRiding(boat, true); p.playerTick(); near(c, .08, gravity(p), "Passenger excluded"); p.stopRiding(); boat.discard();
                p.playerTick(); near(c, .02, gravity(p), "Dismount resumes");
                challenge.setEnabled(false); p.playerTick(); pig.tick();
                near(c, .08, gravity(p), "Disable player"); near(c, .08, gravity(pig), "Disable mob");
                challenge.setEnabled(true); p.playerTick(); pig.tick();
                p.setVelocity(.1, -.7, .2); p.fallDistance = 12;
                s.state.getPersonalActiveChallengeIds(p.getUuid()).clear(); s.activate("test_easy"); pig.tick();
                near(c, .08, gravity(p), "Stop player"); near(c, .08, gravity(pig), "Stop mob on next ordinary tick");
                near(c, -.7, p.getVelocity().y, "Stop keeps momentum"); near(c, 12, p.fallDistance, "Stop keeps fall distance");
                s.activate(ID); pig.tick();
                c.getWorld().getServer().getCommandManager().executeWithPrefix(
                        c.getWorld().getServer().getCommandSource(), "fdc reset");
                p.playerTick(); pig.tick();
                near(c, .08, gravity(p), "Actual reset releases player"); near(c, .08, gravity(pig), "Actual reset releases mob");
            } finally { challenge.setEnabled(true); pig.discard(); }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "low_gravity_mob_load")
    public void newAndNbtLoadedMobsDoNotPersistOwnModifiers(TestContext c) throws Exception {
        var base = c.getAbsolutePos(new BlockPos(2, 12, 2));
        try (var s = new ChallengeScenario(c)) {
            s.activate(ID);
            var pig = pig(c, base); MobEntity loaded = null;
            try {
                near(c, .02, gravity(pig), "New spawn updated before first tick"); foreign(pig);
                var nbt = new NbtCompound(); pig.writeNbt(nbt);
                c.assertFalse(nbt.toString().contains("low_gravity/"), "Temporary modifiers absent from disk NBT");
                pig.discard(); loaded = EntityType.PIG.create(c.getWorld()); loaded.readNbt(nbt);
                near(c, .06, gravity(loaded), "Vanilla NBT retains only external changes");
                c.assertTrue(c.getWorld().spawnEntity(loaded), "Real load into world");
                near(c, .015, gravity(loaded), "Loaded entity receives current rule once");
                nbt = new NbtCompound(); loaded.writeNbt(nbt); loaded.discard(); s.activate("test_easy");
                loaded = EntityType.PIG.create(c.getWorld()); loaded.readNbt(nbt); c.getWorld().spawnEntity(loaded);
                near(c, .06, gravity(loaded), "Late load after end keeps external gravity only");
                c.assertFalse(loaded.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH)
                        .hasModifier(LowGravityChallenge.JUMP_MODIFIER), "No stale jump modifier");
            } finally { pig.discard(); if (loaded != null) loaded.discard(); }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "low_gravity_player_reload")
    public void respawnReconnectDimensionsAndStateReload(TestContext c) throws Exception {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player(); var uuid = p.getUuid(); s.activate(ID);
            p.setHealth(0); p.playerTick(); near(c, .08, gravity(p), "Dead player released");
            p = s.respawn(p, false); near(c, .02, gravity(p), "Respawn");
            p.teleport(p.getServer().getWorld(World.NETHER), 0, 100, 0, 0, 0); p.playerTick(); near(c, .02, gravity(p), "Dimension");
            p.teleport(c.getWorld(), 0, 100, 0, 0, 0);
            var manager = p.getServer().getOverworld().getPersistentStateManager();
            var nbt = s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
            var restored = CurseCycleState.createFromNbt(nbt, c.getWorld().getRegistryManager());
            manager.set(CurseCycleState.STATE_KEY, restored); p.playerTick(); near(c, .02, gravity(p), "State NBT reload idempotent");
            p.getServer().getPlayerManager().remove(p); p = s.player(uuid); near(c, .02, gravity(p), "Reconnect current activation");
            p.getServer().getPlayerManager().remove(p); CycleManager.activateChallenge(c.getWorld().getServer(), restored, "test_easy");
            p = s.player(uuid); near(c, .08, gravity(p), "End while offline");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "low_gravity_sync_pairs")
    public void vanillaAttributePacketAndExistingPhysicsPairs(TestContext c) throws Exception {
        var base = c.getAbsolutePos(new BlockPos(2, 15, 2));
        try (var s = new ChallengeScenario(c)) {
            var p = s.player(UUID.randomUUID(), true); s.activate(ID);
            c.assertTrue(EntityAttributes.GENERIC_GRAVITY.value().isTracked()
                    && EntityAttributes.GENERIC_JUMP_STRENGTH.value().isTracked(), "Both vanilla attributes tracked");
            var packet = new EntityAttributesS2CPacket(p.getId(), p.getAttributes().getAttributesToSend());
            var buf = new RegistryByteBuf(Unpooled.buffer(), p.getRegistryManager());
            try {
                EntityAttributesS2CPacket.CODEC.encode(buf, packet);
                var decoded = EntityAttributesS2CPacket.CODEC.decode(buf);
                c.assertTrue(decoded.getEntries().stream().anyMatch(e -> e.attribute().equals(EntityAttributes.GENERIC_GRAVITY)
                        && e.modifiers().stream().anyMatch(m -> m.id().equals(LowGravityChallenge.GRAVITY_MODIFIER))), "Gravity modifier on wire");
                c.assertTrue(decoded.getEntries().stream().anyMatch(e -> e.attribute().equals(EntityAttributes.GENERIC_JUMP_STRENGTH)
                        && e.modifiers().stream().anyMatch(m -> m.id().equals(LowGravityChallenge.JUMP_MODIFIER))), "Jump modifier on wire");
            } finally { buf.release(); }
            s.activate("double_challenge:low_gravity,gravity_ten");
            near(c, -.06 * DRAG, step(p, base, Vec3d.ZERO), "Pair .25 x3 once");
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0));
            near(c, -.03 * DRAG, step(p, base, Vec3d.ZERO), "Pair retains gravity_ten after Slow Falling cap");
            p.removeStatusEffect(StatusEffects.SLOW_FALLING);
            s.activate("gravity_ten"); near(c, -.24 * DRAG, step(p, base, Vec3d.ZERO), "End low leaves gravity_ten");
            s.activate("double_challenge:low_gravity,heavy_body"); reset(p, base, Vec3d.ZERO); p.setOnGround(true); p.jump();
            near(c, .42 * 1.25 * .6, p.getVelocity().y, "Heavy Body composes once");
            s.activate("double_challenge:low_gravity,no_jumping"); reset(p, base, Vec3d.ZERO); p.jump();
            near(c, 0, p.getVelocity().y, "No jumping still cancels");
            s.activate("double_challenge:low_gravity,auto_jump"); reset(p, base, Vec3d.ZERO); p.setOnGround(true);
            com.cursecycle.challenge.impl.AutoJumpChallenge.tick(p.getServer());
            near(c, .42 * 1.25, p.getVelocity().y, "Auto Jump uses tracked impulse");
        }
        c.complete();
    }
}
