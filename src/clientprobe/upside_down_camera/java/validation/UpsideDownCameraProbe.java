package validation;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.ForcedThirdPersonCamera;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.world.GameMode;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import sun.misc.Unsafe;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Audits transformed render call sites, then invokes their actual injected handler on vanilla projections.
 * Does not run OpenGL/renderWorld or replace production gates/methods. */
public final class UpsideDownCameraProbe implements PreLaunchEntrypoint {
    private static final String ID = "upside_down_camera";
    private static boolean alive = true;
    private static GameMode mode = GameMode.SURVIVAL;
    private static GameRenderer renderer;
    private static Method roll;

    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
    }

    public static class Manager extends ClientPlayerInteractionManager {
        public Manager() { super(null, null); }
        @Override public GameMode getCurrentGameMode() { return mode; }
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        var f = owner.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }

    private static void same(Matrix4f expected, Matrix4f actual, String why) {
        check(expected.equals(actual, 1e-5f), why + ": expected=" + expected + " actual=" + actual);
    }

    private static void sync(String... ids) {
        ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of());
    }

    private static Matrix4f apply(Matrix4f matrix) throws Exception {
        var actual = (Matrix4f) roll.invoke(renderer, matrix);
        check(matrix == actual, "Fresh projection identity retained");
        return actual;
    }

    private static void auditCallSites() throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/client/render/GameRenderer.class"))).accept(node, 0);
        int total = 0;
        for (var method : node.methods) {
            int projections = 0, rolls = 0;
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (call.name.equals("getBasicProjectionMatrix")) {
                    projections++;
                    // MixinExtras inserts the handler immediately after this expression, once.
                    var next = instruction.getNext();
                    int distance = 0;
                    while (next != null && !(next instanceof MethodInsnNode) && distance++ < 8) next = next.getNext();
                    if (method.name.equals("renderWorld") || method.name.equals("renderHand")) {
                        check(next instanceof MethodInsnNode injected && injected.name.equals(roll.getName()),
                                "Projection is immediately rolled exactly once in " + method.name);
                    }
                }
                if (call.name.equals(roll.getName())) rolls++;
            }
            int expected = method.name.equals("renderWorld") ? 2 : method.name.equals("renderHand") ? 1 : 0;
            check(rolls == expected, "Roll call count in " + method.name + ": " + rolls);
            if (expected > 0) check(projections == expected, "Vanilla projection call count");
            total += rolls;
        }
        check(total == 3, "World, culling and independent hand projection only; GUI/basic projection unchanged");
    }

    private static void inactive(String why) throws Exception {
        var original = renderer.getBasicProjectionMatrix(70);
        same(original, apply(new Matrix4f(original)), why);
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
            var player = (Player) unsafe.allocateInstance(Player.class);
            var manager = (Manager) unsafe.allocateInstance(Manager.class);
            var window = (Window) unsafe.allocateInstance(Window.class);
            renderer = (GameRenderer) unsafe.allocateInstance(GameRenderer.class);
            client.player = player;
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class);
            client.interactionManager = manager;
            set(client, MinecraftClient.class, "options", options);
            set(client, MinecraftClient.class, "window", window);
            set(renderer, GameRenderer.class, "client", client);
            set(renderer, GameRenderer.class, "zoom", 1f);
            set(renderer, GameRenderer.class, "viewDistance", 128f);
            roll = Arrays.stream(GameRenderer.class.getDeclaredMethods())
                    .filter(m -> m.getName().contains("cursecycle$rollProjection")).findFirst().orElseThrow();
            roll.setAccessible(true);
            auditCallSites();
            int comparisons = 0;
            var rotation = new Matrix4f().rotationZ((float) Math.PI);
            // Nonzero camera yaw/pitch, hurt and bob fixtures, all applied after the roll.
            for (int width : new int[]{800, 1920, 3440}) {
                set(window, Window.class, "framebufferWidth", width);
                set(window, Window.class, "framebufferHeight", 1080);
                for (var perspective : Perspective.values()) {
                    set(options, GameOptions.class, "perspective", perspective);
                    for (double fov : new double[]{15, 30, 70, 90, 110}) {
                        for (float yaw : new float[]{-130, 0, 85}) {
                            for (float pitch : new float[]{-75, 0, 65}) {
                                player.setYaw(yaw);
                                player.setPitch(pitch);
                                var view = new Matrix4f().rotateX((float) Math.toRadians(pitch))
                                        .rotateY((float) Math.toRadians(yaw + (perspective.isFrontView() ? 180 : 0)));
                                for (boolean effects : new boolean[]{false, true}) {
                                    var bobHurt = effects ? new Matrix4f().rotateZ(.13f).translate(.03f, -.02f, 0).rotateX(.04f)
                                            : new Matrix4f();
                                    sync("no_sprint");
                                    var vanilla = renderer.getBasicProjectionMatrix(fov);
                                    same(vanilla, apply(new Matrix4f(vanilla)), "Inactive scene");
                                    sync(ID, ID, "no_sprint");
                                    var actual = apply(renderer.getBasicProjectionMatrix(fov));
                                    same(new Matrix4f(vanilla).mul(rotation), actual, "Exactly 180 degree roll");
                                    same(new Matrix4f(vanilla).mul(rotation).mul(bobHurt).mul(view),
                                            new Matrix4f(actual).mul(bobHurt).mul(view), "Vanilla bob/hurt/view composition");
                                    same(vanilla, renderer.getBasicProjectionMatrix(fov), "Basic/GUI queries never globally changed");
                                    // Independent hand pass must receive one roll, not accumulate world roll.
                                    var handVanilla = renderer.getBasicProjectionMatrix(70);
                                    same(new Matrix4f(handVanilla).mul(rotation), apply(renderer.getBasicProjectionMatrix(70)), "Hand pass");
                                    var center = actual.transform(new Vector4f(0, 0, -5, 1));
                                    check(Math.abs(center.x) < 1e-5 && Math.abs(center.y) < 1e-5 && center.w > 0,
                                            "Forward central ray stays centered, not reversed");
                                    var point = new Vector4f(1, 2, -5, 1);
                                    var before = vanilla.transform(new Vector4f(point));
                                    var after = actual.transform(new Vector4f(point));
                                    check(Math.abs(before.x + after.x) < 1e-4 && Math.abs(before.y + after.y) < 1e-4
                                            && before.z == after.z && before.w == after.w, "Asymmetric scene turns upside down with unchanged depth");
                                    check(player.getYaw() == yaw && player.getPitch() == pitch && options.getPerspective() == perspective,
                                            "Yaw/pitch and perspective untouched");
                                    sync("no_sprint");
                                    same(vanilla, apply(renderer.getBasicProjectionMatrix(fov)), "Stop restores current vanilla projection");
                                    comparisons++;
                                }
                            }
                        }
                    }
                }
            }
            sync(ID);
            for (var gm : GameMode.values()) {
                mode = gm;
                var vanilla = renderer.getBasicProjectionMatrix(70);
                same(gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE ? new Matrix4f(vanilla).mul(rotation) : vanilla,
                        apply(renderer.getBasicProjectionMatrix(70)), "Mode " + gm);
            }
            mode = GameMode.SURVIVAL;
            alive = false;
            inactive("Dead object");
            alive = true;
            set(player, Entity.class, "removalReason", Entity.RemovalReason.DISCARDED);
            inactive("Removed object");
            client.player = (Player) unsafe.allocateInstance(Player.class);
            check(!apply(renderer.getBasicProjectionMatrix(70)).equals(renderer.getBasicProjectionMatrix(70)), "Respawn object");
            client.world = null;
            inactive("Missing world");
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class);
            check(!apply(renderer.getBasicProjectionMatrix(70)).equals(renderer.getBasicProjectionMatrix(70)), "New dimension/world");
            var respawned = client.player;
            client.player = null;
            inactive("Missing player");
            client.player = respawned;
            client.interactionManager = null;
            inactive("Missing manager");
            client.interactionManager = manager;
            mode = null;
            inactive("Unknown mode");
            mode = GameMode.SURVIVAL;
            ClientDataHolder.reset();
            inactive("Disconnect/unsynced");
            sync("no_sprint");
            inactive("Ended offline");
            sync(ID);
            set(renderer, GameRenderer.class, "renderingPanorama", true);
            inactive("Panorama");
            set(renderer, GameRenderer.class, "renderingPanorama", false);
            set(null, MinecraftClient.class, "instance", client);
            for (String forced : List.of("forced_third_person_front", "forced_third_person_rpg")) {
                sync(ID, forced);
                options.setPerspective(Perspective.FIRST_PERSON);
                var expected = ForcedThirdPersonCamera.resolvePerspective(ClientDataHolder.getActiveChallengeIds());
                check(options.getPerspective() == expected, "Forced perspective setter still works");
                same(new Matrix4f(renderer.getBasicProjectionMatrix(70)).mul(rotation),
                        apply(renderer.getBasicProjectionMatrix(70)), "Roll composes with forced perspective");
                sync(forced);
                inactive("Ending roll leaves forced perspective");
                check(options.getPerspective() == expected, "Forced perspective retained");
            }
            System.out.println("UPSIDE_DOWN_CAMERA_CLIENT_PROBE_PASSED: " + comparisons
                    + " vanilla projection/injected handler comparisons; 3 transformed render call sites audited."
                    + " Unsafe fixtures; no OpenGL, actual renderWorld/renderHand execution, live input or network.");
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }
}
