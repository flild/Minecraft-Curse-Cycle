package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.InvisibleMobsChallenge;
import com.cursecycle.network.CurseCycleSyncPayload;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

/** Dedicated-server coverage of activation, saved IDs and S2C eligibility; rendering is client-only. */
public class InvisibleMobsGameTest implements FabricGameTest {
    private static final String ID = InvisibleMobsChallenge.ID;

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "invisible_mobs")
    public void mobsKeepEquipmentCombatTargetsAndForeignInvisibility(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var player = s.player();
            for (int i = 0; i < 61; i++) player.tick();
            var zombie = context.spawnEntity(net.minecraft.entity.EntityType.ZOMBIE, new net.minecraft.util.math.BlockPos(1, 2, 1));
            var invisible = context.spawnEntity(net.minecraft.entity.EntityType.ZOMBIE, new net.minecraft.util.math.BlockPos(2, 2, 1));
            try {
                zombie.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD));
                zombie.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new net.minecraft.item.ItemStack(net.minecraft.item.Items.NETHERITE_CHESTPLATE));
                zombie.setTarget(player);
                zombie.setGlowing(true);
                invisible.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.INVISIBILITY, 1200));
                invisible.tick();
                context.assertTrue(invisible.isInvisible(), "Foreign vanilla invisibility has a positive control");
                s.activate(ID);
                context.assertFalse(zombie.isInvisible(), "Challenge does not set a real invisibility flag");
                context.assertFalse(zombie.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY), "No invisibility effect applied");
                context.assertTrue(zombie.getTarget() == player && zombie.isGlowing(), "Target and glowing state preserved");
                float before = player.getHealth();
                player.timeUntilRegen = 0;
                zombie.tryAttack(player);
                context.assertTrue(player.getHealth() < before, "Mob attack actually reduces player health");
                before = zombie.getHealth();
                zombie.timeUntilRegen = 0;
                zombie.damage(player.getDamageSources().playerAttack(player), 8);
                context.assertTrue(zombie.getHealth() < before, "Equipped mob can still receive real damage");
                float current = zombie.getHealth();
                s.activate("no_sprint");
                context.assertEquals(current, zombie.getHealth(), "Ending does not restore mob health");
                context.assertTrue(zombie.getMainHandStack().isOf(net.minecraft.item.Items.DIAMOND_SWORD)
                        && zombie.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(net.minecraft.item.Items.NETHERITE_CHESTPLATE), "Equipment retained");
                context.assertTrue(invisible.isInvisible() && invisible.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY), "Foreign invisibility survives cleanup");
                context.assertFalse(zombie.isInvisible(), "Ordinary mob remains naturally visible");
            } finally {
                zombie.discard();
                invisible.discard();
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "invisible_mobs")
    public void activationRecipientsPersistenceAndLifecycle(TestContext context) throws Exception {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer();
            var first = s.player();
            var second = s.player();
            var firstChannel = s.channel(first);
            s.activate(ID);
            assertIds(context, firstChannel, List.of(ID));
            assertIds(context, s.channel(second), List.of(ID));
            var late = s.player();
            assertIds(context, s.channel(late), List.of(ID));
            // The client's current mode gates the mob filter; the active ID survives mode changes.
            for (var mode : GameMode.values()) {
                first.changeGameMode(mode);
                CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
                assertIds(context, firstChannel, List.of(ID));
            }
            first.changeGameMode(GameMode.SURVIVAL);
            first.setHealth(0);
            first = s.respawn(first, false);
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertIds(context, firstChannel, List.of(ID));
            for (var dimension : List.of(World.NETHER, World.END, World.OVERWORLD)) {
                first.teleport(server.getWorld(dimension), .5, 80, .5, 0, 0);
                first.onTeleportationDone();
                CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
                assertIds(context, firstChannel, List.of(ID));
            }
            s.activate("double_challenge:invisible_mobs,no_sprint");
            var both = List.of(ID, "no_sprint");
            assertIds(context, firstChannel, both);
            first.setSprinting(true);
            context.assertFalse(first.isSprinting(), "Companion sprint restriction remains authoritative");
            var persistence = server.getOverworld().getPersistentStateManager();
            persistence.save();
            var saved = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    SharedConstants.getGameVersion().getSaveVersion().getId());
            var restored = CurseCycleState.createFromNbt(saved.getCompound("data"), context.getWorld().getRegistryManager());
            context.assertEquals(both, restored.getActiveChallengeIds(), "Active IDs are actually saved to disk");
            persistence.set(CurseCycleState.STATE_KEY, restored);
            CycleManager.syncState(server, restored, CurseCycleMod.getConfig());
            assertIds(context, firstChannel, both);
            var uuid = second.getUuid();
            server.getPlayerManager().remove(second);
            second = s.player(uuid);
            assertIds(context, s.channel(second), both);
            server.getPlayerManager().remove(second);
            CycleManager.activateChallenge(server, restored, "no_sprint");
            assertIds(context, firstChannel, List.of("no_sprint"));
            persistence.save();
            var endedNbt = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    SharedConstants.getGameVersion().getSaveVersion().getId());
            var ended = CurseCycleState.createFromNbt(endedNbt.getCompound("data"), context.getWorld().getRegistryManager());
            context.assertEquals(List.of("no_sprint"), ended.getActiveChallengeIds(),
                    "Ending while a player is offline is saved to disk");
            persistence.set(CurseCycleState.STATE_KEY, ended);
            second = s.player(uuid);
            assertIds(context, s.channel(second), List.of("no_sprint"));
            second.setSprinting(true);
            context.assertFalse(second.isSprinting(), "Stopping mob filtering does not remove no_sprint");
            var source = server.getCommandSource().withLevel(2);
            for (var command : List.of("fdc list", "fdc activate invisible_mobs", "fdc active"))
                context.assertTrue(server.getCommandManager().getDispatcher().execute(command, source) > 0,
                        "Command succeeds: " + command);
            assertIds(context, s.channel(second), List.of(ID));
            context.assertEquals(1L, ChallengeRegistry.getAll().stream().filter(c -> c.getId().equals(ID)).count(),
                    "Exactly one registered challenge");
        }
        context.waitAndRun(5, context::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "invisible_mobs")
    public void personalIdsDeduplicateAndDisabledServerGateReachesClients(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer();
            var owner = s.player();
            var other = s.player();
            s.activate("no_sprint");
            s.state.getPersonalActiveChallenges().put(owner.getUuid(), new ArrayList<>(List.of(ID, ID)));
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertIds(context, s.channel(owner), List.of("no_sprint", ID));
            assertIds(context, s.channel(other), List.of("no_sprint"));
            s.state.getActiveChallengeIds().add(ID);
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertIds(context, s.channel(owner), List.of("no_sprint", ID));
            assertIds(context, s.channel(other), List.of("no_sprint", ID));
            var challenge = ChallengeRegistry.get(ID);
            try {
                challenge.setEnabled(false);
                CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
                assertIds(context, s.channel(owner), List.of("no_sprint"));
                assertIds(context, s.channel(other), List.of("no_sprint"));
            } finally {
                challenge.setEnabled(true);
            }
            CycleManager.syncState(server, s.state, CurseCycleMod.getConfig());
            assertIds(context, s.channel(owner), List.of("no_sprint", ID));
        }
        context.complete();
    }

    private static void assertIds(TestContext context, EmbeddedChannel channel, List<String> expected) {
        channel.runPendingTasks();
        channel.flush();
        CurseCycleSyncPayload last = null;
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof CustomPayloadS2CPacket custom && custom.payload() instanceof CurseCycleSyncPayload sync)
                last = sync;
        }
        context.assertTrue(last != null, "A real S2C state packet reaches the recipient");
        context.assertEquals(expected, last.activeChallengeIds(), "Client receives only current effective enabled IDs");
        var buffer = new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            CurseCycleSyncPayload.CODEC.encode(buffer, last);
            var decoded = CurseCycleSyncPayload.CODEC.decode(buffer);
            context.assertEquals(expected, decoded.activeChallengeIds(), "Wire codec retains effective IDs");
            context.assertEquals(last.currentDay(), decoded.currentDay(), "Wire codec retains day");
            context.assertEquals(last.ticksUntilNext(), decoded.ticksUntilNext(), "Wire codec retains deadline");
            context.assertEquals(last.nextChallengeId(), decoded.nextChallengeId(), "Wire codec retains next ID");
            context.assertEquals(last.historyChallengeIds(), decoded.historyChallengeIds(), "Wire codec retains history");
            context.assertEquals(last.blockedDirection(), decoded.blockedDirection(), "Wire codec retains companion data");
            context.assertEquals(0, buffer.readableBytes(), "Exact packet consumed");
        } finally { buffer.release(); }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "invisible_mobs")
    public void repeatedActivationAndEmptyServerHaveNoEntityOrPlayerSnapshot(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var server = context.getWorld().getServer();
            var player = s.player();
            player.getInventory().setStack(8, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 17));
            player.setHealth(17);
            player.experienceLevel = 7;
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE, 1000));
            long serial = s.state.getActivationSerial();
            for (int cycle = 0; cycle < 32; cycle++) {
                s.activate("double_challenge:invisible_mobs,invisible_mobs");
                context.assertTrue(s.state.getActivationSerial() > serial, "Same ID starts a new activation");
                serial = s.state.getActivationSerial();
                assertIds(context, s.channel(player), List.of(ID));
                s.activate("double_challenge:invisible_mobs,no_sprint");
                assertIds(context, s.channel(player), List.of(ID, "no_sprint"));
                player.setSprinting(true);
                context.assertFalse(player.isSprinting(), "Companion works while filtering");
                s.activate(ID);
                player.setSprinting(true);
                context.assertTrue(player.isSprinting(), "Ending companion leaves visual activation independent");
                s.activate("no_sprint");
                assertIds(context, s.channel(player), List.of("no_sprint"));
                player.setSprinting(true);
                context.assertFalse(player.isSprinting(), "Companion survives visual end");
                context.assertEquals(17, player.getInventory().getStack(8).getCount(), "Foreign inventory unchanged");
                context.assertEquals(17F, player.getHealth(), "Health unchanged");
                context.assertEquals(7, player.experienceLevel, "XP unchanged");
                context.assertTrue(player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE), "Foreign effect retained");
                context.assertFalse(s.state.getChallengeData().contains(ID), "No persisted visual snapshot");
            }
            var uuid = player.getUuid();
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                    .onPlayDisconnect(player.networkHandler, server);
            server.getPlayerManager().remove(player);
            context.assertTrue(server.getPlayerManager().getPlayerList().isEmpty(), "Empty-server fixture");
            s.activate(ID);
            player = s.player(uuid);
            assertIds(context, s.channel(player), List.of(ID));
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                    .onPlayDisconnect(player.networkHandler, server);
            server.getPlayerManager().remove(player);
            s.activate("no_sprint");
            player = s.player(uuid);
            assertIds(context, s.channel(player), List.of("no_sprint"));
            context.assertEquals(17, player.getInventory().getStack(8).getCount(), "Actual playerdata preserves inventory on same-UUID join");
            context.assertEquals(7, player.experienceLevel, "Actual playerdata preserves XP");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "invisible_mobs")
    public void issuedArmorPersistsAcrossBothPairEndings(TestContext context) {
        try (var s = new ChallengeScenario(context)) {
            var player = s.player();
            s.activate("double_challenge:invisible_mobs,netherite_mobs");
            assertIds(context, s.channel(player), List.of(ID, "netherite_mobs"));
            var mob = context.spawnEntity(net.minecraft.entity.EntityType.HUSK, new net.minecraft.util.math.BlockPos(1, 2, 1));
            try {
                mob.setAiDisabled(true);
                context.getWorld().tickEntity(mob);
                assertIssuedArmor(context, mob);
                context.assertFalse(mob.isInvisible(), "Issued armor pair does not add real invisibility");
                s.activate("netherite_mobs");
                assertIds(context, s.channel(player), List.of("netherite_mobs"));
                assertIssuedArmor(context, mob);
                s.activate("double_challenge:invisible_mobs,netherite_mobs");
                s.activate(ID);
                assertIds(context, s.channel(player), List.of(ID));
                assertIssuedArmor(context, mob);
                var later = context.spawnEntity(net.minecraft.entity.EntityType.HUSK, new net.minecraft.util.math.BlockPos(2, 2, 1));
                try {
                    later.setAiDisabled(true);
                    context.getWorld().tickEntity(later);
                    for (var slot : net.minecraft.entity.EquipmentSlot.values()) if (slot.isArmorSlot())
                        context.assertFalse(com.cursecycle.challenge.impl.NetheriteMobsChallenge.isIssued(later.getEquippedStack(slot)), "Ended companion does not issue new armor");
                } finally { later.discard(); }
            } finally { mob.discard(); }
        }
        context.complete();
    }

    private static void assertIssuedArmor(TestContext context, net.minecraft.entity.mob.MobEntity mob) {
        for (var slot : List.of(net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET))
            context.assertTrue(com.cursecycle.challenge.impl.NetheriteMobsChallenge.isIssued(mob.getEquippedStack(slot)), "Real birth hook issued and retained armor: " + slot);
    }
}
