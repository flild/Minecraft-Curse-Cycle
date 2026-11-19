package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.PacifistChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PacifistGameTest implements FabricGameTest {
    private static final Map<UUID, List<Float>> RETURNS = new HashMap<>();
    private static final Set<UUID> CANCELLED = new HashSet<>();
    private record Nested(boolean sameSource, boolean cancel) {}
    private static final Map<UUID, Nested> NESTED = new HashMap<>();
    private static final Set<UUID> THROWING = new HashSet<>();
    private static final RuntimeException FAILURE = new RuntimeException("pacifist-test-damage-failure");
    static {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
            if (source.isOf(PacifistChallenge.DAMAGE_TYPE) && RETURNS.containsKey(target.getUuid()))
                RETURNS.get(target.getUuid()).add(amount);
            if (THROWING.contains(target.getUuid())) throw FAILURE;
            var nested = NESTED.remove(target.getUuid());
            if (nested != null) {
                target.damage(nested.sameSource() ? source : target.getDamageSources().magic(), 1);
                return !nested.cancel();
            }
            return !CANCELLED.contains(target.getUuid());
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pacifist_amounts")
    public void actualMeleeArmorAbsorptionAndPreTotemAmounts(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player();
            p.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(4);
            a.s.activate("pacifist");
            for (int kind = 0; kind < 5; kind++) {
                a.charge(p);
                p.setHealth(20);
                var cow = a.cow(p);
                if (kind == 0) {
                    cow.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                    cow.tick();
                    c.assertTrue(cow.getArmor() > 0, "Target armor attribute applied");
                }
                if (kind == 1) cow.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 1));
                if (kind == 2) cow.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0));
                if (kind >= 3) cow.setHealth(.25F);
                if (kind == 4) cow.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                float before = cow.getHealth() + cow.getAbsorptionAmount();
                int count = a.returns(p).size();
                p.attack(cow);
                float expected = kind >= 3 ? .25F : before - cow.getHealth() - cow.getAbsorptionAmount();
                near(c, 20 - p.getHealth(), expected, "Return equals actual reduction, kind=" + kind);
                c.assertTrue(expected > 0, "Damaging attack control");
                if (kind == 0 || kind == 2) c.assertTrue(expected < 4, "Target protection really reduces return");
                c.assertEquals(count + 1, a.returns(p).size(), "One return per primary");
                near(c, a.returns(p).getLast(), expected, "Pre-totem numeric receipt");
                if (kind == 4) c.assertTrue(cow.isAlive() && cow.getOffHandStack().isEmpty(), "Target totem restored HP");
                if (kind == 3) c.assertTrue(!cow.isAlive(), "Lethal target still returns damage");
                cow.discard();
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pacifist_rejections")
    public void shieldsFramesCancellationAndInvulnerability(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player();
            var shield = a.player();
            shield.teleport(c.getWorld(), p.getX(), p.getY(), p.getZ() + 2, 180, 0);
            shield.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
            shield.setCurrentHand(Hand.OFF_HAND);
            for (int i = 0; i < 6; i++) shield.playerTick();
            c.assertTrue(shield.isBlocking(), "Shield control");
            a.s.activate("pacifist");
            p.attack(shield);
            near(c, shield.getHealth(), 20, "Target shield blocks primary");
            c.assertEquals(0, a.returns(p).size(), "No reflected shield block");
            shield.stopUsingItem();
            shield.discard();
            var cow = a.cow(p);
            var source = p.getDamageSources().playerAttack(p);
            cow.damage(source, 4);
            int count = a.returns(p).size();
            cow.damage(source, 4);
            c.assertEquals(count, a.returns(p).size(), "Rejected i-frame hit");
            cow.damage(source, 6);
            near(c, a.returns(p).getLast(), 2, "i-frame excess only");
            cow.discard();
            for (int kind = 0; kind < 3; kind++) {
                cow = a.cow(p);
                if (kind == 0) cow.setInvulnerable(true);
                if (kind == 1) cow.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 4));
                if (kind == 2) CANCELLED.add(cow.getUuid());
                count = a.returns(p).size();
                try { cow.damage(source, 4); } finally { CANCELLED.remove(cow.getUuid()); }
                near(c, cow.getHealth(), 10, "No actual loss");
                c.assertEquals(count, a.returns(p).size(), "No return without reduction");
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pacifist_sweep")
    public void sweepReturnsEachVictimOnceAndKeepsOneRecoil(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player();
            a.s.activate("double_challenge:pacifist,attack_recoil");
            a.s.state.getPersonalActiveChallengeIds(p.getUuid()).add("pacifist");
            p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            a.charge(p);
            p.setOnGround(true);
            var primary = a.cow(p);
            var secondary = a.cow(p);
            secondary.setPosition(p.getX() + .8, p.getY(), p.getZ() + 2);
            p.setVelocity(Vec3d.ZERO);
            p.attack(primary);
            c.assertTrue(secondary.getHealth() < 10, "Actual vanilla sweep");
            near(c, 20 - p.getHealth(), 20 - primary.getHealth() - secondary.getHealth(), "Sum of both victims");
            c.assertEquals(2, a.returns(p).size(), "No duplicate from GLOBAL/PERSONAL or borrowed receipt");
            c.assertTrue(p.getVelocity().squaredDistanceTo(new Vec3d(0, 0, -.6)) < 1E-10,
                    "One recoil after whole attack, got " + p.getVelocity());
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pacifist_projectiles")
    public void actualArrowImpactAndIndirectTraps(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player();
            a.s.activate("pacifist");
            var cow = a.cow(p);
            var arrow = new ArrowEntity(c.getWorld(), p, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            a.entities.add(arrow);
            arrow.setPosition(cow.getX(), cow.getY() + .5, cow.getZ() - 1);
            arrow.setVelocity(0, 0, 2);
            c.getWorld().spawnEntity(arrow);
            arrow.tick();
            float loss = 10 - cow.getHealth();
            c.assertTrue(loss > 0, "Arrow tick really hit target");
            near(c, 20 - p.getHealth(), loss, "Arrow reflects actual loss");
            c.assertEquals(1, a.returns(p).size(), "One return from projectile");
            var other = a.player();
            a.s.state.getActiveChallengeIds().clear();
            a.s.state.getPersonalActiveChallengeIds(p.getUuid()).add("pacifist");
            var otherArrow = new ArrowEntity(c.getWorld(), other, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            for (DamageSource source : List.of(p.getDamageSources().onFire(), p.getDamageSources().lava(),
                    p.getDamageSources().fall(), p.getDamageSources().thorns(p),
                    p.getDamageSources().explosion(p, p), p.getDamageSources().arrow(otherArrow, other))) {
                cow = a.cow(p);
                cow.damage(source, 1);
                near(c, cow.getHealth(), 9, "Indirect/trap still damages target: " + source.getName());
                c.assertEquals(1, a.returns(p).size(), "No return: " + source.getName());
                c.assertEquals(0, a.returns(other).size(), "Personal activation does not leak");
                cow.discard();
            }
            var stand = EntityType.ARMOR_STAND.create(c.getWorld());
            a.entities.add(stand);
            stand.damage(p.getDamageSources().playerAttack(p), 1);
            c.assertEquals(1, a.returns(p).size(), "Armor stands excluded");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pacifist_player_protection")
    public void returnBypassesProtectionButRespectsAbsorptionAndTotem(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player();
            a.s.activate("pacifist");
            var chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
            chest.addEnchantment(c.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENCHANTMENT)
                    .entryOf(net.minecraft.enchantment.Enchantments.PROTECTION), 4);
            p.equipStack(EquipmentSlot.CHEST, chest);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 4));
            p.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
            p.setCurrentHand(Hand.OFF_HAND);
            for (int i = 0; i < 6; i++) p.playerTick();
            c.assertTrue(p.isBlocking(), "Attacker shield control");
            p.timeUntilRegen = 20;
            a.cow(p).damage(p.getDamageSources().playerAttack(p), 3);
            a.cow(p).damage(p.getDamageSources().playerAttack(p), 3);
            near(c, p.getHealth(), 14, "Armor/resistance/shield and return i-frames bypassed");
            p.stopUsingItem();
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0));
            a.cow(p).damage(p.getDamageSources().playerAttack(p), 3);
            near(c, p.getHealth(), 14, "Player absorption pays return first");
            near(c, p.getAbsorptionAmount(), 1, "Exact absorption consumption");
            p.removeStatusEffect(StatusEffects.ABSORPTION);
            p.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            p.setHealth(1);
            a.cow(p).damage(p.getDamageSources().playerAttack(p), 3);
            c.assertTrue(p.isAlive() && p.getOffHandStack().isEmpty(), "Player totem works on return");
            p.removeStatusEffect(StatusEffects.ABSORPTION);
            p.setHealth(1);
            a.cow(p).damage(p.getDamageSources().playerAttack(p), 3);
            c.assertTrue(!p.isAlive(), "Return can kill attacker");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pacifist_nested")
    public void nestedResultsAndExceptionsDoNotLeakOrDoubleCount(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player();
            a.s.activate("pacifist");
            p.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(4);
            for (boolean same : new boolean[]{false, true}) for (boolean cancel : new boolean[]{false, true}) {
                p.setHealth(20);
                a.charge(p);
                var cow = a.cow(p);
                NESTED.put(cow.getUuid(), new Nested(same, cancel));
                p.attack(cow);
                near(c, cow.getHealth(), cancel ? 9 : 6, "Nested invocation control");
                near(c, 20 - p.getHealth(), (same ? 1 : 0) + (cancel ? 0 : 3), "Only direct invocation reductions counted");
                cow.discard();
            }
            var cow = a.cow(p);
            THROWING.add(cow.getUuid());
            try {
                try { p.attack(cow); throw new AssertionError("Expected failure"); }
                catch (RuntimeException failure) { c.assertTrue(failure == FAILURE, "Original exception"); }
            } finally { THROWING.remove(cow.getUuid()); }
            p.setHealth(20);
            a.cow(p).damage(p.getDamageSources().playerAttack(p), 2);
            near(c, p.getHealth(), 18, "Receipt stack recovered after exception");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pacifist_reactions")
    public void globalPvpAndIncomingReactionsRunOnceAtOriginalPosition(TestContext c) {
        try (var a = new Arena(c)) {
            var p = a.player();
            var target = a.player();
            a.s.activate("double_challenge:pacifist,damage_slot_drop");
            // A simultaneous personal reaction also exercises the existing deduplicated delivery path.
            a.s.state.getPersonalActiveChallengeIds(p.getUuid()).add("damage_teleport");
            var origin = p.getPos();
            a.floor(origin);
            for (var player : List.of(p, target)) {
                player.getInventory().setStack(10, new ItemStack(Items.DIAMOND));
                player.getInventory().setStack(11, new ItemStack(Items.DIAMOND));
            }
            target.teleport(c.getWorld(), origin.x, origin.y, origin.z + 2, 0, 0);
            target.damage(p.getDamageSources().playerAttack(p), 2);
            near(c, target.getHealth(), 18, "PvP primary unchanged");
            near(c, p.getHealth(), 18, "Return cannot bounce between global players");
            c.assertEquals(1, a.returns(p).size(), "One return to attacker");
            c.assertEquals(0, a.returns(target).size(), "No recursive return to victim");
            for (var player : List.of(p, target)) {
                int remaining = (player.getInventory().getStack(10).isEmpty() ? 0 : 1)
                        + (player.getInventory().getStack(11).isEmpty() ? 0 : 1);
                c.assertEquals(1, remaining, "Incoming drop dispatched once per player");
            }
            near(c, (float) p.getPos().subtract(origin).horizontalLength(), 5, "Return teleports attacker once");
            var drops = c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                    new net.minecraft.util.math.Box(origin, origin).expand(8), e -> e.getStack().isOf(Items.DIAMOND));
            c.assertEquals(2, drops.size(), "Exactly two drops");
            c.assertTrue(drops.stream().anyMatch(e -> e.getPos().squaredDistanceTo(origin) < 1E-8), "Return drop precedes teleport");
            drops.forEach(Entity::discard);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pacifist_lifecycle", tickLimit = 300)
    public void modesDisableReplacementDimensionsAndSavedActivation(TestContext c) throws Exception {
        try (var a = new Arena(c)) {
            var p = a.player();
            var commands = p.getServer().getCommandManager().getDispatcher();
            commands.execute("fdc activate pacifist", p.getServer().getCommandSource());
            for (var mode : GameMode.values()) {
                p.changeGameMode(mode);
                int before = a.returns(p).size();
                a.cow(p).damage(p.getDamageSources().playerAttack(p), 1);
                c.assertEquals(before + (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE ? 1 : 0),
                        a.returns(p).size(), "Mode " + mode);
            }
            p.changeGameMode(GameMode.SURVIVAL);
            var challenge = ChallengeRegistry.get("pacifist");
            challenge.setEnabled(false);
            int count = a.returns(p).size();
            try { a.cow(p).damage(p.getDamageSources().playerAttack(p), 1); }
            finally { challenge.setEnabled(true); }
            c.assertEquals(count, a.returns(p).size(), "Disabled");
            p.setHealth(0);
            a.cow(p).damage(p.getDamageSources().playerAttack(p), 1);
            c.assertEquals(count, a.returns(p).size(), "Dead attacker");
            p = a.s.respawn(p, false);
            a.ready(p);
            a.cow(p).damage(p.getDamageSources().playerAttack(p), 1);
            near(c, p.getHealth(), 19, "Respawn without stale receipt");
            p = a.s.respawn(p, true);
            a.ready(p);
            var nether = p.getServer().getWorld(net.minecraft.world.World.NETHER);
            p.teleport(nether, .5, 180, .5, 0, 0);
            p.onTeleportationDone();
            a.cow(p).damage(p.getDamageSources().playerAttack(p), 1);
            near(c, p.getHealth(), 19, "New entity/dimension uses current activation");
            var manager = c.getWorld().getPersistentStateManager();
            a.s.state.markDirty();
            manager.save();
            var nbt = manager.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(nbt.getCompound("data"), c.getWorld().getRegistryManager());
            manager.set(CurseCycleState.STATE_KEY, loaded);
            a.cow(p).damage(p.getDamageSources().playerAttack(p), 1);
            near(c, p.getHealth(), 18, "Disk load preserves activation without delayed damage");
            com.cursecycle.state.CycleManager.activateChallenge(p.getServer(), loaded, "no_sprint");
            a.cow(p).damage(p.getDamageSources().playerAttack(p), 1);
            near(c, p.getHealth(), 18, "End immediately stops new returns; lost HP stays lost");
            var late = a.player();
            com.cursecycle.state.CycleManager.activateChallenge(p.getServer(), loaded, "pacifist");
            a.cow(late).damage(late.getDamageSources().playerAttack(late), 1);
            near(c, late.getHealth(), 19, "New connection uses current activation");
            var uuid = late.getUuid();
            late.getServer().getPlayerManager().remove(late);
            late = a.s.player(uuid);
            a.ready(late);
            a.cow(late).damage(late.getDamageSources().playerAttack(late), 1);
            near(c, late.getHealth(), 19, "Reconnect has no stale receipt or accumulated debt");
            commands.execute("fdc reset", p.getServer().getCommandSource());
            a.cow(late).damage(late.getDamageSources().playerAttack(late), 1);
            near(c, late.getHealth(), 19, "Reset stops returns immediately");
        }
        c.complete();
    }

    private static void near(TestContext c, float actual, float expected, String message) {
        c.assertTrue(Math.abs(actual - expected) < 1E-4, message + ": expected " + expected + ", got " + actual);
    }

    private static final class Arena implements AutoCloseable {
        final ChallengeScenario s;
        final List<Entity> entities = new ArrayList<>();
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final boolean pvp;
        Arena(TestContext c) {
            s = new ChallengeScenario(c);
            pvp = c.getWorld().getServer().isPvpEnabled();
            c.getWorld().getServer().setPvpEnabled(true);
        }
        List<Float> returns(ServerPlayerEntity p) { return RETURNS.get(p.getUuid()); }
        ServerPlayerEntity player() {
            var p = s.player();
            ready(p);
            return p;
        }
        void ready(ServerPlayerEntity p) {
            RETURNS.put(p.getUuid(), new ArrayList<>());
            for (int i = 0; i < 61; i++) p.tick();
            p.timeUntilRegen = 0;
            p.setHealth(20);
            charge(p);
            var pos = s.context.getAbsolutePos(new BlockPos(8, 3, 8));
            p.teleport(s.context.getWorld(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
            p.setVelocity(Vec3d.ZERO);
        }
        void charge(ServerPlayerEntity p) {
            for (int i = 0; i < 30; i++) p.playerTick();
            p.setSprinting(false);
            p.setOnGround(false);
            p.fallDistance = 0;
        }
        net.minecraft.entity.passive.CowEntity cow(ServerPlayerEntity p) {
            var cow = EntityType.COW.create(p.getServerWorld());
            cow.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ() + 2, 0, 0);
            cow.setAiDisabled(true);
            entities.add(cow);
            p.getServerWorld().spawnEntity(cow);
            return cow;
        }
        void floor(Vec3d origin) {
            var base = BlockPos.ofFloored(origin);
            for (var pos : BlockPos.iterate(base.add(-8, -1, -8), base.add(8, 3, 8))) {
                blocks.put(pos.toImmutable(), s.context.getWorld().getBlockState(pos));
                s.context.getWorld().setBlockState(pos, (pos.getY() < base.getY() ? Blocks.STONE : Blocks.AIR).getDefaultState());
            }
        }
        @Override public void close() {
            entities.forEach(Entity::discard);
            s.context.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                    new net.minecraft.util.math.Box(s.context.getAbsolutePos(BlockPos.ORIGIN)).expand(24), e -> true).forEach(Entity::discard);
            blocks.forEach((pos, state) -> s.context.getWorld().setBlockState(pos, state));
            s.close();
            s.context.getWorld().getServer().setPvpEnabled(pvp);
            RETURNS.clear();
        }
    }
}
