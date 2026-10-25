package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.effect.EffectLedger;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import java.util.UUID;

public class NoBlockBreakingGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void contributionsAndPersistence(TestContext context) throws Exception {
        var server = context.getWorld().getServer();
        var state = CurseCycleState.getServerState(server);
        var original = state.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager());
        UUID uuid = UUID.randomUUID();
        var data = ConnectedClientData.createDefault(new GameProfile(uuid, "fatigue-test"), false);
        var player = new ServerPlayerEntity(server, context.getWorld(), data.gameProfile(), data.syncedOptions());
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(GameMode.SURVIVAL);
        var challenge = ChallengeRegistry.get("no_block_breaking");
        try {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 200, 2));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 500, 1));
            CycleManager.activateChallenge(server, state, "double_challenge:no_block_breaking,no_sprint");
            context.assertEquals(9, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Fatigue X immediately");
            for (int i = 0; i < 25; i++) { player.playerTick(); challenge.onTick(server, player); }
            context.assertEquals(9, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Refresh never raises level");
            CycleManager.activateChallenge(server, state, "no_sprint");
            context.assertEquals(2, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Weaker external effect restored");
            context.assertEquals(175, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getDuration(), "External duration elapsed while hidden");
            context.assertTrue(player.hasStatusEffect(StatusEffects.SPEED), "Other type survives cleanup");

            CycleManager.activateChallenge(server, state, "no_block_breaking");
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 100, 10));
            challenge.onTick(server, player);
            context.assertEquals(10, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Stronger external effect wins");
            CycleManager.activateChallenge(server, state, "no_sprint");
            context.assertEquals(10, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Stronger external effect survives");

            CycleManager.activateChallenge(server, state, "no_block_breaking");
            player.clearStatusEffects();
            context.assertTrue(!player.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Milk clears contributions");
            challenge.onTick(server, player);
            context.assertEquals(9, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Next challenge tick restores fatigue");
            player.setStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 100, 2), null);
            challenge.onTick(server, player);
            context.assertEquals(9, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Direct replacement cannot suppress owned effect until refresh threshold");
            player.changeGameMode(GameMode.CREATIVE);
            EffectLedger.of(player).reconcile();
            context.assertEquals(2, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Excluded mode preserves direct external contribution");
            player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
            player.changeGameMode(GameMode.SURVIVAL);
            challenge.onTick(server, player);

            NbtCompound saved = new NbtCompound();
            player.writeCustomDataToNbt(saved);
            player.readCustomDataFromNbt(saved);
            EffectLedger.of(player).reconcile();
            context.assertEquals(9, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Owned NBT reloads");
            var serialized = state.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager());
            var restored = CurseCycleState.createFromNbt(serialized, context.getWorld().getRegistryManager());
            context.assertEquals(state.getActivationSerial(), restored.getActivationSerial(), "Activation identity persists");
            var persistence = context.getWorld().getPersistentStateManager();
            state.markDirty();
            persistence.save();
            var disk = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            context.assertEquals(state.getActivationSerial(), disk.getCompound("data").getLong("activationSerial"),
                    "Activation identity is written to the real world state file");
            CycleManager.activateChallenge(server, state, "no_sprint");
            player.readCustomDataFromNbt(saved);
            EffectLedger.of(player).reconcile();
            context.assertTrue(!player.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Stale saved contribution removed after offline ending");

            // A stronger external chain can expire while the owned contribution remains.
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 80, 2));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 5, 10));
            CycleManager.activateChallenge(server, state, "no_block_breaking");
            for (int i = 0; i < 10; i++) { player.playerTick(); challenge.onTick(server, player); }
            CycleManager.activateChallenge(server, state, "no_sprint");
            context.assertEquals(2, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier(), "Expired external stronger effect reveals original hidden effect");
            context.assertEquals(70, player.getStatusEffect(StatusEffects.MINING_FATIGUE).getDuration(), "Hidden chain keeps its true remaining duration");
        } finally {
            server.getPlayerManager().remove(player);
            context.getWorld().getPersistentStateManager().set(CurseCycleState.STATE_KEY,
                    CurseCycleState.createFromNbt(original, context.getWorld().getRegistryManager()));
        }
        context.complete();
    }
}
