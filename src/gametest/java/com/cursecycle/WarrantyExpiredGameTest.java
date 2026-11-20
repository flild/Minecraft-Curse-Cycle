package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.WarrantyExpiredChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.concurrent.atomic.AtomicInteger;

public class WarrantyExpiredGameTest implements FabricGameTest {
    private static final String ID = WarrantyExpiredChallenge.ID;

    private static ServerPlayerEntity ready(ChallengeScenario s) {
        var p = s.player();
        for (int i = 0; i < 61; i++) p.tick();
        var pos = s.context.getAbsolutePos(new BlockPos(2, 3, 2));
        p.setPosition(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        p.setNoGravity(true); p.setHealth(20); p.setOnGround(true);
        p.getHungerManager().setFoodLevel(17); p.getHungerManager().setSaturationLevel(0);
        return p;
    }
    private static ItemStack hand(ServerPlayerEntity p, net.minecraft.item.Item item) {
        var stack = new ItemStack(item); p.setStackInHand(Hand.MAIN_HAND, stack); return stack;
    }
    private static void wear(ServerPlayerEntity p, ItemStack stack, int amount) {
        stack.damage(amount, p, EquipmentSlot.MAINHAND);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void realMiningMeleeAndBow(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); var world = c.getWorld(); s.activate(ID);
            var pos = c.getAbsolutePos(new BlockPos(2, 1, 2));
            var original = world.getBlockState(pos);
            try {
                world.setBlockState(pos, Blocks.STONE.getDefaultState());
                var pick = hand(p, Items.DIAMOND_PICKAXE);
                c.assertTrue(p.interactionManager.tryBreakBlock(pos), "Real server mining succeeds");
                c.assertEquals(1, pick.getDamage(), "Mining uses ordinary durability");
                var cow = EntityType.COW.create(world);
                cow.setPosition(p.getX(), p.getY(), p.getZ() + 2); cow.setAiDisabled(true); world.spawnEntity(cow);
                try {
                    var sword = hand(p, Items.DIAMOND_SWORD);
                    for (int i = 0; i < 25; i++) p.playerTick();
                    p.attack(cow);
                    c.assertTrue(cow.getHealth() < 10, "Actual successful melee");
                    c.assertEquals(1, sword.getDamage(), "Sword action uses ordinary durability");
                } finally { cow.discard(); }
                var bow = hand(p, Items.BOW);
                p.getInventory().setStack(9, new ItemStack(Items.ARROW, 2));
                c.assertTrue(bow.use(world, p, Hand.MAIN_HAND).getResult().isAccepted(), "Bow draw starts");
                bow.onStoppedUsing(world, p, bow.getMaxUseTime(p) - 20);
                c.assertEquals(1, bow.getDamage(), "Actual bow release uses ordinary durability");
                c.assertEquals(1, p.getInventory().getStack(9).getCount(), "One arrow consumed");
            } finally {
                world.setBlockState(pos, original);
                for (var e : world.getEntitiesByClass(PersistentProjectileEntity.class, p.getBoundingBox().expand(16), e -> true)) e.discard();
                for (var e : world.getEntitiesByClass(ItemEntity.class, p.getBoundingBox().expand(8), e -> true)) e.discard();
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void realShieldAndAllWornArmor(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); var attacker = EntityType.COW.create(c.getWorld()); s.activate(ID);
            attacker.setPosition(p.getX(), p.getY(), p.getZ() + 2);
            p.setYaw(0);
            var shield = new ItemStack(Items.SHIELD); p.setStackInHand(Hand.OFF_HAND, shield);
            p.setCurrentHand(Hand.OFF_HAND); for (int i = 0; i < 6; i++) p.playerTick();
            c.assertTrue(p.isBlocking(), "Shield is raised");
            p.timeUntilRegen = 0;
            p.damage(p.getDamageSources().mobAttack(attacker), 4);
            c.assertEquals(20F, p.getHealth(), "Actual shield block prevents health loss");
            c.assertEquals(5, shield.getDamage(), "Shield block uses ordinary five durability");
            p.stopUsingItem(); p.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            var items = new net.minecraft.item.Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
            for (int i = 0; i < slots.length; i++) p.equipStack(slots[i], new ItemStack(items[i]));
            p.playerTick(); p.timeUntilRegen = 0;
            c.assertTrue(p.getArmor() > 0, "Vanilla armor attributes applied");
            p.damage(p.getDamageSources().mobAttack(EntityType.ZOMBIE.create(c.getWorld())), 8);
            c.assertTrue(p.getHealth() < 20, "Actual incoming armor hit");
            for (var slot : slots) c.assertEquals(2, p.getEquippedStack(slot).getDamage(), "Armor hit uses ordinary two durability: " + slot);
            c.assertEquals(5, shield.getDamage(), "Unused shield is unaffected by armor hit");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unbreakingKeepsVanillaActionDamage(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); s.activate(ID); var world = c.getWorld();
            for (var item : new net.minecraft.item.Item[]{Items.DIAMOND_PICKAXE, Items.DIAMOND_CHESTPLATE}) {
                var stack = new ItemStack(item);
                stack.addEnchantment(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.UNBREAKING).orElseThrow(), 3);
                int total = 0;
                for (int seed = 0; seed < 32; seed++) {
                    stack.setDamage(0); world.random.setSeed(seed);
                    int expected = EnchantmentHelper.getItemDamage(world, stack, 4);
                    world.random.setSeed(seed); wear(p, stack, 4);
                    c.assertEquals(expected, stack.getDamage(), "Exactly vanilla Unbreaking on four, seed=" + seed + " item=" + item);
                    total += expected;
                }
                c.assertTrue(total > 0 && total < 32 * 4, "Real enchantment protects but does not make item invulnerable");
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mendingAndNegativeDamageRemainVanilla(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); s.activate(ID); var stack = hand(p, Items.DIAMOND_PICKAXE);
            stack.addEnchantment(c.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.MENDING).orElseThrow(), 1);
            stack.setDamage(100);
            var orb = new ExperienceOrbEntity(c.getWorld(), p.getX(), p.getY(), p.getZ(), 7);
            c.getWorld().spawnEntity(orb); p.experiencePickUpDelay = 0;
            try { orb.onPlayerCollision(p); } finally { orb.discard(); }
            c.assertEquals(86, stack.getDamage(), "Real Mending repairs fourteen, not forty-two");
            stack.damage(-2, p.getServerWorld(), p, item -> { throw new AssertionError("Repair must not break"); });
            c.assertEquals(84, stack.getDamage(), "Negative damage remains unmultiplied");
            wear(p, stack, 0); c.assertEquals(84, stack.getDamage(), "Zero damage unchanged");
            wear(p, stack, 2); c.assertEquals(86, stack.getDamage(), "Ordinary wear resumes after repair");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unbreakableNonDamageableAndSingleBreak(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s); s.activate(ID);
            var unbreakable = hand(p, Items.DIAMOND_PICKAXE);
            unbreakable.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(true));
            wear(p, unbreakable, 10); c.assertEquals(0, unbreakable.getDamage(), "Unbreakable remains intact");
            var stone = new ItemStack(Items.STONE, 12); wear(p, stone, 100);
            c.assertEquals(12, stone.getCount(), "Non-damageable cargo unaffected");
            var breaking = new ItemStack(Items.DIAMOND_PICKAXE); breaking.setDamage(breaking.getMaxDamage() - 1);
            var callbacks = new AtomicInteger();
            breaking.damage(1, p.getServerWorld(), p, item -> {
                c.assertTrue(item == Items.DIAMOND_PICKAXE, "Original broken item supplied"); callbacks.incrementAndGet();
            });
            c.assertTrue(breaking.isEmpty(), "Ordinary wear actually breaks item");
            c.assertEquals(1, callbacks.get(), "Exactly one break callback");
            var sword = hand(p, Items.DIAMOND_SWORD); sword.setDamage(sword.getMaxDamage() - 1);
            int before = p.getStatHandler().getStat(Stats.BROKEN.getOrCreateStat(Items.DIAMOND_SWORD));
            wear(p, sword, 1);
            c.assertTrue(p.getMainHandStack().isEmpty(), "Equipment overload removes broken stack");
            c.assertEquals(before + 1, p.getStatHandler().getStat(Stats.BROKEN.getOrCreateStat(Items.DIAMOND_SWORD)), "One vanilla break statistic");
        }
        c.complete();
    }

    private static void sample(ServerPlayerEntity p) {
        ChallengeRegistry.get(ID).onTick(p.getServer(), p);
    }
    private static void later(TestContext c, ChallengeScenario s, int tick, Runnable action) {
        c.runAtTick(tick, () -> {
            try { action.run(); }
            catch (Throwable failure) { s.close(); throw failure; }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "warranty_passive", tickLimit = 130)
    public void realTicksWearEquippedItemsButNotInventoryOrGround(TestContext c) {
        var s = new ChallengeScenario(c); var p = ready(s);
        var tool = hand(p, Items.DIAMOND_PICKAXE);
        var shield = new ItemStack(Items.SHIELD); p.setStackInHand(Hand.OFF_HAND, shield);
        var spare = new ItemStack(Items.DIAMOND_SWORD); p.getInventory().setStack(9, spare);
        var armor = new ItemStack[]{new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)};
        var slots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < slots.length; i++) p.equipStack(slots[i], armor[i]);
        var ground = new ItemEntity(c.getWorld(), p.getX() + 3, p.getY(), p.getZ(), new ItemStack(Items.DIAMOND_AXE));
        ground.setNeverDespawn(); ground.setPickupDelayInfinite(); ground.setNoGravity(true); c.getWorld().spawnEntity(ground);
        s.activate(ID); sample(p);
        later(c, s, 19, () -> {
            for (var part : armor) c.assertEquals(0, part.getDamage(), "Full armor interval before wear");
            c.assertEquals(0, tool.getDamage(), "Hands get grace");
        });
        later(c, s, 21, () -> {
            for (var part : armor) c.assertEquals(1, part.getDamage(), "Idle worn armor loses one per second");
            c.assertEquals(0, shield.getDamage(), "Offhand gets the same grace");
        });
        later(c, s, 39, () -> c.assertEquals(0, tool.getDamage(), "Two full seconds before first hand wear"));
        later(c, s, 41, () -> {
            c.assertEquals(1, tool.getDamage(), "Main hand wears at forty ticks");
            c.assertEquals(1, shield.getDamage(), "Offhand wears independently");
            for (var part : armor) c.assertEquals(2, part.getDamage(), "All four armor slots wear");
        });
        later(c, s, 45, () -> {
            p.setStackInHand(Hand.MAIN_HAND, spare); p.getInventory().setStack(9, tool);
            p.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            for (int i = 0; i < slots.length; i++) {
                p.equipStack(slots[i], ItemStack.EMPTY); p.getInventory().setStack(10 + i, armor[i]);
            }
            sample(p);
        });
        later(c, s, 70, () -> {
            c.assertEquals(0, spare.getDamage(), "New stack gets its own grace");
            c.assertEquals(1, tool.getDamage(), "Stored tool stops wearing");
            for (var part : armor) c.assertEquals(2, part.getDamage(), "Removed armor stops wearing");
        });
        later(c, s, 86, () -> {
            c.assertEquals(1, spare.getDamage(), "Held weapon wears after grace");
            c.assertEquals(0, ground.getStack().getDamage(), "Ground item unaffected");
            ground.discard(); s.activate("test_easy");
        });
        later(c, s, 115, () -> {
            try {
                c.assertEquals(1, spare.getDamage(), "Stop freezes future wear without refund"); c.complete();
            } finally { ground.discard(); s.close(); }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "warranty_grace", tickLimit = 130)
    public void quickMiningAndReequippingResetOnlyHandGrace(TestContext c) {
        var s = new ChallengeScenario(c); var p = ready(s); var pick = hand(p, Items.DIAMOND_PICKAXE);
        s.activate(ID); sample(p);
        later(c, s, 1, () -> {
            var pos = c.getAbsolutePos(new BlockPos(2, 1, 2)); var old = c.getWorld().getBlockState(pos);
            try {
                c.getWorld().setBlockState(pos, Blocks.STONE.getDefaultState());
                c.assertTrue(p.interactionManager.tryBreakBlock(pos), "Quick real mining succeeds");
                c.assertEquals(1, pick.getDamage(), "Only block mining costs durability");
            } finally {
                c.getWorld().setBlockState(pos, old);
                for (var e : c.getWorld().getEntitiesByClass(ItemEntity.class, p.getBoundingBox().expand(8), e -> true)) e.discard();
            }
            p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY); p.getInventory().setStack(9, pick); sample(p);
        });
        later(c, s, 45, () -> c.assertEquals(1, pick.getDamage(), "Putting away avoids the pending idle loss"));
        later(c, s, 46, () -> {
            p.getInventory().setStack(9, ItemStack.EMPTY); p.setStackInHand(Hand.MAIN_HAND, pick); sample(p);
        });
        later(c, s, 75, () -> {
            c.assertEquals(1, pick.getDamage(), "Same item gets a new grace after being put away");
            wear(p, pick, 1); c.assertEquals(2, pick.getDamage(), "Actions stay vanilla during grace");
        });
        later(c, s, 87, () -> {
            c.assertEquals(3, pick.getDamage(), "Changing durability did not renew the grace");
            sample(p); sample(p); c.assertEquals(3, pick.getDamage(), "Repeated calls in same tick never multiply wear");
        });
        later(c, s, 107, () -> {
            try { c.assertEquals(4, pick.getDamage(), "Subsequent idle loss every twenty ticks"); c.complete(); }
            finally { s.close(); }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "warranty_modes", tickLimit = 180)
    public void personalDoubleModesDisabledAndReset(TestContext c) {
        var s = new ChallengeScenario(c); var p = ready(s); var q = ready(s);
        var pick = hand(p, Items.DIAMOND_PICKAXE); var other = hand(q, Items.DIAMOND_PICKAXE);
        s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID); sample(p);
        later(c, s, 41, () -> {
            c.assertEquals(1, pick.getDamage(), "Personal owner wears"); c.assertEquals(0, other.getDamage(), "Unassigned player unaffected");
            s.activate("double_challenge:warranty_expired,no_sprint");
            s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID); sample(p); sample(q);
        });
        later(c, s, 82, () -> {
            c.assertEquals(2, pick.getDamage(), "Global plus personal wears once"); c.assertEquals(1, other.getDamage(), "Global affects other owner");
            p.changeGameMode(GameMode.CREATIVE); q.changeGameMode(GameMode.SPECTATOR); sample(p); sample(q);
        });
        later(c, s, 110, () -> {
            c.assertEquals(2, pick.getDamage(), "Creative unaffected"); c.assertEquals(1, other.getDamage(), "Spectator unaffected");
            p.changeGameMode(GameMode.ADVENTURE); sample(p);
        });
        later(c, s, 140, () -> {
            c.assertEquals(2, pick.getDamage(), "Adventure starts with new grace");
            ChallengeRegistry.get(ID).setEnabled(false);
        });
        later(c, s, 160, () -> {
            c.assertEquals(2, pick.getDamage(), "Disabled timer does not run");
            ChallengeRegistry.get(ID).setEnabled(true); sample(p);
            s.state.reset();
        });
        later(c, s, 175, () -> {
            try {
                c.assertEquals(2, pick.getDamage(), "Reset stops timer without refund");
                var decoded = ItemStack.fromNbt(c.getWorld().getRegistryManager(), pick.encode(c.getWorld().getRegistryManager())).orElseThrow();
                c.assertEquals(2, decoded.getDamage(), "Lost durability persists in item NBT"); c.complete();
            } finally { ChallengeRegistry.get(ID).setEnabled(true); s.close(); }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "warranty_enchant_break", tickLimit = 90)
    public void passiveWearUsesUnbreakingAndVanillaBreaks(TestContext c) {
        var s = new ChallengeScenario(c); var p = ready(s);
        var pick = hand(p, Items.DIAMOND_PICKAXE);
        pick.addEnchantment(c.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.UNBREAKING).orElseThrow(), 3);
        var shield = new ItemStack(Items.SHIELD); shield.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(true));
        p.setStackInHand(Hand.OFF_HAND, shield);
        var armor = new ItemStack(Items.DIAMOND_CHESTPLATE); armor.setDamage(armor.getMaxDamage() - 1);
        p.equipStack(EquipmentSlot.CHEST, armor);
        int before = p.getStatHandler().getStat(Stats.BROKEN.getOrCreateStat(Items.DIAMOND_CHESTPLATE));
        s.activate(ID); sample(p);
        later(c, s, 21, () -> {
            c.assertTrue(p.getEquippedStack(EquipmentSlot.CHEST).isEmpty(), "Idle wear breaks and removes armor");
            c.assertEquals(before + 1, p.getStatHandler().getStat(Stats.BROKEN.getOrCreateStat(Items.DIAMOND_CHESTPLATE)), "Single vanilla break statistic");
        });
        later(c, s, 81, () -> {
            try {
                c.assertTrue(pick.getDamage() >= 0 && pick.getDamage() <= 3, "Unbreaking passive wear remains bounded by three losses");
                c.assertEquals(0, shield.getDamage(), "Unbreakable offhand never wears");
                c.assertEquals(before + 1, p.getStatHandler().getStat(Stats.BROKEN.getOrCreateStat(Items.DIAMOND_CHESTPLATE)), "Empty slot does not repeat break");
                c.complete();
            } finally { s.close(); }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "warranty_lifecycle", tickLimit = 160)
    public void reloadDeathRespawnDimensionAndReconnectRestartGrace(TestContext c) {
        var s = new ChallengeScenario(c); var first = ready(s);
        var player = new ServerPlayerEntity[]{first};
        var tool = new ItemStack[]{hand(first, Items.DIAMOND_PICKAXE)};
        s.activate(ID); sample(first);
        later(c, s, 41, () -> {
            c.assertEquals(1, tool[0].getDamage(), "First session wears normally");
            var saved = s.state.writeNbt(new NbtCompound(), c.getWorld().getRegistryManager());
            c.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY,
                    CurseCycleState.createFromNbt(saved, c.getWorld().getRegistryManager()));
            sample(first);
        });
        later(c, s, 70, () -> c.assertEquals(1, tool[0].getDamage(), "Loaded state has fresh grace and keeps old damage"));
        later(c, s, 82, () -> {
            c.assertEquals(2, tool[0].getDamage(), "Reloaded timer runs after full grace");
            first.kill(); sample(first);
            player[0] = s.respawn(first, false); player[0].setNoGravity(true); player[0].setHealth(20);
            tool[0] = hand(player[0], Items.DIAMOND_PICKAXE); sample(player[0]);
        });
        later(c, s, 100, () -> {
            c.assertEquals(0, tool[0].getDamage(), "Respawn does not inherit the old due time");
            var p = player[0]; var origin = p.getPos(); var nether = p.getServer().getWorld(World.NETHER);
            p.teleport(nether, origin.x, 100, origin.z, 0, 0); sample(p);
            p.teleport(c.getWorld(), origin.x, origin.y, origin.z, 0, 0); sample(p);
        });
        later(c, s, 110, () -> {
            var p = player[0]; var uuid = p.getUuid();
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler, p.getServer());
            p.getServer().getPlayerManager().remove(p);
            player[0] = s.player(uuid); player[0].setNoGravity(true);
            tool[0] = hand(player[0], Items.DIAMOND_PICKAXE); sample(player[0]);
        });
        later(c, s, 149, () -> c.assertEquals(0, tool[0].getDamage(), "Reconnect has its own full grace"));
        later(c, s, 151, () -> {
            try {
                c.assertEquals(1, tool[0].getDamage(), "Reconnected timer becomes active normally"); c.complete();
            } finally { s.close(); }
        });
    }
}
