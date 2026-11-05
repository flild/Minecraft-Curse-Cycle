package validation;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.ForcedThirdPersonCamera;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import sun.misc.Unsafe;

import java.lang.reflect.Method;
import java.util.List;

/** Calls the transformed vanilla getFov, without an OpenGL window or a live world. */
public final class TinyFovProbe implements PreLaunchEntrypoint {
    private static int checks;
    private static boolean alive = true;
    private static GameMode mode = GameMode.SURVIVAL;
    private static CameraSubmersionType submersion = CameraSubmersionType.NONE;
    private static ClientPlayerEntity player;
    private static GameRenderer renderer;
    private static Camera camera;
    private static Method getFov;

    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isDead() { return !alive; }
    }

    public static class Manager extends ClientPlayerInteractionManager {
        public Manager() { super(null, null); }
        @Override public GameMode getCurrentGameMode() { return mode; }
    }

    public static class ProbeCamera extends Camera {
        @Override public Entity getFocusedEntity() { return player; }
        @Override public CameraSubmersionType getSubmersionType() { return submersion; }
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        var f = owner.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static void same(double expected, double actual, String why) {
        check(Math.abs(expected - actual) < 1e-9, why + ": expected=" + expected + " actual=" + actual);
    }

    private static void sync(String... ids) {
        ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of());
    }

    private static double fov(float delta, boolean scene) throws Exception {
        return (double) getFov.invoke(renderer, camera, delta, scene);
    }

    private static SimpleOption<Integer> integerOption(int value) {
        return new SimpleOption<>("probe.integer", SimpleOption.emptyTooltip(), (text, v) -> Text.empty(),
                new SimpleOption.ValidatingIntSliderCallbacks(0, 120), value, v -> {});
    }

    private static SimpleOption<Double> scaleOption(double value) {
        return new SimpleOption<>("probe.scale", SimpleOption.emptyTooltip(), (text, v) -> Text.empty(),
                SimpleOption.DoubleSliderCallbacks.INSTANCE, value, v -> {});
    }

    public static class Context extends net.minecraft.client.gui.DrawContext {
        Context(MinecraftClient client) { super(client, null); }
        @Override public int getScaledWindowWidth() { return 800; }
        @Override public int getScaledWindowHeight() { return 600; }
    }

    // Invoke the actual merged production handlers, with no GL or replacement Mixin.
    // This proves composition at their boundary, not a complete renderWorld/HUD frame.
    private static void companions(MinecraftClient client) throws Exception {
        var roll = java.util.Arrays.stream(GameRenderer.class.getDeclaredMethods())
                .filter(m -> m.getName().contains("cursecycle$rollProjection")).findFirst().orElseThrow();
        roll.setAccessible(true);
        var hudClass = net.minecraft.client.gui.hud.InGameHud.class;
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var hud = ((Unsafe) unsafeField.get(null)).allocateInstance(hudClass);
        set(hud, hudClass, "client", client);
        var offset = java.util.Arrays.stream(hudClass.getDeclaredMethods())
                .filter(m -> m.getName().contains("cursecycle$offsetCrosshair")).findFirst().orElseThrow();
        offset.setAccessible(true);
        for (boolean tiny : new boolean[]{true, false}) {
            for (boolean companion : new boolean[]{true, false}) {
                sync(tiny ? "tiny_fov" : "no_sprint", companion ? "upside_down_camera" : "no_sprint");
                double degrees = fov(1, true);
                same(tiny ? 15 : 80, degrees, "FOV and roll: both independent endings");
                var projection = new org.joml.Matrix4f().perspective((float)Math.toRadians(degrees), 4f/3, .05f, 128);
                var expected = new org.joml.Matrix4f(projection);
                if (companion) expected.rotateZ((float)Math.PI);
                var actual = (org.joml.Matrix4f)roll.invoke(renderer, projection);
                check(expected.equals(actual, 1e-6f), "Actual injected roll preserves FOV projection");
                sync(tiny ? "tiny_fov" : "no_sprint", companion ? "offset_crosshair" : "no_sprint");
                var context = new Context(client);
                var originalMatrix = new org.joml.Matrix4f(context.getMatrices().peek().getPositionMatrix());
                int[] calls = {0};
                com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original = args -> {
                    calls[0]++;
                    var matrix = context.getMatrices().peek().getPositionMatrix();
                    same(companion ? 80 : 0, matrix.m30(), "Actual crosshair wrapper X");
                    same(companion ? 40 : 0, matrix.m31(), "Actual crosshair wrapper Y");
                    return null;
                };
                offset.invoke(hud, context, net.minecraft.client.render.RenderTickCounter.ZERO, original);
                check(calls[0] == 1, "Crosshair drawn exactly once");
                check(originalMatrix.equals(context.getMatrices().peek().getPositionMatrix()), "HUD matrix restored");
                same(tiny ? 15 : 80, fov(1, true), "Crosshair never changes scene FOV");
            }
        }
        for (int cycle = 0; cycle < 32; cycle++) {
            ClientDataHolder.reset(); same(80, fov(1, true), "Disconnect/reset removes override");
            sync("no_sprint"); same(80, fov(1, true), "Offline end cannot restore override");
            optionsForCycle(client, cycle);
        }
    }

    private static void optionsForCycle(MinecraftClient client, int cycle) throws Exception {
        int latest = 70 + cycle;
        set(client.options, GameOptions.class, "fov", integerOption(latest));
        for (int duplicate = 0; duplicate < 50; duplicate++) {
            sync("tiny_fov", "tiny_fov", "no_sprint");
            same(15, fov(1, true), "Repeated sync never compounds override");
        }
        sync("no_sprint"); same(latest, fov(1, true), "Current option used after every end");
        set(client.options, GameOptions.class, "fov", integerOption(80));
    }

    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion();
            net.minecraft.Bootstrap.initialize();
            var f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            var unsafe = (Unsafe) f.get(null);
            var client = (MinecraftClient) unsafe.allocateInstance(MinecraftClient.class);
            var options = (GameOptions) unsafe.allocateInstance(GameOptions.class);
            player = (Player) unsafe.allocateInstance(Player.class);
            var world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class);
            var manager = (Manager) unsafe.allocateInstance(Manager.class);
            renderer = (GameRenderer) unsafe.allocateInstance(GameRenderer.class);
            camera = new ProbeCamera();
            set(client, MinecraftClient.class, "options", options);
            client.player = player;
            client.world = world;
            client.interactionManager = manager;
            set(renderer, GameRenderer.class, "client", client);
            set(options, GameOptions.class, "guiScale", integerOption(3));
            getFov = GameRenderer.class.getDeclaredMethod("getFov", Camera.class, float.class, boolean.class);
            getFov.setAccessible(true);
            int comparisons = 0;
            // Inject final multiplier fixtures: neutral, widening, bow-like narrowing and spyglass-like zoom.
            // Actual item use/sprint/attributes are not simulated by this headless probe.
            for (int base : new int[]{30, 70, 80, 90, 110}) {
                set(options, GameOptions.class, "fov", integerOption(base));
                for (var perspective : Perspective.values()) {
                    set(options, GameOptions.class, "perspective", perspective);
                    for (float multiplier : new float[]{.1f, .85f, 1f, 1.3f, 1.5f}) {
                        set(renderer, GameRenderer.class, "lastFovMultiplier", .9f);
                        set(renderer, GameRenderer.class, "fovMultiplier", multiplier);
                        for (var immersion : CameraSubmersionType.values()) {
                            submersion = immersion;
                            for (double scale : new double[]{0, .5, 1}) {
                                set(options, GameOptions.class, "fovEffectScale", scaleOption(scale));
                                for (float delta : new float[]{0, .5f, 1}) {
                                    sync("no_sprint");
                                    double vanilla = fov(delta, true);
                                    double hand = fov(delta, false);
                                    sync("tiny_fov", "tiny_fov", "no_sprint");
                                    same(15, fov(delta, true), "Final scene FOV");
                                    same(hand, fov(delta, false), "Independent hand FOV");
                                    check(options.getFov().getValue() == base
                                            && options.getGuiScale().getValue() == 3
                                            && options.getPerspective() == perspective
                                            && options.getFovEffectScale().getValue() == scale,
                                            "Current user options remain untouched");
                                    sync("no_sprint");
                                    same(vanilla, fov(delta, true), "Stop immediately resumes vanilla modifiers");
                                    comparisons++;
                                }
                            }
                        }
                    }
                }
            }

            submersion = CameraSubmersionType.NONE;
            set(renderer, GameRenderer.class, "lastFovMultiplier", 1f);
            set(renderer, GameRenderer.class, "fovMultiplier", 1f);
            set(options, GameOptions.class, "fov", integerOption(90));
            sync("tiny_fov");
            same(15, fov(1, true), "Active at initial setting 90");
            set(options, GameOptions.class, "fov", integerOption(80));
            same(15, fov(1, true), "Setting edited during activation");
            sync("no_sprint");
            same(80, fov(1, true), "Latest setting 80 used after stop, without an old snapshot");
            for (var gm : GameMode.values()) {
                mode = gm;
                sync("tiny_fov");
                same(gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE ? 15 : 80,
                        fov(1, true), "Mode " + gm);
            }
            mode = GameMode.SURVIVAL;
            alive = false;
            sync("no_sprint");
            double deadVanilla = fov(1, true);
            sync("tiny_fov");
            same(deadVanilla, fov(1, true), "Dead object uses vanilla death FOV");
            alive = true;
            set(player, Entity.class, "removalReason", Entity.RemovalReason.DISCARDED);
            same(80, fov(1, true), "Removed player");
            player = (Player) unsafe.allocateInstance(Player.class);
            client.player = player;
            same(15, fov(1, true), "Respawn/new player object");
            client.world = null;
            same(80, fov(1, true), "No world");
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class);
            same(15, fov(1, true), "Replacement world/dimension");
            client.player = null;
            same(80, fov(1, true), "No player");
            client.player = player;
            client.interactionManager = null;
            same(80, fov(1, true), "No interaction manager");
            client.interactionManager = manager;
            mode = null;
            same(80, fov(1, true), "Unknown mode");
            mode = GameMode.SURVIVAL;
            ClientDataHolder.reset();
            same(80, fov(1, true), "Disconnect/unsynced");
            sync("no_sprint");
            same(80, fov(1, true), "Ended while offline");
            sync("tiny_fov");
            same(15, fov(1, true), "Fresh active sync");
            set(renderer, GameRenderer.class, "renderingPanorama", true);
            same(90, fov(1, true), "Vanilla panorama");
            same(90, fov(1, false), "Panorama hand query");
            set(renderer, GameRenderer.class, "renderingPanorama", false);
            // Exercise the existing perspective setter Mixin alongside the new FOV Mixin.
            set(null, MinecraftClient.class, "instance", client);
            for (String id : List.of("forced_third_person_front", "forced_third_person_rpg")) {
                sync("tiny_fov", id);
                var forced = ForcedThirdPersonCamera.resolvePerspective(ClientDataHolder.getActiveChallengeIds());
                options.setPerspective(Perspective.FIRST_PERSON);
                check(options.getPerspective() == forced, "Existing forced perspective still wins");
                same(15, fov(1, true), "FOV independent of forced perspective");
            }
            companions(client);
            System.out.println("TINY_FOV_CLIENT_PROBE_PASSED: " + comparisons
                    + " transformed vanilla getFov comparisons; checks=" + checks
                    + "; hand FOV, settings edits, modes, lifecycle, panorama, forced perspectives, actual roll/crosshair handlers, 32 reset cycles."
                    + " Unsafe client/player/world, stub camera accessors and seeded vanilla modifier fields; no live window/network/item use.");
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }
}
