package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DamageSlotDropGameTest implements FabricGameTest {
    private static final Set<UUID> CANCELLED = new HashSet<>();
    private static final java.util.Map<UUID, Boolean> NESTED = new java.util.HashMap<>();
    static {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (CANCELLED.contains(entity.getUuid())) return false;
            var sameSource = NESTED.remove(entity.getUuid());
            if (sameSource != null) entity.damage(sameSource ? source : entity.getDamageSources().magic(), 1);
            return true;
        });
    }

    private static ServerPlayerEntity ready(ChallengeScenario s) {
        var p = s.player();
        // ServerPlayerEntity.tick expires login protection; playerTick does not.
        for (int i = 0; i < 61; i++) p.tick();
        var pos = s.context.getAbsolutePos(new net.minecraft.util.math.BlockPos(2, 2, 2));
        p.teleport(s.context.getWorld(), pos.getX() + .5, 180, pos.getZ() + .5, 0, 0);
        p.setNoGravity(true);
        p.getInventory().clear();
        p.getHungerManager().setFoodLevel(18);
        return p;
    }

    private static void hit(ServerPlayerEntity p) {
        p.timeUntilRegen = 0;
        p.setHealth(p.getMaxHealth());
        p.damage(p.getDamageSources().generic(), 1);
    }

    private static List<ItemEntity> drops(ServerPlayerEntity p) {
        return p.getServerWorld().getEntitiesByClass(ItemEntity.class, p.getBoundingBox().expand(4), e -> true);
    }

    private static void discardDrops(ServerPlayerEntity p) { drops(p).forEach(ItemEntity::discard); }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slot_drop_nested")
    public void nestedAcceptedDamageWithSameOrDifferentSource(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s);
            s.activate("damage_slot_drop");
            for (boolean sameSource : new boolean[]{false, true}) {
                p.getInventory().setStack(0, new ItemStack(Items.STONE, 64));
                p.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 3));
                p.timeUntilRegen = 0;
                p.setHealth(20);
                NESTED.put(p.getUuid(), sameSource);
                try { p.damage(p.getDamageSources().generic(), 2); }
                finally { NESTED.remove(p.getUuid()); }
                c.assertEquals(18F, p.getHealth(), "Inner 1 plus accepted outer i-frame difference 1");
                c.assertTrue(p.getInventory().getStack(0).isEmpty() && p.getInventory().getStack(10).isEmpty(),
                        "Each accepted nested invocation drops once; same source=" + sameSource);
                c.assertEquals(2, drops(p).size(), "Two distinct accepted results");
                discardDrops(p);
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slot_drop_slots")
    public void everyMainSlotExcludedStorageAndCurrentStacks(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s);
            p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            p.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
            p.currentScreenHandler.setCursorStack(new ItemStack(Items.EMERALD, 3));
            var chest = net.minecraft.screen.GenericContainerScreenHandler.createGeneric9x3(77, p.getInventory());
            chest.getInventory().setStack(0, new ItemStack(Items.GOLD_INGOT, 9));
            p.currentScreenHandler = chest;
            chest.setCursorStack(new ItemStack(Items.EMERALD, 3));
            s.activate("damage_slot_drop");
            for (int slot = 0; slot < 36; slot++) {
                var stack = new ItemStack(Items.STONE, slot + 1);
                p.getInventory().setStack(slot, stack.copy());
                hit(p);
                var items = drops(p);
                c.assertTrue(p.getInventory().getStack(slot).isEmpty(), "Participating slot " + slot);
                c.assertEquals(1, items.size(), "Exactly one real entity for slot " + slot);
                c.assertTrue(ItemStack.areEqual(stack, items.getFirst().getStack()), "Whole stack preserved " + slot);
                discardDrops(p);
            }
            hit(p);
            c.assertTrue(drops(p).isEmpty(), "Empty main inventory never selects excluded storage");
            c.assertTrue(p.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.DIAMOND_HELMET), "Armor unchanged");
            c.assertTrue(p.getOffHandStack().isOf(Items.SHIELD), "Offhand unchanged");
            c.assertEquals(3, chest.getCursorStack().getCount(), "Cursor unchanged");
            c.assertEquals(9, chest.getInventory().getStack(0).getCount(), "External inventory unchanged");

            // Two differently sized occupied slots: exercise both outcomes through actual damage.
            var seen = new HashSet<Integer>();
            for (int i = 0; i < 128; i++) {
                p.getInventory().setStack(0, new ItemStack(Items.STONE, 64));
                p.getInventory().setStack(35, new ItemStack(Items.DIAMOND));
                hit(p);
                int empty = p.getInventory().getStack(0).isEmpty() ? 0 : 35;
                c.assertTrue(p.getInventory().getStack(empty).isEmpty(), "One occupied slot removed");
                c.assertTrue(!p.getInventory().getStack(empty == 0 ? 35 : 0).isEmpty(), "Other occupied slot retained");
                c.assertEquals(1, drops(p).size(), "No duplicate callbacks");
                seen.add(empty);
                discardDrops(p);
            }
            c.assertEquals(2, seen.size(), "Both slots reachable independent of stack size; uniformity follows bounded nextInt");
            p.currentScreenHandler = p.playerScreenHandler;
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slot_drop_pickup", tickLimit = 100)
    public void fullComponentsAndFortyRealTickPickup(TestContext c) {
        var s = new ChallengeScenario(c);
        try {
            var p = ready(s);
            var nested = new ItemStack(Items.DIAMOND_PICKAXE);
            nested.setDamage(123);
            nested.addEnchantment(c.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENCHANTMENT)
                    .entryOf(net.minecraft.enchantment.Enchantments.UNBREAKING), 3);
            nested.set(DataComponentTypes.CUSTOM_NAME, Text.literal("nested tool"));
            var box = new ItemStack(Items.SHULKER_BOX);
            box.set(DataComponentTypes.CUSTOM_NAME, Text.literal("whole container"));
            box.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(nested, new ItemStack(Items.DIAMOND, 17))));
            p.getInventory().setStack(10, box.copy());
            s.activate("damage_slot_drop");
            c.assertTrue(ItemStack.areEqual(box, p.getInventory().getStack(10)), "Activation causes no drop");
            hit(p);
            var items = drops(p);
            c.assertEquals(1, items.size(), "One complete container");
            var item = items.getFirst();
            c.assertTrue(ItemStack.areEqual(box, item.getStack()), "Nested components/durability/name preserved");
            item.setNoGravity(true);
            item.setVelocity(Vec3d.ZERO);
            item.onPlayerCollision(p);
            c.assertTrue(p.getInventory().getStack(10).isEmpty() && !item.isRemoved(), "Cannot immediately pick up");
            p.setPosition(p.getX() + 20, 180, p.getZ());
            int start = p.getServer().getTicks();
            c.waitAndRun(39, () -> {
                try {
                    c.assertEquals(39, p.getServer().getTicks() - start, "39 real ticks");
                    item.onPlayerCollision(p);
                    c.assertTrue(!item.isRemoved(), "Delay still positive at tick 39");
                } catch (Throwable failure) { item.discard(); s.close(); throw failure; }
            });
            c.waitAndRun(40, () -> {
                try {
                    c.assertEquals(40, p.getServer().getTicks() - start, "40 real ticks");
                    item.onPlayerCollision(p);
                    c.assertTrue(item.isRemoved(), "Vanilla pickup now succeeds");
                    c.assertTrue(ItemStack.areEqual(box, p.getInventory().getStack(0)), "Picked up unchanged stack");
                    s.activate("no_sprint");
                    c.assertTrue(ItemStack.areEqual(box, p.getInventory().getStack(0)) && p.getInventory().getStack(10).isEmpty(), "Ending never restores old slot");
                    c.complete();
                } finally { item.discard(); s.close(); }
            });
        } catch (Throwable failure) { s.close(); throw failure; }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slot_drop_sources")
    public void actualCancellationShieldResistanceSourcesAndMeleeReceipt(TestContext c) {
        boolean pvp = c.getWorld().getServer().isPvpEnabled();
        c.getWorld().getServer().setPvpEnabled(true);
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s);
            var attacker = ready(s);
            attacker.setPosition(p.getX(), p.getY(), p.getZ() - 2);
            p.setYaw(180);
            p.getInventory().setStack(10, new ItemStack(Items.STONE, 64));
            hit(p);
            c.assertEquals(64, p.getInventory().getStack(10).getCount(), "Inactive control");
            s.activate("damage_slot_drop");
            p.setHealth(18);
            c.assertEquals(64, p.getInventory().getStack(10).getCount(), "Direct setter is not damage");
            CANCELLED.add(p.getUuid());
            try { hit(p); } finally { CANCELLED.remove(p.getUuid()); }
            c.assertEquals(20F, p.getHealth(), "ALLOW_DAMAGE cancellation control");
            c.assertEquals(64, p.getInventory().getStack(10).getCount(), "Cancelled damage causes no drop");
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 4));
            hit(p);
            c.assertEquals(20F, p.getHealth(), "Resistance eliminates loss");
            c.assertEquals(64, p.getInventory().getStack(10).getCount(), "No loss causes no drop");
            p.clearStatusEffects();
            p.setInvulnerable(true);
            hit(p);
            c.assertEquals(64, p.getInventory().getStack(10).getCount(), "Invulnerability excluded");
            p.setInvulnerable(false);
            p.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
            p.setCurrentHand(Hand.OFF_HAND);
            for (int i = 0; i < 6; i++) p.playerTick();
            c.assertTrue(p.isBlocking(), "Shield active");
            c.assertTrue(p.shouldDamagePlayer(attacker), "PvP permits the shield test hit");
            p.timeUntilRegen = 0;
            p.damage(p.getDamageSources().playerAttack(attacker), 2);
            c.assertEquals(20F, p.getHealth(), "Real shield block");
            c.assertEquals(64, p.getInventory().getStack(10).getCount(), "Blocked hit creates no drop");
            p.clearActiveItem();
            for (var source : List.of(p.getDamageSources().fall(), p.getDamageSources().onFire(),
                    p.getDamageSources().magic(), p.getDamageSources().drown(), p.getDamageSources().generic())) {
                p.getInventory().setStack(10, new ItemStack(Items.STONE, 64));
                p.timeUntilRegen = 0;
                p.setHealth(20);
                p.damage(source, 1);
                c.assertTrue(p.getHealth() < 20 && p.getInventory().getStack(10).isEmpty(), "Real source " + source.getName());
                c.assertEquals(1, drops(p).size(), "One source result");
                discardDrops(p);
            }
            // Recoil owns the outer receipt, LivingEntity.damage must reuse it.
            s.activate("double_challenge:damage_slot_drop,attack_recoil");
            p.getInventory().setStack(0, new ItemStack(Items.STONE, 64));
            p.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 3));
            p.timeUntilRegen = 0;
            p.setHealth(20);
            attacker.setVelocity(Vec3d.ZERO);
            for (int i = 0; i < 30; i++) attacker.playerTick();
            attacker.attack(p);
            c.assertTrue(p.getHealth() < 20, "Actual primary melee hit");
            c.assertEquals(1, drops(p).size(), "Outer melee and inner damage do not duplicate");
            c.assertTrue(p.getInventory().getStack(0).isEmpty() != p.getInventory().getStack(10).isEmpty(), "Only one of two slots dropped");
            c.assertTrue(attacker.getVelocity().lengthSquared() > 0, "Other recoil mechanic runs");
            discardDrops(p);
            p.getInventory().setStack(0, new ItemStack(Items.STONE, 64));
            p.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 3));
            p.timeUntilRegen = 0;
            p.setHealth(20);
            attacker.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(4);
            for (int i = 0; i < 30; i++) attacker.playerTick();
            attacker.setVelocity(Vec3d.ZERO);
            NESTED.put(p.getUuid(), true);
            try { attacker.attack(p); } finally { NESTED.remove(p.getUuid()); }
            c.assertTrue(p.getHealth() < 19, "Outer melee and inner damage both accepted");
            c.assertEquals(2, drops(p).size(), "Borrowed attack receipt still separates recursive same-source result");
            c.assertTrue(Math.abs(attacker.getVelocity().length() - .6) < .000001, "Exactly one primary recoil");
            discardDrops(p);
        } finally { c.getWorld().getServer().setPvpEnabled(pvp); }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slot_drop_lifecycle")
    public void modesPersonalLifecycleDiskAndIndependentSprint(TestContext c) throws Exception {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s);
            var q = ready(s);
            var server = p.getServer();
            var dispatcher = server.getCommandManager().getDispatcher();
            for (String cmd : new String[]{"fdc list", "fdc activate damage_slot_drop", "fdc active"})
                c.assertTrue(dispatcher.execute(cmd, server.getCommandSource().withLevel(2)) > 0, "OP2 " + cmd);
            var late = ready(s);
            ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(late.networkHandler, null, server);
            for (var player : new ServerPlayerEntity[]{p, q, late}) {
                player.getInventory().setStack(10, new ItemStack(Items.STONE));
                hit(player);
                c.assertTrue(player.getInventory().getStack(10).isEmpty(), "Each player including late join");
                discardDrops(player);
            }
            for (var mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL}) {
                p.changeGameMode(mode);
                p.getInventory().setStack(10, new ItemStack(Items.STONE));
                hit(p);
                c.assertEquals(mode == GameMode.ADVENTURE || mode == GameMode.SURVIVAL,
                        p.getInventory().getStack(10).isEmpty(), "Mode " + mode);
                discardDrops(p);
            }
            var challenge = ChallengeRegistry.get("damage_slot_drop");
            challenge.setEnabled(false);
            try {
                p.getInventory().setStack(10, new ItemStack(Items.STONE));
                hit(p);
                c.assertTrue(!p.getInventory().getStack(10).isEmpty(), "Disabled mechanic");
            } finally { challenge.setEnabled(true); }
            s.state.getActiveChallengeIds().clear();
            s.state.getPersonalActiveChallengeIds(p.getUuid()).add("damage_slot_drop");
            hit(p);
            c.assertTrue(p.getInventory().getStack(10).isEmpty(), "PERSONAL current UUID");
            q.getInventory().setStack(10, new ItemStack(Items.STONE));
            hit(q);
            c.assertTrue(!q.getInventory().getStack(10).isEmpty(), "Other UUID not affected");
            discardDrops(p);
            s.state.getPersonalActiveChallenges().clear();
            s.activate("double_challenge:damage_slot_drop,no_sprint");
            p.setSprinting(true);
            ChallengeRegistry.get("no_sprint").onTick(server, p);
            c.assertTrue(!p.isSprinting(), "Sprint independently blocked");
            p.getInventory().setStack(10, new ItemStack(Items.STONE));
            hit(p);
            discardDrops(p);
            s.activate("no_sprint");
            c.assertTrue(p.getInventory().getStack(10).isEmpty(), "No restoration after end");
            p.getInventory().setStack(10, new ItemStack(Items.STONE));
            hit(p);
            p.setSprinting(true);
            ChallengeRegistry.get("no_sprint").onTick(server, p);
            c.assertTrue(!p.getInventory().getStack(10).isEmpty() && !p.isSprinting(), "Remaining mechanic works alone");
            for (int i = 0; i < 32; i++) {
                s.activate("damage_slot_drop");
                s.activate("damage_slot_drop");
                hit(p);
                c.assertTrue(p.getInventory().getStack(10).isEmpty(), "Repeated activation one drop");
                c.assertEquals(1, drops(p).size(), "Repeated activation no duplicate");
                discardDrops(p);
                s.activate("no_sprint");
                p.getInventory().setStack(10, new ItemStack(Items.STONE));
            }
            s.activate("damage_slot_drop");
            var original = p.getPos();
            p.teleport(server.getWorld(net.minecraft.world.World.NETHER), .5, 180, .5, 0, 0);
            p.onTeleportationDone();
            hit(p);
            c.assertTrue(p.getInventory().getStack(10).isEmpty(), "Nether retains rules");
            discardDrops(p);
            p.teleport(c.getWorld(), original.x, original.y, original.z, 0, 0);
            p.onTeleportationDone();
            p = s.respawn(p, true);
            for (int i = 0; i < 61; i++) p.tick();
            p.getInventory().setStack(10, new ItemStack(Items.STONE));
            hit(p);
            c.assertTrue(p.getInventory().getStack(10).isEmpty(), "Live object replacement retains rules");
            discardDrops(p);

            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 777));
            p.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 7));
            var uuid = p.getUuid();
            disconnect(p);
            var pm = server.getOverworld().getPersistentStateManager();
            pm.save();
            var disk = pm.readNbt(CurseCycleState.STATE_KEY, null, net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager());
            pm.set(CurseCycleState.STATE_KEY, loaded);
            p = s.player(uuid);
            for (int i = 0; i < 61; i++) p.tick();
            hit(p);
            c.assertTrue(p.getInventory().getStack(10).isEmpty(), "Active actual disk reload affects rejoined player");
            discardDrops(p);
            p.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 9));
            int foreignDuration = p.getStatusEffect(StatusEffects.SPEED).getDuration();
            disconnect(p);
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            pm.save();
            disk = pm.readNbt(CurseCycleState.STATE_KEY, null, net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            pm.set(CurseCycleState.STATE_KEY, CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager()));
            p = s.player(uuid);
            c.assertEquals(foreignDuration, p.getStatusEffect(StatusEffects.SPEED).getDuration(), "Foreign effect clock survived offline end");
            for (int i = 0; i < 61; i++) p.tick();
            hit(p);
            c.assertEquals(9, p.getInventory().getStack(10).getCount(), "Offline end actual playerdata does not resume drop");
            c.assertTrue(CurseCycleState.getServerState(server).getChallengeData().isEmpty(), "No own persistent snapshots");
        }
        c.complete();
    }

    private static void disconnect(ServerPlayerEntity p) {
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler, p.getServer());
        p.getServer().getPlayerManager().remove(p);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slot_drop_shuffle", tickLimit = 150)
    public void actualShuffleThenDamageUsesCurrentSlot(TestContext c) {
        var s = new ChallengeScenario(c);
        try {
            var p = ready(s);
            p.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 13));
            s.activate("double_challenge:damage_slot_drop,hotbar_shuffle");
            int start = p.getServer().getTicks();
            c.waitAndRun(101, () -> {
                try {
                    c.assertEquals(101, p.getServer().getTicks() - start, "Real shuffle interval elapsed");
                    c.assertTrue(p.getInventory().getStack(0).isEmpty(), "Actual shuffle moved singleton");
                    hit(p);
                    c.assertEquals(1, drops(p).size(), "Current shuffled slot dropped once");
                    c.assertEquals(13, drops(p).getFirst().getStack().getCount(), "Shuffle and drop preserve count");
                    for (int i = 0; i < 36; i++) c.assertTrue(p.getInventory().getStack(i).isEmpty(), "No stale slot snapshot");
                    s.activate("hotbar_shuffle");
                    c.assertTrue(drops(p).size() == 1, "Ending drop leaves world consequence");
                    c.complete();
                } finally { discardDrops(p); s.close(); }
            });
        } catch (Throwable failure) { s.close(); throw failure; }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slot_drop_teleport")
    public void dropPrecedesTeleportRegardlessOfEncodedOrder(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s);
            var pos = c.getAbsolutePos(new net.minecraft.util.math.BlockPos(2, 2, 2));
            p.teleport(c.getWorld(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
            // Bounded safe pads for the existing reaction; this only checks the pair contract.
            var origin = p.getPos();
            for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++)
                c.getWorld().setBlockState(net.minecraft.util.math.BlockPos.ofFloored(origin).add(x, -1, z), net.minecraft.block.Blocks.STONE.getDefaultState());
            for (String pair : new String[]{"double_challenge:damage_teleport,damage_slot_drop", "double_challenge:damage_slot_drop,damage_teleport"}) {
                p.teleport(c.getWorld(), origin.x, origin.y, origin.z, 0, 0);
                p.onTeleportationDone();
                p.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 7));
                s.activate(pair);
                hit(p);
                c.assertTrue(p.getPos().squaredDistanceTo(origin) > 1, "Other reaction teleports");
                var items = c.getWorld().getEntitiesByClass(ItemEntity.class, new net.minecraft.util.math.Box(net.minecraft.util.math.BlockPos.ofFloored(origin)).expand(1), e -> e.getStack().isOf(Items.DIAMOND));
                c.assertEquals(1, items.size(), "Drop remains at original hit position");
                c.assertEquals(origin, items.getFirst().getPos(), "Exact receipt position before teleport");
                items.forEach(ItemEntity::discard);
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slot_drop_death")
    public void realDamageAbsorptionDeathAndLockedSlots(TestContext context) {
        var rules = context.getWorld().getGameRules();
        boolean keep = rules.getBoolean(GameRules.KEEP_INVENTORY);
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            for (int i = 0; i < 61; i++) player.tick();
            var pos = context.getAbsolutePos(new net.minecraft.util.math.BlockPos(2, 2, 2));
            player.teleport(context.getWorld(), pos.getX()+0.5, pos.getY(), pos.getZ()+0.5, 0, 0);
            var stack = new ItemStack(Items.DIAMOND, 64);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("damage receipt"));
            player.getInventory().setStack(10, stack.copy());
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
            player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1200));
            var area = player.getBoundingBox().expand(3);
            int before = context.getWorld().getEntitiesByClass(ItemEntity.class, area, e -> true).size();
            scenario.activate("damage_slot_drop");
            player.damage(player.getDamageSources().generic(), 2);
            context.assertEquals(20F, player.getHealth(), "Only absorption lost");
            context.assertTrue(player.getInventory().getStack(10).isEmpty(), "Whole occupied slot dropped");
            var drops = context.getWorld().getEntitiesByClass(ItemEntity.class, area, e -> e.getStack().isOf(Items.DIAMOND));
            context.assertEquals(1, drops.size(), "One drop per accepted damage");
            context.assertEquals(64, drops.getFirst().getStack().getCount(), "Whole stack count");
            context.assertTrue(ItemStack.areItemsAndComponentsEqual(stack, drops.getFirst().getStack()), "Components preserved");
            context.assertTrue(player.getStackInHand(Hand.OFF_HAND).isOf(Items.SHIELD), "Offhand untouched");
            player.getInventory().setStack(10, stack.copy());
            player.damage(player.getDamageSources().generic(), 2);
            context.assertEquals(64, player.getInventory().getStack(10).getCount(), "Ignored i-frame creates no drop");
            rules.get(GameRules.KEEP_INVENTORY).set(true, player.getServer());
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().generic(), 100);
            context.assertTrue(!player.isAlive(), "Lethal damage without totem");
            context.assertEquals(64, player.getInventory().getStack(10).getCount(), "Death with keepInventory creates no extra challenge drop");
            player = scenario.respawn(player, false);
            for (int i = 0; i < 61; i++) player.tick();
            player.teleport(context.getWorld(), pos.getX()+0.5, pos.getY(), pos.getZ()+0.5, 0, 0);
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().generic(), 100);
            context.assertTrue(player.isAlive(), "Totem saves");
            context.assertTrue(player.getInventory().getStack(10).isEmpty(), "Totem-survived damage drops stack");
            scenario.activate("double_challenge:damage_slot_drop,inventory_lockdown");
            player.clearStatusEffects();
            player.setHealth(20);
            player.getInventory().setStack(10, stack.copy());
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().generic(), 2);
            context.assertTrue(player.getInventory().getStack(10).isEmpty(), "Closed main slot still participates in automatic drop");
        } finally { rules.get(GameRules.KEEP_INVENTORY).set(keep, context.getWorld().getServer()); }
        context.complete();
    }
}
