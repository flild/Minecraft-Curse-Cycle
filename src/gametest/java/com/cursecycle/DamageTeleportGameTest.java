package com.cursecycle;

import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.DamageTeleportChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DamageTeleportGameTest implements FabricGameTest {
    private static final int FLAGS = Block.FORCE_STATE | Block.NOTIFY_LISTENERS;
    private static final Set<UUID> CANCELLED = new HashSet<>();
    private static final Map<UUID, Boolean> NESTED = new HashMap<>();
    static {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (CANCELLED.contains(entity.getUuid())) return false;
            var sameSource = NESTED.remove(entity.getUuid());
            if (sameSource != null) entity.damage(sameSource ? source : entity.getDamageSources().magic(), 1);
            return true;
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "teleport_campfires")
    public void litCampfireSupportCannotStartDamageTeleportChain(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            expireLoginProtection(p);
            var origin = Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(8, 3, 8))).add(0, 7.0 / 16, 0);
            for (var block : new Block[]{Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE}) {
                s.activate("test_easy");
                prepare(c.getWorld(), origin, false);
                reset(p, c.getWorld(), origin);
                int acceptedBurningSupports = 0;
                for (int direction = 0; direction < 8; direction++) {
                    var target = origin.add(5 * Math.cos(direction * Math.PI / 4), 0, 5 * Math.sin(direction * Math.PI / 4));
                    var pos = BlockPos.ofFloored(target);
                    c.getWorld().setBlockState(pos.down(), Blocks.STONE.getDefaultState(), FLAGS);
                    c.getWorld().setBlockState(pos, block.getDefaultState().with(CampfireBlock.LIT, false), FLAGS);
                    c.assertTrue(DamageTeleportChallenge.isSafe(p, c.getWorld(), target), "Unlit campfire is a solid safe control");
                    p.setHealth(20);
                    p.timeUntilRegen = 0;
                    c.getWorld().getBlockState(pos).onEntityCollision(c.getWorld(), pos, p);
                    c.assertEquals(20F, p.getHealth(), "Extinguished support does no contact damage");
                    c.getWorld().setBlockState(pos, block.getDefaultState().with(CampfireBlock.LIT, true), FLAGS);
                    c.getWorld().getBlockState(pos).onEntityCollision(c.getWorld(), pos, p);
                    c.assertTrue(p.getHealth() < 20, "Control: burning support really damages through vanilla collision");
                    if (DamageTeleportChallenge.isSafe(p, c.getWorld(), target)) acceptedBurningSupports++;
                }
                s.activate("damage_teleport");
                reset(p, c.getWorld(), origin);
                p.timeUntilRegen = 0;
                p.damage(p.getDamageSources().generic(), 1);
                at(c, p, origin, "All forty candidates unsafe: no move onto burning support");
                c.assertEquals(0, acceptedBurningSupports, "Burning supports rejected by safety gate");
                c.assertEquals(19F, p.getHealth(), "Only original damage, no fire chain");
            }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "teleport_dimensions", tickLimit = 300)
    public void dimensionsBoundsAndPartialCollisionSupports(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            expireLoginProtection(p);
            s.activate("damage_teleport");
            for (var key : List.of(net.minecraft.world.World.NETHER, net.minecraft.world.World.END)) {
                var world = p.getServer().getWorld(key);
                var origin = new Vec3d(8.5, 180, 8.5);
                world.getChunk(0, 0); // Fixture prepares a loaded arena; the safety query must not load chunks.
                prepare(world, origin, true);
                reset(p, world, origin);
                p.onTeleportationDone();
                p.timeUntilRegen = 0;
                p.damage(p.getDamageSources().generic(), 1);
                c.assertTrue(p.getServerWorld() == world, "No dimension change from reaction");
                c.assertTrue(Math.abs(Math.hypot(p.getX() - origin.x, p.getZ() - origin.z) - 5) < 1.0E-6,
                        "Five-block teleport in " + key.getValue());
            }
            var world = c.getWorld();
            var origin = Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(8, 3, 8)));
            prepare(world, origin, false);
            reset(p, world, origin);
            p.onTeleportationDone();
            var target = origin.add(5, 0, 0);
            var feet = BlockPos.ofFloored(target);
            world.setBlockState(feet.down(), Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), FLAGS);
            c.assertTrue(DamageTeleportChallenge.isSafe(p, world, target), "Dry top slab safe");
            world.setBlockState(feet.down(), Blocks.OAK_FENCE.getDefaultState(), FLAGS);
            var fenceTop = target.add(0, .5, 0);
            c.assertTrue(DamageTeleportChallenge.isSafe(p, world, fenceTop), "Extended fence collision provides support");
            world.setBlockState(feet.down(), Blocks.OAK_FENCE.getDefaultState().with(net.minecraft.block.FenceBlock.WATERLOGGED, true), FLAGS);
            c.assertTrue(!DamageTeleportChallenge.isSafe(p, world, fenceTop), "Waterlogged support below body cells rejected");
            c.assertTrue(!DamageTeleportChallenge.isSafe(p, world, new Vec3d(target.x, world.getBottomY(), target.z)), "Below-world support impossible");
            c.assertTrue(!DamageTeleportChallenge.isSafe(p, world, new Vec3d(target.x, world.getTopY() - 1, target.z)), "Full standing headroom inside build height");
            // Center inside border is insufficient when the body's edge crosses it.
            var border = world.getWorldBorder();
            double x = border.getCenterX(), z = border.getCenterZ(), size = border.getSize();
            try {
                border.setCenter(target.x - .1, target.z);
                border.setSize(.7);
                c.assertTrue(!DamageTeleportChallenge.isSafe(p, world, fenceTop), "Whole hitbox must fit border");
            } finally { border.setCenter(x, z); border.setSize(size); }
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "teleport_pairs", tickLimit = 300)
    public void actualOrbDamageHealthCapAndBothDropOrders(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            expireLoginProtection(p);
            var world = c.getWorld();
            var origin = Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(8, 3, 8)));
            prepare(world, origin, true);
            for (String ids : new String[]{"damage_teleport,damage_slot_drop", "damage_slot_drop,damage_teleport"}) {
                s.activate("double_challenge:" + ids);
                reset(p, world, origin);
                p.getInventory().clear();
                p.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 7));
                teleportPackets(s, p);
                p.damage(p.getDamageSources().generic(), 1);
                c.assertEquals(1, teleportPackets(s, p), "One teleport for order " + ids);
                var drops = world.getEntitiesByClass(ItemEntity.class, new Box(origin, origin).expand(10), e -> e.getStack().isOf(Items.DIAMOND));
                c.assertEquals(1, drops.size(), "One drop for order " + ids);
                c.assertTrue(drops.getFirst().getPos().squaredDistanceTo(origin) < 1.0E-12, "Drop uses receipt position");
                c.assertEquals(7, drops.getFirst().getStack().getCount(), "Whole stack");
                drops.forEach(ItemEntity::discard);
            }
            s.activate("double_challenge:damage_teleport,bitter_experience");
            reset(p, world, origin);
            int xp = p.totalExperience;
            var orb = new net.minecraft.entity.ExperienceOrbEntity(world, origin.x, origin.y, origin.z, 7);
            world.spawnEntity(orb);
            p.experiencePickUpDelay = 0;
            teleportPackets(s, p);
            try { orb.onPlayerCollision(p); } finally { orb.discard(); }
            c.assertEquals(xp + 7, p.totalExperience, "Real orb XP granted");
            c.assertEquals(19F, p.getHealth(), "Pickup damage accepted");
            moved(c, p, origin);
            c.assertEquals(1, teleportPackets(s, p), "Actual XP source causes one teleport");
            s.activate("bitter_experience");
            reset(p, world, origin);
            orb = new net.minecraft.entity.ExperienceOrbEntity(world, origin.x, origin.y, origin.z, 1);
            world.spawnEntity(orb);
            p.experiencePickUpDelay = 0;
            try { orb.onPlayerCollision(p); } finally { orb.discard(); }
            c.assertEquals(19F, p.getHealth(), "Partner still damages after teleport ends");
            at(c, p, origin, "Ending teleport leaves XP consequence unchanged");
            s.activate("test_easy");
            reset(p, world, origin);
            teleportPackets(s, p);
            s.activate("double_challenge:damage_teleport,one_heart");
            c.assertEquals(2F, p.getHealth(), "One-heart health cap really applied");
            at(c, p, origin, "Cap setter is not an accepted damage event");
            c.assertEquals(0, teleportPackets(s, p), "Cap creates no teleport packet");
            p.damage(p.getDamageSources().generic(), .5F);
            c.assertEquals(1.5F, p.getHealth(), "Nonlethal accepted damage under cap");
            moved(c, p, origin);
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "teleport_candidates", tickLimit = 300)
    public void everyDirectionAndHeightThroughActualDamageAndNoRetry(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            expireLoginProtection(p);
            var origin = Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(8, 3, 8)));
            s.activate("damage_teleport");
            for (int direction = 0; direction < 8; direction++) {
                for (int height : new int[]{0, 1, -1, 2, -2}) {
                    prepare(c.getWorld(), origin, false);
                    var target = origin.add(5 * Math.cos(direction * Math.PI / 4), height, 5 * Math.sin(direction * Math.PI / 4));
                    c.getWorld().setBlockState(BlockPos.ofFloored(target).down(), Blocks.STONE.getDefaultState(), FLAGS);
                    reset(p, c.getWorld(), origin);
                    teleportPackets(s, p);
                    p.damage(p.getDamageSources().generic(), 1);
                    at(c, p, target, "Only safe candidate direction=" + direction + " height=" + height);
                    c.assertEquals(1, teleportPackets(s, p), "Exactly one vanilla teleport packet per accepted hit");
                }
            }
            prepare(c.getWorld(), origin, false);
            reset(p, c.getWorld(), origin);
            p.setVelocity(.1, -.2, .3);
            p.fallDistance = 7;
            teleportPackets(s, p);
            p.damage(p.getDamageSources().generic(), 1);
            at(c, p, origin, "Forty failures leave position unchanged");
            c.assertEquals(new Vec3d(.1, -.2, .3), p.getVelocity(), "Failed search preserves velocity");
            c.assertEquals(7F, p.fallDistance, "Failed search preserves fall distance");
            c.assertEquals(0, teleportPackets(s, p), "No teleport packet after failure");
            prepare(c.getWorld(), origin, true);
            for (int i = 0; i < 100; i++) ChallengeRegistry.get("damage_teleport").onTick(p.getServer(), p);
            at(c, p, origin, "No queued retry after geometry changes");
            c.assertEquals(0, teleportPackets(s, p), "No tick teleport");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "teleport_receipts")
    public void cancelledZeroLossSourcesAndNestedResults(TestContext c) {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            expireLoginProtection(p);
            var origin = Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(8, 3, 8)));
            prepare(c.getWorld(), origin, true);
            reset(p, c.getWorld(), origin);
            p.damage(p.getDamageSources().generic(), 1);
            at(c, p, origin, "Inactive control");
            s.activate("damage_teleport");
            reset(p, c.getWorld(), origin);
            CANCELLED.add(p.getUuid());
            try { p.damage(p.getDamageSources().generic(), 2); }
            finally { CANCELLED.remove(p.getUuid()); }
            c.assertEquals(20F, p.getHealth(), "Actual cancellation");
            at(c, p, origin, "Cancelled damage never teleports");
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 4));
            p.damage(p.getDamageSources().generic(), 2);
            c.assertEquals(20F, p.getHealth(), "Zero loss control");
            at(c, p, origin, "Fully resisted damage never teleports");
            p.clearStatusEffects();
            p.setInvulnerable(true);
            try { p.damage(p.getDamageSources().generic(), 2); }
            finally { p.setInvulnerable(false); }
            at(c, p, origin, "Invulnerability never teleports");
            for (var source : List.of(p.getDamageSources().fall(), p.getDamageSources().onFire(),
                    p.getDamageSources().magic(), p.getDamageSources().drown(), p.getDamageSources().generic())) {
                reset(p, c.getWorld(), origin);
                teleportPackets(s, p);
                p.damage(source, 1);
                c.assertEquals(19F, p.getHealth(), "Accepted source " + source.getName());
                moved(c, p, origin);
                c.assertEquals(1, teleportPackets(s, p), "One result per source");
            }
            for (boolean sameSource : new boolean[]{false, true}) {
                reset(p, c.getWorld(), origin);
                teleportPackets(s, p);
                NESTED.put(p.getUuid(), sameSource);
                try { p.damage(p.getDamageSources().generic(), 2); }
                finally { NESTED.remove(p.getUuid()); }
                c.assertEquals(18F, p.getHealth(), "Inner loss1 plus outer i-frame difference1");
                c.assertEquals(2, teleportPackets(s, p), "Separate accepted nested calls, same source=" + sameSource);
            }
            reset(p, c.getWorld(), origin);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100));
            teleportPackets(s, p);
            p.damage(p.getDamageSources().generic(), 1);
            c.assertEquals(20F, p.getHealth(), "Absorption-only damage");
            c.assertEquals(3F, p.getAbsorptionAmount(), "Golden HP actually lost");
            c.assertEquals(1, teleportPackets(s, p), "Absorption counts exactly once");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "teleport_lifecycle", tickLimit = 400)
    public void modesPersonalRepeatedActivationAndActualDiskReconnect(TestContext c) throws Exception {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player();
            var q = s.player();
            expireLoginProtection(p);
            expireLoginProtection(q);
            var world = c.getWorld();
            var server = world.getServer();
            var origin = Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(8, 3, 8)));
            prepare(world, origin, true);
            for (String cmd : new String[]{"fdc list", "fdc activate damage_teleport", "fdc active"})
                c.assertTrue(server.getCommandManager().getDispatcher().execute(cmd, server.getCommandSource().withLevel(2)) > 0, "OP2 " + cmd);
            var late = s.player();
            expireLoginProtection(late);
            ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(late.networkHandler, null, server);
            for (var player : new ServerPlayerEntity[]{p, q, late}) {
                reset(player, world, origin);
                player.damage(player.getDamageSources().generic(), 1);
                moved(c, player, origin);
            }
            for (var mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL}) {
                p.changeGameMode(mode);
                reset(p, world, origin);
                // Generic kill bypasses vanilla Creative immunity; the mod gate must still exclude it.
                p.damage(p.getDamageSources().genericKill(), 1);
                if (mode == GameMode.ADVENTURE || mode == GameMode.SURVIVAL) moved(c, p, origin);
                else at(c, p, origin, "Excluded mode " + mode);
            }
            var challenge = ChallengeRegistry.get("damage_teleport");
            challenge.setEnabled(false);
            try {
                reset(p, world, origin);
                p.damage(p.getDamageSources().generic(), 1);
                at(c, p, origin, "Disabled mechanic");
            } finally { challenge.setEnabled(true); }
            s.state.getActiveChallengeIds().clear();
            s.state.getPersonalActiveChallengeIds(p.getUuid()).add("damage_teleport");
            reset(p, world, origin);
            p.damage(p.getDamageSources().generic(), 1);
            moved(c, p, origin);
            reset(q, world, origin);
            q.damage(q.getDamageSources().generic(), 1);
            at(c, q, origin, "Independent UUID without PERSONAL ID");
            s.state.getActiveChallengeIds().add("damage_teleport");
            reset(p, world, origin);
            teleportPackets(s, p);
            p.damage(p.getDamageSources().generic(), 1);
            c.assertEquals(1, teleportPackets(s, p), "GLOBAL plus PERSONAL still one reaction");
            s.state.getPersonalActiveChallenges().clear();
            for (int i = 0; i < 32; i++) {
                s.activate("damage_teleport");
                long serial = s.state.getActivationSerial();
                s.activate("damage_teleport");
                c.assertTrue(s.state.getActivationSerial() > serial, "New activation serial");
                reset(p, world, origin);
                teleportPackets(s, p);
                p.damage(p.getDamageSources().generic(), 1);
                c.assertEquals(1, teleportPackets(s, p), "Repeated activation never duplicates");
                s.activate("no_sprint");
                var ended = p.getPos();
                p.timeUntilRegen = 0;
                p.damage(p.getDamageSources().generic(), 1);
                at(c, p, ended, "Ended never restores old position");
            }
            s.activate("double_challenge:damage_teleport,no_sprint");
            reset(p, world, origin);
            p.damage(p.getDamageSources().generic(), 1);
            moved(c, p, origin);
            p.setSprinting(true);
            c.assertTrue(!p.isSprinting(), "Other mechanic active during pair");
            s.activate("no_sprint");
            p.setSprinting(true);
            c.assertTrue(!p.isSprinting(), "Other mechanic survives end");

            s.activate("damage_teleport");
            var uuid = p.getUuid();
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 777));
            p.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 7));
            disconnect(p);
            var pm = server.getOverworld().getPersistentStateManager();
            pm.save();
            var disk = pm.readNbt(CurseCycleState.STATE_KEY, null, net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), world.getRegistryManager());
            pm.set(CurseCycleState.STATE_KEY, loaded);
            p = s.player(uuid);
            expireLoginProtection(p);
            reset(p, world, origin);
            p.damage(p.getDamageSources().generic(), 1);
            moved(c, p, origin);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 777));
            disconnect(p);
            CycleManager.activateChallenge(server, loaded, "no_sprint");
            pm.save();
            disk = pm.readNbt(CurseCycleState.STATE_KEY, null, net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            pm.set(CurseCycleState.STATE_KEY, CurseCycleState.createFromNbt(disk.getCompound("data"), world.getRegistryManager()));
            p = s.player(uuid);
            c.assertEquals(777, p.getStatusEffect(StatusEffects.SPEED).getDuration(), "Foreign effect survives offline end");
            c.assertEquals(7, p.getInventory().getStack(10).getCount(), "Foreign inventory unchanged");
            expireLoginProtection(p);
            reset(p, world, origin);
            p.damage(p.getDamageSources().generic(), 1);
            at(c, p, origin, "Actual saved ended state never resumes teleport");
            c.assertTrue(CurseCycleState.getServerState(server).getChallengeData().isEmpty(), "No own persistent positions/RNG/snapshots");
        }
        c.complete();
    }

    private static int teleportPackets(ChallengeScenario s, ServerPlayerEntity p) {
        var channel = s.channel(p);
        channel.runPendingTasks();
        int count = 0;
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof PlayerPositionLookS2CPacket) count++;
            io.netty.util.ReferenceCountUtil.release(packet);
        }
        return count;
    }

    private static void disconnect(ServerPlayerEntity p) {
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler, p.getServer());
        p.getServer().getPlayerManager().remove(p);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "damage_teleport", tickLimit = 500)
    public void safeTeleportActualDamageAndLifecycle(TestContext context) throws Exception {
        var world = context.getWorld();
        var origin = Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(8, 3, 8)));
        try (var scenario = new ChallengeScenario(context)) {
            prepare(world, origin, true);
            var player = scenario.player();
            expireLoginProtection(player);
            reset(player, world, origin);
            scenario.activate("damage_teleport");
            at(context, player, origin, "Activation does not teleport");
            player.setYaw(37);
            player.setPitch(-12);
            player.setVelocity(0.1, -0.1, 0.2);
            player.fallDistance = 8;
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);
            context.assertEquals(19F, player.getHealth(), "Only original damage");
            context.assertEquals(37F, player.getYaw(), "Yaw preserved");
            context.assertEquals(-12F, player.getPitch(), "Pitch preserved");
            context.assertEquals(Vec3d.ZERO, player.getVelocity(), "Velocity reset");
            context.assertEquals(0F, player.fallDistance, "Fall distance reset");
            Vec3d after = player.getPos();
            player.damage(player.getDamageSources().generic(), 1);
            at(context, player, after, "Ignored i-frame does not teleport");
            player.setHealth(18);
            at(context, player, after, "Direct health reduction is not damage");

            reset(player, world, origin);
            player.setYaw(0);
            player.setPitch(0);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHIELD));
            player.setCurrentHand(Hand.MAIN_HAND);
            for (int i = 0; i < 6; i++) player.playerTick();
            player.teleport(world, origin.x, origin.y, origin.z, 0, 0);
            var zombie = EntityType.ZOMBIE.create(world);
            zombie.setPosition(origin.add(0, 0, 2));
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().mobAttack(zombie), 4);
            context.assertEquals(20F, player.getHealth(), "Control: shield really blocks damage");
            at(context, player, origin, "Shield-blocked hit does not teleport");
            player.stopUsingItem();
            zombie.discard();
            player.getInventory().clear();

            reset(player, world, origin);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1000));
            player.damage(player.getDamageSources().generic(), 1);
            context.assertEquals(20F, player.getHealth(), "Absorption-only damage leaves health unchanged");
            moved(context, player, origin);

            reset(player, world, origin);
            var boat = new BoatEntity(world, origin.x, origin.y, origin.z);
            world.spawnEntity(boat);
            player.startRiding(boat, true);
            Vec3d riding = player.getPos();
            player.damage(player.getDamageSources().generic(), 1);
            at(context, player, riding, "Passenger does not teleport");
            context.assertTrue(player.getVehicle() == boat, "No forced dismount");
            player.stopRiding();
            boat.discard();

            prepare(world, origin, false);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            at(context, player, origin, "No safe support gives no teleport");
            prepare(world, origin, true);
            ChallengeRegistry.get("damage_teleport").onTick(world.getServer(), player);
            at(context, player, origin, "No queued retry when support later appears");

            prepare(world, origin, false);
            BlockPos east = BlockPos.ofFloored(origin.add(5, 0, 0));
            world.setBlockState(east, Blocks.STONE.getDefaultState(), FLAGS);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            at(context, player, origin.add(5, 1, 0), "First safe height +1 is selected");
            prepare(world, origin, false);
            world.setBlockState(east.down(3), Blocks.STONE.getDefaultState(), FLAGS);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            at(context, player, origin.add(5, -2, 0), "Height -2 remains within search");

            prepare(world, origin, true);
            reset(player, world, origin);
            checkHazardsAndChunks(context, player, world, origin);

            reset(player, world, origin);
            player.getInventory().setStack(10, new ItemStack(Items.DIAMOND, 3));
            player.getInventory().getStack(10).set(DataComponentTypes.CUSTOM_NAME, Text.literal("teleport-origin"));
            scenario.activate("double_challenge:damage_teleport,damage_slot_drop");
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);
            var drops = world.getEntitiesByClass(ItemEntity.class,
                    new Box(origin.x - 1, origin.y - 1, origin.z - 1, origin.x + 1, origin.y + 2, origin.z + 1),
                    item -> item.getStack().isOf(Items.DIAMOND)
                            && item.getStack().get(DataComponentTypes.CUSTOM_NAME) != null
                            && item.getStack().get(DataComponentTypes.CUSTOM_NAME).getString().equals("teleport-origin"));
            context.assertEquals(1, drops.size(), "Exactly one drop at original damage position");
            context.assertTrue(drops.getFirst().getPos().squaredDistanceTo(origin) < 1.0E-12, "Drop precedes teleport even with reverse ID order");
            context.assertEquals(3, drops.getFirst().getStack().getCount(), "Full stack preserved");
            context.assertTrue(player.getInventory().getStack(10).isEmpty(), "Original slot emptied once");

            scenario.activate("damage_teleport");
            reset(player, world, origin);
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.damage(player.getDamageSources().generic(), 100);
            context.assertTrue(player.isAlive(), "Totem saved the player");
            moved(context, player, origin);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 100);
            context.assertTrue(!player.isAlive(), "Lethal control has no totem");
            at(context, player, origin, "Death without totem does not teleport");
            player = scenario.respawn(player, false);
            expireLoginProtection(player);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);
            player = scenario.respawn(player, true);
            expireLoginProtection(player);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);

            var late = scenario.player();
            expireLoginProtection(late);
            reset(late, world, origin);
            late.damage(late.getDamageSources().generic(), 1);
            moved(context, late, origin);
            for (var mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
                player.changeGameMode(mode);
                context.assertTrue(!ChallengeActivity.active(player, "damage_teleport"), "Excluded mode " + mode);
            }
            player.changeGameMode(GameMode.ADVENTURE);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);
            player.changeGameMode(GameMode.SURVIVAL);

            scenario.state.getActiveChallengeIds().clear();
            scenario.state.getPersonalActiveChallengeIds(player.getUuid()).add("damage_teleport");
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);
            reset(late, world, origin);
            late.damage(late.getDamageSources().generic(), 1);
            at(context, late, origin, "Personal ID does not affect the other player");
            scenario.state.getPersonalActiveChallengeIds(player.getUuid()).clear();
            scenario.activate("damage_teleport");

            var persistence = world.getPersistentStateManager();
            scenario.state.markDirty();
            persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(saved.getCompound("data"), world.getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, loaded);
            reset(player, world, origin);
            player.damage(player.getDamageSources().generic(), 1);
            moved(context, player, origin);
            CycleManager.activateChallenge(world.getServer(), loaded, "no_sprint");
            Vec3d ended = player.getPos();
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().generic(), 1);
            at(context, player, ended, "Stopping does not restore position or react to later damage");
            player.setSprinting(true);
            context.assertTrue(!player.isSprinting(), "Other challenge remains active");
        }
        context.waitAndRun(5, context::complete);
    }

    private static void checkHazardsAndChunks(TestContext context, ServerPlayerEntity player, ServerWorld world, Vec3d origin) {
        Vec3d target = origin.add(5, 0, 0);
        BlockPos feet = BlockPos.ofFloored(target);
        context.assertTrue(DamageTeleportChallenge.isSafe(player, world, target), "Control: ordinary stone is safe");
        for (var block : new net.minecraft.block.Block[]{Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.WATER, Blocks.LAVA,
                Blocks.CACTUS, Blocks.SWEET_BERRY_BUSH, Blocks.POWDER_SNOW, Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_GATEWAY}) {
            world.setBlockState(feet.down(), (block == Blocks.SOUL_FIRE ? Blocks.SOUL_SOIL : Blocks.STONE).getDefaultState(), FLAGS);
            world.setBlockState(feet, block.getDefaultState(), FLAGS);
            context.assertTrue(world.getBlockState(feet).isOf(block), "Hazard control actually exists: " + block);
            context.assertTrue(!DamageTeleportChallenge.isSafe(player, world, target), "Unsafe body block " + block);
            world.setBlockState(feet, Blocks.AIR.getDefaultState(), FLAGS);
        }
        world.setBlockState(feet.down(), Blocks.MAGMA_BLOCK.getDefaultState(), FLAGS);
        context.assertTrue(!DamageTeleportChallenge.isSafe(player, world, target), "Magma support rejected");
        world.setBlockState(feet.down(), Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP)
                .with(SlabBlock.WATERLOGGED, true), FLAGS);
        context.assertTrue(!DamageTeleportChallenge.isSafe(player, world, target), "Waterlogged support rejected");
        world.setBlockState(feet.down(), Blocks.STONE.getDefaultState(), FLAGS);
        world.setBlockState(feet.up(), Blocks.STONE.getDefaultState(), FLAGS);
        context.assertTrue(!DamageTeleportChallenge.isSafe(player, world, target), "Whole body needs clearance");
        world.setBlockState(feet.up(), Blocks.AIR.getDefaultState(), FLAGS);
        var border = world.getWorldBorder();
        double centerX = border.getCenterX(), centerZ = border.getCenterZ(), size = border.getSize();
        try {
            border.setCenter(origin.x, origin.z);
            border.setSize(4);
            context.assertTrue(!DamageTeleportChallenge.isSafe(player, world, target), "World border respected");
        } finally {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
        Vec3d distant = origin.add(4096, 0, 0);
        int x = BlockPos.ofFloored(distant).getX() >> 4, z = BlockPos.ofFloored(distant).getZ() >> 4;
        context.assertTrue(!world.getChunkManager().isChunkLoaded(x, z), "Unloaded control chunk");
        context.assertTrue(!DamageTeleportChallenge.isSafe(player, world, distant), "Unloaded candidate rejected");
        context.assertTrue(!world.getChunkManager().isChunkLoaded(x, z), "Safety query did not load the chunk");
    }

    private static void prepare(ServerWorld world, Vec3d origin, boolean floor) {
        BlockPos center = BlockPos.ofFloored(origin);
        for (BlockPos pos : BlockPos.iterate(center.add(-8, -3, -8), center.add(8, 4, 8)))
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), FLAGS);
        if (floor) for (BlockPos pos : BlockPos.iterate(center.add(-8, -1, -8), center.add(8, -1, 8)))
            world.setBlockState(pos, Blocks.STONE.getDefaultState(), FLAGS);
        world.setBlockState(center.down(), Blocks.STONE.getDefaultState(), FLAGS);
    }

    private static void reset(ServerPlayerEntity player, ServerWorld world, Vec3d origin) {
        player.stopUsingItem();
        player.clearStatusEffects();
        player.setHealth(20);
        player.setAbsorptionAmount(0);
        player.timeUntilRegen = 0;
        player.teleport(world, origin.x, origin.y, origin.z, 0, 0);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
    }

    private static void expireLoginProtection(ServerPlayerEntity player) {
        for (int i = 0; i < 61; i++) player.tick();
    }

    private static void at(TestContext context, ServerPlayerEntity player, Vec3d expected, String message) {
        context.assertTrue(player.getPos().squaredDistanceTo(expected) < 1.0E-12,
                message + ": " + player.getPos() + " expected " + expected);
    }

    private static void moved(TestContext context, ServerPlayerEntity player, Vec3d origin) {
        context.assertTrue(Math.abs(Math.hypot(player.getX() - origin.x, player.getZ() - origin.z) - 5) < 1.0E-6,
                "Exactly one five-block horizontal teleport: " + player.getPos() + " from " + origin);
        context.assertTrue(player.getServerWorld() == context.getWorld(), "Dimension preserved");
    }
}
