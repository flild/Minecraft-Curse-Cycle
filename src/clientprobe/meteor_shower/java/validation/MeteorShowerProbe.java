package validation;

import com.cursecycle.challenge.impl.MeteorShowerChallenge;
import com.cursecycle.client.MeteorShowerVisuals;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.file.*;
import java.util.*;

/** Isolated real integrated server, real networking and OpenGL captures. Excluded from production. */
public final class MeteorShowerProbe implements ClientModInitializer {
    private int stage, ticks, checks, totalTicks;
    private boolean meteorSeen, eruptionSeen, compactEruptionSeen;
    private final Set<Integer> compactIds = new HashSet<>();
    private volatile boolean prepared;
    private static Object get(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private void capture(MinecraftClient client, String name) throws Exception {
        var path = Path.of("..", "screenshots"); Files.createDirectories(path);
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) { image.writeTo(path.resolve(name + ".png")); }
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL state " + name);
        System.out.println("METEOR_SHOWER_CAPTURE " + name);
    }
    private static void wave(MinecraftClient client, boolean cave, boolean night) throws Exception {
        var server = client.getServer(); var world = server.getOverworld();
        world.setTimeOfDay(night ? 18000 : 6000);
        var player = server.getPlayerManager().getPlayer(client.player.getUuid());
        int center = cave ? 48 : 0;
        for (int x = center - 16; x <= center + 16; x++) for (int z = -16; z <= 16; z++) {
            world.setBlockState(new BlockPos(x, -61, z), Blocks.STONE.getDefaultState());
            if (cave) {
                world.setBlockState(new BlockPos(x, -53, z), Blocks.STONE.getDefaultState());
                if (x % 6 == 0 && z % 6 == 0) world.setBlockState(new BlockPos(x, -54, z), Blocks.GLOWSTONE.getDefaultState());
                if (x == center - 16 || x == center + 16 || Math.abs(z) == 16)
                    for (int y = -60; y < -53; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.STONE.getDefaultState());
            }
        }
        player.teleport(world, center, -60, 0, 180, 0);
        MeteorShowerChallenge.tick(server);
        var map = MeteorShowerChallenge.class.getDeclaredField("STORMS"); map.setAccessible(true);
        var storm = ((Map<?, ?>) map.get(null)).get(server);
        ((List<?>) get(storm, "meteors")).clear();
        var session = ((Map<?, ?>) get(storm, "sessions")).get(player.getUuid());
        var method = Arrays.stream(MeteorShowerChallenge.class.getDeclaredMethods()).filter(m -> m.getName().equals("issue")).findFirst().orElseThrow();
        method.setAccessible(true); method.invoke(null, storm, session, Integer.toUnsignedLong(server.getTicks() - (Integer) get(storm, "start")));
        if (((List<?>) get(storm, "meteors")).size() < 5) throw new AssertionError("No complete real wave");
        set(session, "next", Long.MAX_VALUE);
        set(session, "remaining", 0);
        player.teleport(world, center, cave ? -58.5 : -52, cave ? 13 : 24, 180, cave ? 2 : 12);
    }
    private static void compact(MinecraftClient client, boolean cell) throws Exception {
        var server = client.getServer(); var world = server.getOverworld(); int center = cell ? 144 : 96;
        for (int cx = (center - 32) >> 4; cx <= (center + 32) >> 4; cx++) for (int cz = -2; cz <= 2; cz++) world.getChunk(cx, cz);
        for (int x = -13; x <= 13; x++) for (int z = -2; z <= 2; z++) for (int y = -61; y <= -58; y++) {
            boolean air = (cell ? Math.abs(x) <= 1 && Math.abs(z) <= 1 : z == 0) && (y == -60 || y == -59);
            world.setBlockState(new BlockPos(center + x, y, z), (air ? Blocks.AIR : Blocks.OBSIDIAN).getDefaultState());
        }
        var player = server.getPlayerManager().getPlayer(client.player.getUuid());
        player.teleport(world, center + .5, -60, .5, cell ? 180 : 90, cell ? 65 : 18);
        player.setVelocity(Vec3d.ZERO);
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 1200, 0, false, false));
        CycleManager.activateChallenge(server, CurseCycleState.getServerState(server), MeteorShowerChallenge.ID);
        // Let the registered tick schedule every warning and continuation normally.
    }
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (++totalTicks > 1800) throw new AssertionError("Client/world load timeout");
                if (stage == 0) {
                    if (!(client.currentScreen instanceof TitleScreen) || client.getOverlay() != null) return;
                    GLFW.glfwHideWindow(client.getWindow().getHandle()); client.options.hudHidden = true;
                    client.options.getGraphicsMode().setValue(GraphicsMode.FANCY);
                    client.options.pauseOnLostFocus = false; client.options.getFov().setValue(75);
                    stage = 1;
                    if (Files.exists(Path.of("saves", "meteor-shower-visual-v2", "level.dat")))
                        client.createIntegratedServerLoader().start("meteor-shower-visual-v2", () -> { throw new AssertionError("Load cancelled"); });
                    else client.createIntegratedServerLoader().createAndStart("meteor-shower-visual-v2",
                            new LevelInfo("Meteor shower visual fixture", GameMode.SURVIVAL, false, Difficulty.NORMAL,
                                    true, new GameRules(), DataConfiguration.SAFE_MODE), new GeneratorOptions(42, false, false),
                            registries -> registries.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder(),
                            client.currentScreen);
                    return;
                }
                if (stage == 1) {
                    if (client.player == null || client.world == null || client.getServer() == null || client.getOverlay() != null) return;
                    if (++ticks < 80) return;
                    stage = 2; ticks = 0; client.setScreen(null);
                    client.getServer().execute(() -> {
                        try {
                            var server = client.getServer(); var world = server.getOverworld();
                            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
                            var oldMobs = new ArrayList<net.minecraft.entity.Entity>();
                            for (var entity : world.iterateEntities()) if (entity instanceof net.minecraft.entity.mob.MobEntity) oldMobs.add(entity);
                            oldMobs.forEach(net.minecraft.entity.Entity::discard);
                            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server); world.setTimeOfDay(6000);
                            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) world.getChunk(x, z);
                            var player = server.getPlayerManager().getPlayer(client.player.getUuid());
                            player.teleport(world, 0, -58, 32, 180, 0); player.getAbilities().invulnerable = true;
                            player.getAbilities().allowFlying = true; player.getAbilities().flying = true;
                            player.setNoGravity(true); player.sendAbilitiesUpdate();
                            CycleManager.activateChallenge(server, CurseCycleState.getServerState(server), MeteorShowerChallenge.ID);
                            wave(client, false, false);
                            prepared = true;
                        } catch (Exception e) { throw new RuntimeException(e); }
                    });
                    return;
                }
                if (!prepared) return;
                ticks++;
                meteorSeen |= MeteorShowerVisuals.STRIKES.values().stream().anyMatch(s -> !s.sample.geyser() && s.age(0) >= 60 && s.impactAge(0) < 0);
                eruptionSeen |= MeteorShowerVisuals.STRIKES.values().stream().anyMatch(s -> s.sample.geyser() && s.impactAge(0) >= 0);
                if (ticks > 600) for (var s : MeteorShowerVisuals.STRIKES.values()) {
                    compactIds.add(s.sample.id()); compactEruptionSeen |= s.impactAge(0) >= 0;
                }
                switch (ticks) {
                    case 25 -> { check(MeteorShowerVisuals.STRIKES.size() >= 5, "Real full-wave S2C"); capture(client, "surface-warning"); }
                    case 70 -> capture(client, "surface-eruption");
                    case 82 -> capture(client, "surface-meteor");
                    case 110 -> client.getServer().execute(() -> { try { wave(client, false, true); } catch (Exception e) { throw new RuntimeException(e); } });
                    case 140 -> capture(client, "night-warning");
                    case 185 -> { check(meteorSeen && eruptionSeen, "Real flight and eruption phases"); capture(client, "night-eruption"); }
                    case 220 -> client.getServer().execute(() -> { try { wave(client, true, false); } catch (Exception e) { throw new RuntimeException(e); } });
                    case 245 -> { check(!MeteorShowerVisuals.STRIKES.isEmpty() && MeteorShowerVisuals.STRIKES.values().stream().allMatch(s -> s.sample.geyser()), "Underground floor erupts"); capture(client, "cave-warning"); }
                    case 265 -> { client.options.getGraphicsMode().setValue(GraphicsMode.FABULOUS); client.worldRenderer.reload(); }
                    case 292 -> capture(client, "cave-eruption-fabulous");
                    case 310 -> client.getServer().execute(() -> CycleManager.activateChallenge(client.getServer(), CurseCycleState.getServerState(client.getServer()), "no_sprint"));
                    case 330 -> {
                        check(MeteorShowerVisuals.STRIKES.isEmpty(), "Stop clears actual client"); capture(client, "stopped");
                    }
                    case 340 -> client.getServer().execute(() -> { try { compact(client, false); } catch (Exception e) { throw new RuntimeException(e); } });
                    case 450 -> { check(MeteorShowerVisuals.STRIKES.size() >= 5, "Real 1x2 tunnel wave"); capture(client, "tunnel-warning"); }
                    case 485 -> { check(MeteorShowerVisuals.STRIKES.values().stream().anyMatch(s -> s.sample.geyser() && s.impactAge(0) >= 0 && s.sample.height() < 2), "Real low ceiling eruption"); capture(client, "tunnel-eruption"); }
                    case 530 -> { compactIds.clear(); client.getServer().execute(() -> { try { compact(client, true); } catch (Exception e) { throw new RuntimeException(e); } }); }
                    case 635 -> { check(MeteorShowerVisuals.STRIKES.size() == 1, "Cramped cell first portion"); capture(client, "small-room-warning"); }
                    case 685 -> capture(client, "small-room-eruption");
                    case 730 -> { check(compactEruptionSeen && compactIds.size() == 2 && MeteorShowerVisuals.STRIKES.size() == 1
                            && MeteorShowerVisuals.STRIKES.values().stream().allMatch(s -> s.sample.geyser() && s.age(0) < 60 && s.sample.height() < 2),
                            "Real clock and networking deliver a second cramped portion with a fresh warning"); capture(client, "small-room-next-warning"); }
                    case 750 -> client.getServer().execute(() -> CycleManager.activateChallenge(client.getServer(), CurseCycleState.getServerState(client.getServer()), "no_sprint"));
                    case 770 -> {
                        check(MeteorShowerVisuals.STRIKES.isEmpty(), "Stop clears cramped portion and pending remainder");
                        System.out.println("METEOR_SHOWER_UNDERGROUND_PROBE_PASSED checks=" + checks); client.scheduleStop();
                    }
                }
                if (ticks > 850) throw new AssertionError("Probe timeout");
            } catch (Throwable failure) {
                failure.printStackTrace(); System.out.println("METEOR_SHOWER_VISUAL_PROBE_FAILED");
                client.scheduleStop(); throw new RuntimeException(failure);
            }
        });
    }
}
