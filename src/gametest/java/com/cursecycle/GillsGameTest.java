package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.GillsChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;

public class GillsGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void actualAirDamagePersistenceAndRespawn(TestContext context) throws Exception {
        try (var scenario = new ChallengeScenario(context)) {
            var player = scenario.player();
            for (int i = 0; i < 61; i++) player.tick();
            scenario.activate("gills");
            var challenge = ChallengeRegistry.get("gills");
            context.assertEquals(300, player.getAir(), "New activation capacity");
            for (int i = 0; i < 340; i++) { player.tick(); GillsChallenge.updateBreathing(player); }
            context.assertEquals(0, player.getAir(), "Dry air exhausted");
            context.assertEquals(16F, player.getHealth(), "Two drowning pulses after capacity expires");
            challenge.onStart(player.getServer(), player);
            context.assertEquals(0, player.getAir(), "Repeated start does not refill");
            player.changeGameMode(GameMode.CREATIVE);
            player.setAir(300);
            player.changeGameMode(GameMode.SURVIVAL);
            challenge.onStart(player.getServer(), player);
            context.assertEquals(0, player.getAir(), "Mode switch does not refill saved supply");
            var pos = context.getAbsolutePos(new net.minecraft.util.math.BlockPos(3, 2, 3));
            context.getWorld().setBlockState(pos, Blocks.WATER.getDefaultState());
            context.getWorld().setBlockState(pos.up(), Blocks.WATER.getDefaultState());
            context.getWorld().setBlockState(pos.up(2), Blocks.WATER.getDefaultState());
            player.teleport(context.getWorld(), pos.getX()+0.5, pos.getY(), pos.getZ()+0.5, 0, 0);
            player.playerTick();
            context.assertTrue(player.isSubmergedIn(FluidTags.WATER), "Eyes really in water");
            context.assertEquals(0, player.getAir(), "Vanilla air calculation is disabled");
            GillsChallenge.updateBreathing(player);
            context.assertEquals(4, player.getAir(), "Own water recovery exactly four");
            var persistence = context.getWorld().getPersistentStateManager();
            persistence.save();
            var disk = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), context.getWorld().getRegistryManager());
            persistence.set(CurseCycleState.STATE_KEY, loaded);
            player.setAir(300);
            challenge.onPlayerJoin(player.getServer(), player);
            context.assertEquals(4, player.getAir(), "Disk roundtrip and join do not refill");
            player = scenario.respawn(player, true);
            context.assertEquals(4, player.getAir(), "Live respawn preserves air");
            player.kill();
            player = scenario.respawn(player, false);
            context.assertEquals(300, player.getAir(), "New life gets full supply");
            com.cursecycle.state.CycleManager.activateChallenge(player.getServer(), loaded, "no_sprint");
            context.assertTrue(!GillsChallenge.active(player), "Vanilla breathing restored after ending");
        }
        context.complete();
    }
}
