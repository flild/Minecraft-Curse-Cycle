package validation;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.OffsetCrosshair;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import sun.misc.Unsafe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs the transformed vanilla renderCrosshair, capturing textured draws and replacing GL in a separate test JAR. */
public final class OffsetCrosshairProbe implements PreLaunchEntrypoint {
    public static Matrix4f debugMatrix;
    public static int debugCalls;
    private static boolean alive = true, debug;
    private static float cooldown = .5f;
    private static GameMode mode = GameMode.SURVIVAL;
    private static final String ID = "offset_crosshair";
    private static Method render;
    private static InGameHud hud;
    private static int comparisons;

    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean hasReducedDebugInfo() { return false; }
        @Override public float getAttackCooldownProgress(float tickDelta) { return cooldown; }
        @Override public float getAttackCooldownProgressPerTick() { return 10; }
    }
    public static class Manager extends ClientPlayerInteractionManager {
        public Manager() { super(null, null); }
        @Override public GameMode getCurrentGameMode() { return mode; }
    }
    public static class Debug extends DebugHud {
        public Debug() { super(null); }
        @Override public boolean shouldShowDebugHud() { return debug; }
    }
    public static class Context extends DrawContext {
        final int width, height;
        final List<Vector3f> points = new ArrayList<>();
        final List<String> textures = new ArrayList<>();
        boolean fail;
        Context(MinecraftClient client, int width, int height) {
            super(client, null);
            this.width = width;
            this.height = height;
        }
        @Override public int getScaledWindowWidth() { return width; }
        @Override public int getScaledWindowHeight() { return height; }
        private void capture(Identifier texture, int x, int y) {
            if (fail) throw new IllegalStateException("intentional draw failure");
            textures.add(texture.toString());
            points.add(getMatrices().peek().getPositionMatrix().transformPosition(new Vector3f(x, y, 0)));
        }
        @Override public void drawGuiTexture(Identifier texture, int x, int y, int width, int height) {
            capture(texture, x, y);
        }
        @Override public void drawGuiTexture(Identifier texture, int tw, int th, int u, int v, int x, int y, int w, int h) {
            capture(texture, x, y);
        }
    }
    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static void sync(String... ids) { ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of()); }
    private static void draw(Context context) throws Exception {
        var before = new Matrix4f(context.getMatrices().peek().getPositionMatrix());
        var modelView = new Matrix4f(RenderSystem.getModelViewStack());
        debugCalls = 0;
        debugMatrix = null;
        render.invoke(hud, context, RenderTickCounter.ZERO);
        check(before.equals(context.getMatrices().peek().getPositionMatrix()), "Other HUD matrix restored");
        check(modelView.equals(RenderSystem.getModelViewStack()), "Debug model-view restored");
        comparisons++;
    }
    private static void compare(MinecraftClient client, int w, int h, boolean expected) throws Exception {
        sync("no_sprint");
        var baseline = new Context(client, w, h);
        draw(baseline);
        var baselineDebug = debugMatrix;
        int baselineDebugCalls = debugCalls;
        sync(ID, ID, "no_sprint", "tiny_fov", "upside_down_camera", "inverted_mouse");
        var actual = new Context(client, w, h);
        draw(actual);
        check(baseline.textures.equals(actual.textures), "Same textures and count; no central duplicate");
        float dx = expected ? OffsetCrosshair.translation(w, 80) : 0;
        float dy = expected ? OffsetCrosshair.translation(h, 40) : 0;
        for (int i = 0; i < actual.points.size(); i++)
            check(new Vector3f(baseline.points.get(i)).add(dx, dy, 0).equals(actual.points.get(i), 1e-5f),
                    "Crosshair and every cooldown texture translated once");
        check(debugCalls == baselineDebugCalls, "Debug count unchanged");
        if (baselineDebug != null)
            check(new Matrix4f().translation(dx, dy, 0).mul(baselineDebug).equals(debugMatrix, 1e-5f),
                    "Debug projection center shifted in screen axes, before camera rotation");
    }
    private static void audit() throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/client/gui/hud/InGameHud.class"))).accept(node, 0);
        int calls = 0;
        for (var method : node.methods) for (var insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.contains("cursecycle$offsetCrosshair")) {
                check(method.name.equals("renderCrosshair"), "Wrapper called only by renderCrosshair");
                calls++;
            }
        }
        check(calls == 1, "Exactly one production render wrapper");
    }
    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion();
            net.minecraft.Bootstrap.initialize();
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var unsafe = (Unsafe) field.get(null);
            var client = (MinecraftClient) unsafe.allocateInstance(MinecraftClient.class);
            var options = (GameOptions) unsafe.allocateInstance(GameOptions.class);
            var player = (Player) unsafe.allocateInstance(Player.class);
            client.player = player;
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class);
            client.interactionManager = (Manager) unsafe.allocateInstance(Manager.class);
            set(client, MinecraftClient.class, "options", options);
            set(options, GameOptions.class, "reducedDebugInfo", SimpleOption.ofBoolean("probe", false));
            var renderer = (GameRenderer) unsafe.allocateInstance(GameRenderer.class);
            set(client, MinecraftClient.class, "gameRenderer", renderer);
            var camera = new Camera();
            set(camera, Camera.class, "yaw", 73f);
            set(camera, Camera.class, "pitch", -42f);
            set(renderer, GameRenderer.class, "camera", camera);
            hud = (InGameHud) unsafe.allocateInstance(InGameHud.class);
            set(hud, InGameHud.class, "client", client);
            set(hud, InGameHud.class, "debugHud", unsafe.allocateInstance(Debug.class));
            render = InGameHud.class.getDeclaredMethod("renderCrosshair", DrawContext.class, RenderTickCounter.class);
            render.setAccessible(true);
            audit();
            for (var perspective : Perspective.values()) {
                set(options, GameOptions.class, "perspective", perspective);
                for (var gameMode : GameMode.values()) {
                    mode = gameMode;
                    for (boolean debugValue : new boolean[]{false, true}) {
                        debug = debugValue;
                        for (var indicator : AttackIndicator.values()) {
                            set(options, GameOptions.class, "attackIndicator", new SimpleOption<>("probe", SimpleOption.emptyTooltip(),
                                    (text, value) -> text, new SimpleOption.PotentialValuesBasedCallbacks<>(List.of(AttackIndicator.values()),
                                    com.mojang.serialization.Codec.INT.xmap(i -> AttackIndicator.values()[i], Enum::ordinal)), indicator, v -> {}));
                            for (float progress : new float[]{.5f, 1f}) {
                                cooldown = progress;
                                client.targetedEntity = progress == 1 ? player : null;
                                for (int[] size : new int[][]{{640,360},{321,181},{160,100},{17,16},{15,9}})
                                    compare(client, size[0], size[1], mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
                            }
                        }
                    }
                }
            }
            debug = false;
            mode = GameMode.SURVIVAL;
            set(options, GameOptions.class, "perspective", Perspective.FIRST_PERSON);
            alive = false;
            compare(client, 640, 360, false);
            alive = true;
            player.setYaw(28); player.setPitch(-31);
            var look = player.getRotationVec(1);
            sync(ID);
            draw(new Context(client, 640, 360));
            check(player.getYaw() == 28 && player.getPitch() == -31 && look.equals(player.getRotationVec(1)), "Actual look direction unchanged");
            var failing = new Context(client, 640, 360);
            failing.fail = true;
            var before = new Matrix4f(failing.getMatrices().peek().getPositionMatrix());
            try { render.invoke(hud, failing, RenderTickCounter.ZERO); throw new AssertionError("Expected draw failure"); }
            catch (InvocationTargetException ex) { check(ex.getCause() instanceof IllegalStateException, "Expected exception propagates"); }
            check(before.equals(failing.getMatrices().peek().getPositionMatrix()), "finally restores matrix after failure");
            ClientDataHolder.reset();
            check(!OffsetCrosshair.shouldApply(client), "Disconnect reset");
            sync("no_sprint");
            check(!OffsetCrosshair.shouldApply(client), "Ended offline");
            sync(ID);
            set(player, net.minecraft.entity.Entity.class, "removalReason", net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            check(!OffsetCrosshair.shouldApply(client), "Removed player");
            client.player = (Player) unsafe.allocateInstance(Player.class);
            check(OffsetCrosshair.shouldApply(client), "Replacement player");
            client.world = null;
            check(!OffsetCrosshair.shouldApply(client), "No world");
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class);
            check(OffsetCrosshair.shouldApply(client), "Replacement world");
            client.interactionManager = null;
            check(!OffsetCrosshair.shouldApply(client), "No manager");
            client.player = null;
            check(!OffsetCrosshair.shouldApply(client), "No player");
            System.out.println("OFFSET_CROSSHAIR_CLIENT_PROBE_PASSED comparisons=" + comparisons);
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }
}
