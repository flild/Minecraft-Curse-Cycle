package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.SunlightBurnChallenge;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ReadableContainer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SunlightBurnGameTest implements FabricGameTest {
    private static final String ID = SunlightBurnChallenge.ID;

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "sunlight_burn")
    public void environmentVanillaFireAndLifecycle(TestContext c) throws Exception {
        try (var a = new Arena(c)) {
            var p = a.player();
            p.setFireTicks(0);
            a.world().setTimeOfDay(6000);
            a.s.activate(ID);
            c.assertEquals(0, p.getFireTicks(), "Start waits for first server tick");
            a.check(p); c.assertEquals(80, p.getFireTicks(), "First tick ignites");
            p.setFireTicks(300); a.check(p);
            c.assertEquals(300, p.getFireTicks(), "Longer external fire preserved");
            p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            p.setFireTicks(0); a.check(p); c.assertEquals(80, p.getFireTicks(), "Helmet is no shelter");
            c.assertEquals(0, p.getEquippedStack(EquipmentSlot.HEAD).getDamage(), "No special helmet wear");
            for (var block : List.of(Blocks.STONE, Blocks.GLASS, Blocks.OAK_LEAVES, Blocks.OAK_SLAB)) {
                a.block(a.roof, block.getDefaultState());
                c.assertFalse(SunlightBurnChallenge.exposed(p), "Collision roof " + block);
                p.setFireTicks(0); a.check(p); c.assertEquals(0, p.getFireTicks(), "Roof prevents renewal");
            }
            a.block(a.roof, Blocks.OAK_FENCE.getDefaultState());
            c.assertFalse(SunlightBurnChallenge.exposed(p), "Fence post intersects central ray");
            p.refreshPositionAndAngles(a.base.getX()+.05, a.base.getY(), a.base.getZ()+.05, 0, 0);
            c.assertTrue(SunlightBurnChallenge.exposed(p), "Empty portion of nonempty shape is no roof");
            a.position(p);
            a.block(a.roof, Blocks.TORCH.getDefaultState());
            c.assertTrue(SunlightBurnChallenge.exposed(p), "Empty torch collision is no roof");
            a.block(a.roof, Blocks.SHORT_GRASS.getDefaultState());
            c.assertTrue(SunlightBurnChallenge.exposed(p), "Empty grass collision is no roof");
            a.block(a.roof, Blocks.AIR.getDefaultState());
            p.refreshPositionAndAngles(p.getX(), a.world().getTopY()+10, p.getZ(), 0, 0);
            c.assertTrue(SunlightBurnChallenge.exposed(p), "Above build height has no roof");
            a.position(p);
            for (long time : List.of(0L, 11999L, 24000L, 6000L)) {
                a.world().setTimeOfDay(time); c.assertTrue(SunlightBurnChallenge.exposed(p), "Day " + time);
            }
            for (long time : List.of(12000L, 18000L, 23999L)) {
                a.world().setTimeOfDay(time); p.setFireTicks(0); a.check(p);
                c.assertEquals(0, p.getFireTicks(), "Night " + time);
            }
            a.world().setTimeOfDay(6000);
            a.biome(BiomeKeys.PLAINS); a.weather(true);
            c.assertTrue(a.world().hasRain(BlockPos.ofFloored(p.getEyePos())), "Rain reaches eyes");
            c.assertFalse(SunlightBurnChallenge.exposed(p), "Local rain protects");
            a.biome(BiomeKeys.SNOWY_PLAINS);
            c.assertEquals(Biome.Precipitation.SNOW, a.world().getBiome(BlockPos.ofFloored(p.getEyePos())).value()
                    .getPrecipitation(BlockPos.ofFloored(p.getEyePos())), "Snowy fixture");
            c.assertFalse(SunlightBurnChallenge.exposed(p), "Local snow protects too");
            a.biome(BiomeKeys.DESERT);
            c.assertTrue(SunlightBurnChallenge.exposed(p), "Dry biome during world rain still burns");
            a.weather(false);
            a.block(a.base, Blocks.WATER.getDefaultState());
            a.block(a.base.up(), Blocks.WATER.getDefaultState());
            a.block(a.base.up(2), Blocks.WATER.getDefaultState());
            p.playerTick();
            c.assertTrue(p.isSubmergedIn(FluidTags.WATER), "Real submerged eye state");
            p.setFireTicks(0); a.check(p); c.assertEquals(0, p.getFireTicks(), "Water stops renewal");
            for (int y = 0; y < 3; y++) a.block(a.base.up(y), Blocks.AIR.getDefaultState());
            a.position(p); p.playerTick();
            for (var mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR, GameMode.ADVENTURE, GameMode.SURVIVAL)) {
                p.changeGameMode(mode); p.setFireTicks(0); a.check(p);
                c.assertEquals(mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR ? 0 : 80,
                        p.getFireTicks(), "Mode " + mode);
            }
            a.challenge.setEnabled(false); p.setFireTicks(0); a.check(p);
            c.assertEquals(0, p.getFireTicks(), "Disabled means no fire"); a.challenge.setEnabled(true);
            for (var key : List.of(World.NETHER, World.END)) {
                var other = p.getServer().getWorld(key);
                p.teleport(other, a.base.getX()+.5, 80, a.base.getZ()+.5, 0, 0);
                p.onTeleportationDone(); // Embedded client cannot acknowledge the dimension transition.
                p.setFireTicks(200); a.check(p); c.assertEquals(200, p.getFireTicks(), "External fire in " + key);
                p.setFireTicks(0); a.check(p); c.assertEquals(0, p.getFireTicks(), "No sunlight in " + key);
            }
            p.teleport(a.world(), a.base.getX()+.5, a.base.getY(), a.base.getZ()+.5, 0, 0);
            p.onTeleportationDone();
            // Exercise actual vanilla ticking, not a separate solar damage path.
            a.s.activate("test_easy");
            for (int i = 0; i < 70; i++) p.tick();
            a.position(p); p.setHealth(20); p.timeUntilRegen = 0; p.setFireTicks(80);
            p.playerTick(); c.assertTrue(p.getHealth() < 20, "Vanilla fire deals health damage");
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 1000, 0));
            p.setHealth(20); p.timeUntilRegen = 0; p.setFireTicks(80); p.playerTick();
            c.assertEquals(20F, p.getHealth(), "Fire resistance prevents vanilla damage");
            c.assertTrue(p.hasStatusEffect(StatusEffects.FIRE_RESISTANCE), "Resistance retained");
            p.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1000, 0));
            p.setAbsorptionAmount(4); p.setHealth(20); p.timeUntilRegen = 0; p.setFireTicks(80); p.playerTick();
            c.assertTrue(p.getAbsorptionAmount() > 0 && p.getAbsorptionAmount() < 4, "Vanilla absorption consumed");
            c.assertEquals(20F, p.getHealth(), "Absorption shields health");
            a.s.activate("double_challenge:sunlight_burn,no_sprint"); a.check(p);
            p.setSprinting(true); c.assertFalse(p.isSprinting(), "No sprint remains effective");
            p.setFireTicks(250); a.s.activate("no_sprint"); a.check(p);
            c.assertEquals(250, p.getFireTicks(), "Ending leaves external fire");
            p.setFireTicks(0); a.check(p); c.assertEquals(0, p.getFireTicks(), "No ignition after ending");
            a.s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID); a.check(p);
            c.assertEquals(80, p.getFireTicks(), "Personal activation supported");
            a.s.state.getPersonalActiveChallengeIds(p.getUuid()).clear();
            a.s.activate(ID);
            var persistence = a.world().getPersistentStateManager(); persistence.save();
            var disk = persistence.readNbt(CurseCycleState.STATE_KEY, null,
                    SharedConstants.getGameVersion().getSaveVersion().getId());
            var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), a.world().getRegistryManager());
            c.assertTrue(loaded.getActiveChallengeIds().contains(ID), "Activation saved to disk");
            c.assertTrue(loaded.getChallengeData().isEmpty(), "No solar clocks/fire snapshot in NBT");
            persistence.set(CurseCycleState.STATE_KEY, loaded);
            p.setFireTicks(0); new SunlightBurnChallenge().onTick(p.getServer(), p);
            c.assertEquals(80, p.getFireTicks(), "Fresh implementation after disk load checks first tick");
            p = a.s.respawn(p, true); a.position(p); p.setFireTicks(0); a.check(p);
            c.assertEquals(80, p.getFireTicks(), "Replacement player resumes");
            p.setHealth(0); p.setFireTicks(0); a.check(p);
            c.assertEquals(0, p.getFireTicks(), "Dead player excluded");
            p = a.s.respawn(p, false); a.position(p); p.setFireTicks(0); a.check(p);
            c.assertEquals(80, p.getFireTicks(), "New life resumes");
            var uuid = p.getUuid(); p.getServer().getPlayerManager().remove(p);
            p = a.s.player(uuid); a.position(p); p.setFireTicks(0); a.check(p);
            c.assertEquals(80, p.getFireTicks(), "Same UUID reconnect resumes current activation");
            p.getServer().getPlayerManager().remove(p);
            com.cursecycle.state.CycleManager.activateChallenge(a.world().getServer(), loaded, "no_sprint");
            p = a.s.player(uuid); a.position(p); p.setFireTicks(0); a.check(p);
            c.assertEquals(0, p.getFireTicks(), "Ended offline does not resume");
            var source = a.world().getServer().getCommandSource().withLevel(4);
            for (var cmd : List.of("fdc list", "fdc activate sunlight_burn", "fdc active"))
                a.world().getServer().getCommandManager().executeWithPrefix(source, cmd);
            c.assertEquals(1L, ChallengeRegistry.getAll().stream().filter(it -> it.getId().equals(ID)).count(), "Registered once");
        }
        c.waitAndRun(5, c::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "sunlight_burn_clock", tickLimit = 120)
    public void realServerTicksAndNoExtraTimePulses(TestContext c) {
        var a = new Arena(c);
        var p = a.player(); var second = a.player();
        a.position(p); a.position(second);
        // Embedded clients send no movement packets; explicitly drive vanilla player ticks.
        c.runAtEveryTick(() -> {
            if (!a.closed) { a.position(p); a.position(second); p.playerTick(); second.playerTick(); }
        });
        a.at(1, () -> {
            a.world().setTimeOfDay(6000); a.s.activate(ID);
            a.s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID);
        });
        a.at(2, () -> {
            c.assertTrue(p.getFireTicks() >= 79, "First actual server tick ignited");
            c.assertTrue(second.getFireTicks() >= 79, "Independent second player");
            p.setFireTicks(0); second.setFireTicks(0);
            a.world().setTimeOfDay(18000);
        });
        a.at(10, () -> { a.world().setTimeOfDay(6000); });
        a.at(21, () -> {
            c.assertTrue(p.getFireTicks() <= 0, "19 ticks: time commands do not add a pulse");
            c.assertTrue(second.getFireTicks() <= 0, "Independent schedule at 19 ticks");
        });
        a.at(22, () -> {
            c.assertTrue(p.getFireTicks() >= 79, "20 tick interval");
            c.assertTrue(second.getFireTicks() >= 79, "Second 20 tick interval");
            p.setFireTicks(0); a.challenge.onTick(p.getServer(), p);
            c.assertEquals(0, p.getFireTicks(), "Same server tick duplicate ignored");
            a.s.state.getPersonalActiveChallengeIds(p.getUuid()).clear();
            a.s.activate("no_sprint");
        });
        a.at(110, () -> {
            c.assertTrue(second.getFireTicks() <= 0, "Last solar fire expired naturally after stop");
            c.assertTrue(p.getFireTicks() <= 0, "No new fire after stop");
            a.close(); c.waitAndRun(5, c::complete);
        });
    }

    private static final class Arena implements AutoCloseable {
        final TestContext c;
        final ChallengeScenario s;
        final SunlightBurnChallenge challenge;
        final BlockPos base, roof;
        final long time;
        final boolean rain, thunder, daylight, weatherCycle;
        final float rainGradient, thunderGradient;
        final int rainTime, thunderTime, clearWeatherTime;
        final Map<BlockPos, net.minecraft.block.BlockState> blocks = new LinkedHashMap<>();
        final Map<ChunkSection, ReadableContainer<RegistryEntry<Biome>>> biomes = new LinkedHashMap<>();
        boolean closed;
        Arena(TestContext c) {
            this.c = c; s = new ChallengeScenario(c);
            challenge = (SunlightBurnChallenge) ChallengeRegistry.get(ID);
            base = c.getAbsolutePos(new BlockPos(4, 25, 4)); roof = base.up(4);
            time = world().getTimeOfDay(); rain = world().isRaining(); thunder = world().isThundering();
            rainGradient = world().getRainGradient(1); thunderGradient = world().getThunderGradient(1);
            var properties = (net.minecraft.world.level.ServerWorldProperties) world().getLevelProperties();
            rainTime = properties.getRainTime(); thunderTime = properties.getThunderTime();
            clearWeatherTime = properties.getClearWeatherTime();
            daylight = world().getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
            weatherCycle = world().getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE);
            world().getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, world().getServer());
            world().getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, world().getServer());
            world().setTimeOfDay(6000); weather(false);
        }
        net.minecraft.server.world.ServerWorld world() { return c.getWorld(); }
        ServerPlayerEntity player() { var p = s.player(); p.setNoGravity(true); position(p); return p; }
        void position(ServerPlayerEntity p) {
            if (closed) return;
            p.refreshPositionAndAngles(base.getX()+.5, base.getY(), base.getZ()+.5, 0, 0); p.setVelocity(Vec3d.ZERO);
        }
        void block(BlockPos pos, net.minecraft.block.BlockState state) {
            blocks.putIfAbsent(pos, world().getBlockState(pos)); world().setBlockState(pos, state, Block.NOTIFY_LISTENERS);
        }
        void check(ServerPlayerEntity p) { challenge.onStart(p.getServer(), p); challenge.onTick(p.getServer(), p); }
        void weather(boolean raining) {
            world().setWeather(0, 6000, raining, false);
            world().setRainGradient(raining ? 1 : 0); world().setThunderGradient(0);
        }
        void biome(net.minecraft.registry.RegistryKey<Biome> key) {
            var biome = world().getRegistryManager().get(RegistryKeys.BIOME).entryOf(key);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                var chunk = world().getChunk((base.getX() >> 4)+x, (base.getZ() >> 4)+z);
                int middle = world().getSectionIndex(base.getY());
                for (int y = middle - 1; y <= middle + 1; y++) {
                    var section = chunk.getSection(y);
                    biomes.computeIfAbsent(section, it -> it.getBiomeContainer().slice());
                    section.populateBiomes((bx, by, bz, sampler) -> biome, null, 0, 0, 0);
                }
            }
        }
        void at(long tick, Runnable action) {
            c.runAtTick(tick, () -> {
                try { action.run(); }
                catch (Exception | AssertionError failure) { close(); throw new RuntimeException("sunlight_burn tick " + tick + ": " + failure.getMessage(), failure); }
            });
        }
        @Override public void close() {
            if (closed) return; closed = true;
            challenge.setEnabled(true); s.close();
            blocks.forEach((pos, state) -> world().setBlockState(pos, state, Block.NOTIFY_LISTENERS));
            biomes.forEach((section, original) -> section.populateBiomes(
                    (x, y, z, sampler) -> original.get(x & 3, y & 3, z & 3), null, 0, 0, 0));
            world().setTimeOfDay(time); world().setWeather(clearWeatherTime, rainTime, rain, thunder);
            ((net.minecraft.world.level.ServerWorldProperties) world().getLevelProperties()).setThunderTime(thunderTime);
            world().setRainGradient(rainGradient); world().setThunderGradient(thunderGradient);
            world().getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(daylight, world().getServer());
            world().getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(weatherCycle, world().getServer());
        }
    }
}
