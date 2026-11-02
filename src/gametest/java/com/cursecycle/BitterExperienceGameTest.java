package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.BitterExperienceChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BitterExperienceGameTest implements FabricGameTest {
    private record Request(float damage, int xp, int toolDamage) {}
    private static final Map<UUID, List<Request>> REQUESTS = new HashMap<>();
    private static final Map<UUID, Runnable> NESTED = new HashMap<>();
    private static final Set<UUID> CANCELLED = new HashSet<>();
    static {
        // Observe actual requests before mitigation/i-frames without replacing damage.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player && source.isOf(BitterExperienceChallenge.DAMAGE_TYPE)) {
                var requests = REQUESTS.get(player.getUuid());
                if (requests != null) requests.add(new Request(amount, player.totalExperience,
                        player.getMainHandStack().getDamage()));
            }
            var nested = NESTED.remove(entity.getUuid());
            if (nested != null) nested.run();
            return !CANCELLED.contains(entity.getUuid());
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bitter_experience", tickLimit = 300)
    public void consumedPortionsMendingAndVanillaDamage(TestContext context) throws Exception {
        try (var scenario = new ChallengeScenario(context)) {
            var player = ready(scenario);
            var requests = new ArrayList<Request>();
            REQUESTS.put(player.getUuid(), requests);
            try {
                pickup(player, 1);
                health(context, player, 20, "Inactive pickup is unchanged");
                var server = player.getServer();
                var dispatcher = server.getCommandManager().getDispatcher();
                var source = server.getCommandSource().withLevel(2);
                dispatcher.execute("fdc list", source);
                dispatcher.execute("fdc activate bitter_experience", source);
                dispatcher.execute("fdc active", source);
                health(context, player, 20, "Activation does not damage existing XP");
                context.assertTrue(scenario.state.getActiveChallengeIds().contains("bitter_experience"), "Command activation");

                for (int value : new int[]{1, 7, 2477}) {
                    player.timeUntilRegen = 0;
                    float before = player.getHealth();
                    int xp = player.totalExperience;
                    pickup(player, value);
                    health(context, player, before - 1, "One damage, independent of XP value");
                    context.assertEquals(xp + value, player.totalExperience, "All XP granted once");
                    context.assertEquals(xp + value, requests.getLast().xp(), "XP granted before damage request");
                }
                context.assertEquals(3, requests.size(), "Exactly one request per successful pickup");
                context.assertTrue(requests.stream().allMatch(r -> r.damage() == 1F), "Every request is one health unit");
                int count = requests.size();
                float hp = player.getHealth();
                dispatcher.execute("experience add " + player.getName().getString() + " 12 points", source);
                player.addExperienceLevels(-1);
                health(context, player, hp, "Command and level spending cause no damage");
                context.assertEquals(count, requests.size(), "No request from direct XP or level spending");

                var blocked = orb(player, 7, 1);
                int xp = player.totalExperience;
                player.experiencePickUpDelay = 2;
                blocked.onPlayerCollision(player);
                context.assertEquals(1, portions(blocked), "Cooldown does not consume portion");
                context.assertEquals(xp, player.totalExperience, "Cooldown grants no XP");
                context.assertEquals(count, requests.size(), "Failed pickup does not request damage");
                blocked.discard();

                var merged = orb(player, 7, 3);
                player.timeUntilRegen = 0;
                hp = player.getHealth();
                for (int remaining = 2; remaining >= 0; remaining--) {
                    player.experiencePickUpDelay = 0; // Simulate expiration of the vanilla two-tick pickup delay only.
                    merged.onPlayerCollision(player);
                    context.assertEquals(remaining, portions(merged), "Only one merged portion consumed");
                }
                context.assertTrue(merged.isRemoved(), "Last portion discards orb");
                context.assertEquals(xp + 21, player.totalExperience, "Merged XP is not duplicated");
                context.assertEquals(count + 3, requests.size(), "Each merged portion requests damage during i-frames");
                health(context, player, hp - 1, "Vanilla i-frames block subsequent equal requests");
                context.assertEquals(20, player.timeUntilRegen, "Damage does not clear vanilla i-frames");

                var tool = new ItemStack(Items.DIAMOND_PICKAXE);
                tool.addEnchantment(player.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.MENDING), 1);
                tool.setDamage(100);
                player.setStackInHand(Hand.MAIN_HAND, tool);
                player.timeUntilRegen = 0;
                xp = player.totalExperience;
                count = requests.size();
                pickup(player, 7);
                context.assertEquals(86, tool.getDamage(), "Mending repairs once");
                context.assertEquals(xp, player.totalExperience, "All XP consumed by Mending");
                context.assertEquals(count + 1, requests.size(), "Mending-only pickup still requests damage");
                context.assertEquals(86, requests.getLast().toolDamage(), "Repair completes before damage");
                tool.setDamage(10);
                player.timeUntilRegen = 0;
                pickup(player, 7);
                context.assertEquals(0, tool.getDamage(), "Partial Mending finishes repair");
                context.assertEquals(xp + 2, player.totalExperience, "Only the remaining XP is awarded");
                player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);

                player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                player.playerTick();
                context.assertTrue(player.getArmor() > 0, "Armor control equipped");
                player.setHealth(20);
                player.timeUntilRegen = 0;
                pickup(player, 1);
                health(context, player, 19, "Armor does not reduce damage");
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0));
                player.timeUntilRegen = 0;
                pickup(player, 1);
                health(context, player, 18.2F, "Resistance reduces damage normally");
                player.clearStatusEffects();
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0));
                player.setAbsorptionAmount(2);
                context.assertEquals(2F, player.getAbsorptionAmount(), "Absorption control exists before pickup");
                player.timeUntilRegen = 0;
                hp = player.getHealth();
                pickup(player, 1);
                health(context, player, hp, "Absorption protects health");
                context.assertEquals(1F, player.getAbsorptionAmount(), "One absorption consumed");
                player.setAbsorptionAmount(0);

                var type = player.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BitterExperienceChallenge.DAMAGE_TYPE);
                context.assertTrue(type.isIn(DamageTypeTags.BYPASSES_ARMOR), "Source bypasses armor");
                context.assertTrue(!type.isIn(DamageTypeTags.BYPASSES_COOLDOWN)
                        && !type.isIn(DamageTypeTags.BYPASSES_RESISTANCE)
                        && !type.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY), "Vanilla protections retained");
                player.setHealth(1);
                player.timeUntilRegen = 0;
                player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                pickup(player, 1);
                context.assertTrue(player.isAlive() && player.getOffHandStack().isEmpty(), "Totem saves lethal pickup");
                player.clearStatusEffects();
                player.setAbsorptionAmount(0);
                player.setHealth(1);
                player.timeUntilRegen = 0;
                pickup(player, 1);
                context.assertTrue(!player.isAlive(), "Lethal pickup without totem remains lethal");
                count = requests.size();
                pickup(player, 1);
                context.assertEquals(count, requests.size(), "Dead player causes no further damage request");
                context.assertTrue(requests.stream().allMatch(r -> r.damage() == 1F), "Every observed pickup requests exactly one damage");
            } finally {
                REQUESTS.remove(player.getUuid());
            }
        }
        context.waitAndRun(5, context::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bitter_experience_nested")
    public void nestedPickupsCancelledDamageAndRemovedOrb(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s);
            s.activate("double_challenge:bitter_experience,damage_slot_drop");
            var requests = new ArrayList<Request>();
            REQUESTS.put(p.getUuid(), requests);
            try {
                p.getInventory().clear();
                p.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 3));
                var merged = orb(p, 7, 2);
                int xp = p.totalExperience;
                NESTED.put(p.getUuid(), () -> {
                    // A distinct portion, consumed reentrantly while the first request is pending.
                    p.experiencePickUpDelay = 0;
                    merged.onPlayerCollision(p);
                });
                p.experiencePickUpDelay = 0;
                merged.onPlayerCollision(p);
                c.assertEquals(2, requests.size(), "Nested consumed portions each request once");
                c.assertEquals(xp + 7, requests.getFirst().xp(), "Outer XP precedes outer request");
                c.assertEquals(xp + 14, requests.getLast().xp(), "Inner XP precedes inner request");
                c.assertEquals(xp + 14, p.totalExperience, "Nested XP distributed once per portion");
                health(c, p, 19, "Nested equal requests retain i-frames");
                c.assertEquals(0, portions(merged), "Both portions consumed");
                c.assertTrue(merged.isRemoved(), "Merged orb exhausted");
                var drops = p.getServerWorld().getEntitiesByClass(ItemEntity.class, p.getBoundingBox().expand(2), e -> e.getStack().isOf(Items.DIAMOND));
                c.assertEquals(1, drops.size(), "Only accepted inner damage drops once");
                c.assertEquals(3, drops.getFirst().getStack().getCount(), "Entire stack retained");
                drops.forEach(ItemEntity::discard);
                p.experiencePickUpDelay = 0;
                merged.onPlayerCollision(p);
                c.assertEquals(2, requests.size(), "Stale removed orb cannot request again");

                p.getInventory().setStack(10, new ItemStack(Items.DIAMOND));
                p.timeUntilRegen = 0;
                CANCELLED.add(p.getUuid());
                xp = p.totalExperience;
                pickup(p, 11);
                c.assertEquals(3, requests.size(), "Cancelled damage still has exactly one request");
                c.assertEquals(xp + 11, p.totalExperience, "Cancellation does not undo vanilla XP");
                health(c, p, 19, "Cancelled request does no damage");
                c.assertEquals(1, p.getInventory().getStack(10).getCount(), "Cancelled request does not drop");
                CANCELLED.remove(p.getUuid());

                // Orb damage nested inside an unrelated, larger damage request: two real results.
                p.timeUntilRegen = 0;
                p.getInventory().setStack(11, new ItemStack(Items.DIAMOND));
                NESTED.put(p.getUuid(), () -> pickup(p, 1));
                p.damage(p.getDamageSources().generic(), 2);
                health(c, p, 17, "Nested pickup and remaining outer damage retain vanilla reduction");
                c.assertEquals(4, requests.size(), "Generic damage itself never becomes an XP request");
                c.assertTrue(p.getInventory().getStack(10).isEmpty() && p.getInventory().getStack(11).isEmpty(),
                        "Both accepted nested damage results react independently");
                p.getServerWorld().getEntitiesByClass(ItemEntity.class, p.getBoundingBox().expand(2), e -> e.getStack().isOf(Items.DIAMOND))
                        .forEach(ItemEntity::discard);

                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 4));
                p.getInventory().setStack(10, new ItemStack(Items.DIAMOND));
                p.timeUntilRegen = 0;
                pickup(p, 1);
                health(c, p, 17, "Resistance V fully blocks actual damage");
                c.assertEquals(5, requests.size(), "Fully mitigated pickup still requests once");
                c.assertEquals(1, p.getInventory().getStack(10).getCount(), "No accepted loss means no reaction");
            } finally {
                REQUESTS.remove(p.getUuid());
                NESTED.remove(p.getUuid());
                CANCELLED.remove(p.getUuid());
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bitter_experience_cadence", tickLimit = 100)
    public void realServerTicksKeepVanillaPickupDelayAndIFrames(TestContext c) {
        var s = new ChallengeScenario(c);
        var p = ready(s);
        p.setNoGravity(true);
        p.getHungerManager().setFoodLevel(17); // Exclude vanilla natural regeneration from the damage measurement.
        s.activate("bitter_experience");
        var requests = new ArrayList<Request>();
        REQUESTS.put(p.getUuid(), requests);
        // Keep this entity unspawned so the fixture alone controls collision attempts;
        // the actual server ticks advance the player's vanilla cooldowns, without resets.
        var merged = new ExperienceOrbEntity(p.getWorld(), p.getX(), p.getY(), p.getZ(), 7);
        var nbt = new NbtCompound();
        merged.writeCustomDataToNbt(nbt);
        nbt.putInt("Count", 3);
        merged.readCustomDataFromNbt(nbt);
        int xp = p.totalExperience;
        long[] start = {-1};
        boolean[] closed = {false};
        int[] steps = {0};
        p.experiencePickUpDelay = 0;
        c.runAtEveryTick(() -> {
                if (closed[0] || c.getTick() < 1) return;
                try {
                    if (start[0] < 0) start[0] = p.getServerWorld().getTime();
                    int step = (int) (p.getServerWorld().getTime() - start[0]);
                    c.assertEquals(steps[0]++, step, "One callback per consecutive real server tick");
                    // The server drives tick(), but EmbeddedChannel does not drive the
                    // play-network playerTick(). Advance that vanilla path once here.
                    p.playerTick();
                    if (step <= 4) merged.onPlayerCollision(p);
                    if (step <= 4) c.assertEquals(20 - step, p.timeUntilRegen, "Vanilla i-frame countdown");
                    if (step == 0 || step == 1) c.assertEquals(1, requests.size(), "Vanilla pickup delay rejects adjacent attempt");
                    if (step == 2 || step == 3) c.assertEquals(2, requests.size(), "Second portion after two ticks only");
                    if (step == 4) {
                        c.assertEquals(3, requests.size(), "Three consumed portions in five collision attempts");
                        c.assertEquals(xp + 21, p.totalExperience, "All merged XP once");
                        health(c, p, 19, "i-frames suppress second and third actual damage");
                    }
                    if (step == 25) {
                        c.assertEquals(0, p.timeUntilRegen, "i-frames naturally expired");
                        float before = p.getHealth();
                        health(c, p, 19, "No natural regeneration in this control");
                        pickup(p, 1);
                        c.assertEquals(4, requests.size(), "New pickup after natural i-frame expiry");
                        health(c, p, before - 1, "Separated pickup deals one more damage");
                        REQUESTS.remove(p.getUuid());
                        closed[0] = true;
                        s.close();
                        c.complete();
                    }
                } catch (Throwable failure) {
                    REQUESTS.remove(p.getUuid());
                    closed[0] = true;
                    s.close();
                    throw failure;
                }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bitter_experience_disk", tickLimit = 300)
    public void repeatedActivationsAndActualPlayerDiskReload(TestContext c) throws Exception {
        try (var s = new ChallengeScenario(c)) {
            var p = ready(s);
            var server = p.getServer();
            var pm = server.getOverworld().getPersistentStateManager();
            for (int i = 0; i < 32; i++) {
                s.activate("bitter_experience");
                long serial = s.state.getActivationSerial();
                s.activate("double_challenge:bitter_experience,bitter_experience");
                c.assertTrue(s.state.getActivationSerial() > serial, "Reactivation changes serial");
                s.state.getPersonalActiveChallengeIds(p.getUuid()).add("bitter_experience");
                p.setHealth(20);
                p.timeUntilRegen = 0;
                int xp = p.totalExperience;
                pickup(p, 1);
                health(c, p, 19, "Overlapping scopes and repeated IDs still one result");
                c.assertEquals(xp + 1, p.totalExperience, "No duplicate XP on reactivation");
                s.state.getPersonalActiveChallenges().clear();
                s.activate("no_sprint");
                p.timeUntilRegen = 0;
                pickup(p, 1);
                health(c, p, 19, "End never heals or damages on future pickup");
            }
            s.activate("bitter_experience");
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 777));
            p.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 7));
            var uuid = p.getUuid();
            int xp = p.totalExperience;
            disconnect(p);
            pm.save();
            var disk = pm.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager());
            pm.set(CurseCycleState.STATE_KEY, loaded);
            p = s.player(uuid);
            c.assertEquals(xp, p.totalExperience, "Actual playerdata retains XP");
            health(c, p, 19, "Active reconnect does not heal or replay damage");
            expireLoginProtection(p);
            pickup(p, 7);
            health(c, p, 18, "Active disk state applies only to new pickup");
            c.assertEquals(xp + 7, p.totalExperience, "New XP granted once after disk reload");
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 777));
            disconnect(p);
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            pm.save();
            disk = pm.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            pm.set(CurseCycleState.STATE_KEY, CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager()));
            p = s.player(uuid);
            c.assertEquals(777, p.getStatusEffect(StatusEffects.SPEED).getDuration(), "Foreign effect unchanged at offline end");
            c.assertEquals(7, p.getInventory().getStack(10).getCount(), "Foreign inventory unchanged");
            health(c, p, 18, "Ended reconnect does not restore health snapshot");
            expireLoginProtection(p);
            pickup(p, 7);
            health(c, p, 18, "Ended disk state never restores pickup damage");
            c.assertEquals(xp + 14, p.totalExperience, "Ordinary XP still granted");
            p.setSprinting(true);
            c.assertTrue(!p.isSprinting(), "No sprint still active after offline end");
            c.assertTrue(CurseCycleState.getServerState(server).getChallengeData().isEmpty(), "No custom pickup state persists");
        }
        c.complete();
    }

    private static void disconnect(ServerPlayerEntity p) {
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler, p.getServer());
        p.getServer().getPlayerManager().remove(p);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bitter_experience_lifecycle", tickLimit = 300)
    public void activityPersistenceAndDamageReactions(TestContext context) throws Exception {
        try (var scenario = new ChallengeScenario(context)) {
            var player = ready(scenario);
            scenario.activate("double_challenge:bitter_experience,no_sprint");
            pickup(player, 1);
            health(context, player, 19, "Double challenge works solo");
            player.setSprinting(true);
            context.assertTrue(!player.isSprinting(), "No sprint remains active");
            for (var mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL}) {
                player.changeGameMode(mode);
                player.timeUntilRegen = 0;
                float before = player.getHealth();
                pickup(player, 1);
                health(context, player, before - (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE ? 1 : 0), "Mode " + mode);
            }
            var challenge = ChallengeRegistry.get("bitter_experience");
            challenge.setEnabled(false);
            try {
                player.timeUntilRegen = 0;
                pickup(player, 1);
                health(context, player, 17, "Disabled challenge does not intervene outside i-frames");
            } finally {
                challenge.setEnabled(true);
            }

            var late = ready(scenario);
            pickup(late, 1);
            health(context, late, 19, "Late join independently affected");
            scenario.state.getActiveChallengeIds().clear();
            scenario.state.getPersonalActiveChallengeIds(player.getUuid()).add("bitter_experience");
            player.timeUntilRegen = 0;
            pickup(player, 1);
            health(context, player, 16, "Personal activation works");
            late.timeUntilRegen = 0;
            pickup(late, 1);
            health(context, late, 19, "Personal activation does not leak to another player");
            scenario.state.getPersonalActiveChallengeIds(player.getUuid()).clear();
            scenario.activate("bitter_experience");
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().generic(), 100);
            context.assertTrue(!player.isAlive(), "Death control");
            player = scenario.respawn(player, false);
            expireLoginProtection(player);
            pickup(player, 1);
            health(context, player, 19, "Respawn has exactly one pickup effect");
            player = scenario.respawn(player, true);
            expireLoginProtection(player);
            pickup(player, 1);
            health(context, player, 18, "Replacement living entity has no stale pickup state");

            var persistence = context.getWorld().getPersistentStateManager();
            scenario.state.markDirty();
            persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(saved.getCompound("data"), context.getWorld().getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, loaded);
            player.timeUntilRegen = 0;
            pickup(player, 1);
            health(context, player, 17, "Real persistent-state disk load restores activation");
            CycleManager.activateChallenge(player.getServer(), loaded, "no_sprint");
            player.timeUntilRegen = 0;
            pickup(player, 1);
            health(context, player, 17, "Ending stops future damage without healing");
            player.setSprinting(true);
            context.assertTrue(!player.isSprinting(), "Other mechanic remains after ending");

            CycleManager.activateChallenge(player.getServer(), loaded, "double_challenge:bitter_experience,damage_slot_drop");
            player.getInventory().clear();
            player.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 3));
            player.timeUntilRegen = 0;
            pickup(player, 1);
            context.assertTrue(player.getInventory().getStack(10).isEmpty(), "Applied pickup damage triggers slot drop");
            var drops = player.getServerWorld().getEntitiesByClass(ItemEntity.class, player.getBoundingBox().expand(2), e -> e.getStack().isOf(Items.DIAMOND));
            context.assertEquals(1, drops.size(), "One stack dropped once");
            context.assertEquals(3, drops.getFirst().getStack().getCount(), "Full stack retained");
            drops.getFirst().discard();
            player.getInventory().setStack(10, new ItemStack(Items.DIAMOND));
            pickup(player, 1);
            context.assertTrue(!player.getInventory().getStack(10).isEmpty(), "i-frame rejected damage triggers no drop");

            var origin = Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(8, 3, 8)));
            for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(origin).add(-8, -1, -8), BlockPos.ofFloored(origin).add(8, -1, 8)))
                context.getWorld().setBlockState(pos, Blocks.STONE.getDefaultState(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS);
            player.teleport(context.getWorld(), origin.x, origin.y, origin.z, 0, 0);
            CycleManager.activateChallenge(player.getServer(), loaded, "double_challenge:bitter_experience,damage_teleport");
            player.timeUntilRegen = 0;
            pickup(player, 7);
            context.assertTrue(Math.abs(Math.hypot(player.getX() - origin.x, player.getZ() - origin.z) - 5) < 1.0E-6,
                    "Applied pickup damage triggers one five-block teleport");
            var moved = player.getPos();
            pickup(player, 7);
            context.assertEquals(moved, player.getPos(), "Rejected damage triggers no teleport");

            CycleManager.activateChallenge(player.getServer(), loaded, "double_challenge:bitter_experience,no_healing");
            float before = player.getHealth();
            player.timeUntilRegen = 0;
            pickup(player, 1);
            player.heal(10);
            health(context, player, before - 1, "No healing blocks compensation for pickup damage");
            CycleManager.activateChallenge(player.getServer(), loaded, "double_challenge:bitter_experience,one_heart");
            context.assertEquals(2F, player.getMaxHealth(), "One heart keeps its low maximum");
            player.timeUntilRegen = 0;
            pickup(player, 1);
            health(context, player, 1, "Pickup remains one damage under the one-heart cap");
        }
        context.waitAndRun(5, context::complete);
    }

    private static ServerPlayerEntity ready(ChallengeScenario scenario) {
        var player = scenario.player();
        expireLoginProtection(player);
        player.setHealth(20);
        var pos = scenario.context.getAbsolutePos(BlockPos.ORIGIN);
        player.teleport(scenario.context.getWorld(), pos.getX() + 1.5, pos.getY() + 3, pos.getZ() + 1.5, 0, 0);
        return player;
    }

    private static void expireLoginProtection(ServerPlayerEntity player) {
        for (int i = 0; i < 61; i++) player.tick();
        player.timeUntilRegen = 0;
    }

    private static ExperienceOrbEntity orb(ServerPlayerEntity player, int value, int count) {
        var orb = new ExperienceOrbEntity(player.getWorld(), player.getX(), player.getY(), player.getZ(), value);
        var nbt = new NbtCompound();
        orb.writeCustomDataToNbt(nbt);
        nbt.putInt("Count", count);
        orb.readCustomDataFromNbt(nbt);
        player.getServerWorld().spawnEntity(orb);
        return orb;
    }

    private static int portions(ExperienceOrbEntity orb) {
        var nbt = new NbtCompound();
        orb.writeCustomDataToNbt(nbt);
        return nbt.getInt("Count");
    }

    private static void pickup(ServerPlayerEntity player, int value) {
        player.experiencePickUpDelay = 0;
        orb(player, value, 1).onPlayerCollision(player);
    }

    private static void health(TestContext context, ServerPlayerEntity player, float expected, String message) {
        context.assertTrue(Math.abs(player.getHealth() - expected) < 0.0001F,
                message + ": expected " + expected + ", got " + player.getHealth());
    }
}
