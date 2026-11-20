package validation;

import com.cursecycle.challenge.impl.BlackHoleChallenge;
import com.cursecycle.client.BlackHoleVisuals;
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
public final class BlackHoleProbe implements ClientModInitializer {
    private int stage, ticks, checks, totalTicks;
    private boolean fragmentsSeen;
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
        System.out.println("BLACK_HOLE_CAPTURE " + name);
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
                    if (Files.exists(Path.of("saves", "black-hole-visual-v2", "level.dat")))
                        client.createIntegratedServerLoader().start("black-hole-visual-v2", () -> { throw new AssertionError("Load cancelled"); });
                    else client.createIntegratedServerLoader().createAndStart("black-hole-visual-v2",
                            new LevelInfo("Black hole visual fixture", GameMode.SURVIVAL, false, Difficulty.NORMAL,
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
                            for (int x = -5; x <= 5; x++) for (int y = -60; y <= -56; y++)
                                world.setBlockState(new BlockPos(x, y, -5), Blocks.STONE.getDefaultState());
                            for (int i = 0; i < 4; i++) {
                                var pig = EntityType.PIG.create(world); pig.setPosition(4 + i, -59, 1); world.spawnEntity(pig);
                            }
                            CycleManager.activateChallenge(server, CurseCycleState.getServerState(server), BlackHoleChallenge.ID);
                            BlackHoleChallenge.tick(server);
                            var map = BlackHoleChallenge.class.getDeclaredField("FIELDS"); map.setAccessible(true);
                            var field = ((Map<?, ?>) map.get(null)).get(server);
                            var holes = (List<?>) get(field, "holes");
                            if (holes.isEmpty()) throw new AssertionError("No server hunter");
                            set(holes.getFirst(), "center", new Vec3d(0, -56, 0));
                            set(holes.getFirst(), "issued", (long) server.getTicks() - (Integer) get(field, "start"));
                            prepared = true;
                        } catch (Exception e) { throw new RuntimeException(e); }
                    });
                    return;
                }
                if (!prepared) return;
                ticks++; fragmentsSeen |= !BlackHoleVisuals.FRAGMENTS.isEmpty();
                switch (ticks) {
                    case 30 -> { check(!BlackHoleVisuals.HOLES.isEmpty(), "Real S2C state received"); capture(client, "formation"); }
                    case 100 -> capture(client, "active-day");
                    case 120 -> client.getServer().execute(() -> {
                        var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                        player.teleport(player.getServerWorld(), 20, -44, 42, 154, 16);
                    });
                    case 160 -> { check(fragmentsSeen, "Real consumed blocks animate"); capture(client, "field-radius"); }
                    case 170 -> { client.options.getGraphicsMode().setValue(GraphicsMode.FABULOUS); client.worldRenderer.reload(); }
                    case 200 -> capture(client, "fabulous");
                    case 210 -> client.getServer().execute(() -> client.getServer().getOverworld().setTimeOfDay(18000));
                    case 240 -> capture(client, "active-night");
                    case 250 -> client.getServer().execute(() -> {
                        var server = client.getServer();
                        CycleManager.activateChallenge(server, CurseCycleState.getServerState(server), "no_sprint");
                    });
                    case 270 -> {
                        check(BlackHoleVisuals.HOLES.isEmpty() && BlackHoleVisuals.FRAGMENTS.isEmpty(), "Stop clears real client");
                        capture(client, "stopped");
                        System.out.println("BLACK_HOLE_VISUAL_PROBE_PASSED checks=" + checks);
                        client.scheduleStop();
                    }
                }
                if (ticks > 400) throw new AssertionError("Probe timeout");
            } catch (Throwable failure) {
                failure.printStackTrace(); System.out.println("BLACK_HOLE_VISUAL_PROBE_FAILED");
                client.scheduleStop(); throw new RuntimeException(failure);
            }
        });
    }
}
