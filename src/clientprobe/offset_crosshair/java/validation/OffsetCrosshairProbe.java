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
    private static boolean alive = true, debug, reducedPlayer;
    private static float cooldown = .5f;
    private static GameMode mode = GameMode.SURVIVAL;
    private static final String ID = "offset_crosshair";
    private static Method render;
    private static InGameHud hud;
    private static int comparisons, checks;

    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean hasReducedDebugInfo() { return reducedPlayer; }
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
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void sync(String... ids) { ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of()); }
    private static void draw(Context context) throws Exception {
        context.getMatrices().translate(3, -4, 2);
        var before = new Matrix4f(context.getMatrices().peek().getPositionMatrix());
        var modelView = new Matrix4f(RenderSystem.getModelViewStack());
        debugCalls = 0;
        debugMatrix = null;
        render.invoke(hud, context, RenderTickCounter.ZERO);
        check(before.equals(context.getMatrices().peek().getPositionMatrix()), "Other HUD matrix restored");
        check(context.getMatrices().isEmpty(), "No leaked DrawContext stack entries");
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
        boolean visible = client.options.getPerspective().isFirstPerson() && mode != GameMode.SPECTATOR;
        boolean debugVisible = visible && debug && !reducedPlayer && !client.options.getReducedDebugInfo().getValue();
        check(debugCalls == (debugVisible ? 1 : 0), "Vanilla debug visibility, no forced crosshair");
        int textureCount = !visible || debugVisible ? 0
                : client.options.getAttackIndicator().getValue() != AttackIndicator.CROSSHAIR ? 1 : cooldown < 1 ? 3 : 2;
        check(actual.textures.size() == textureCount, "Vanilla crosshair/full/partial attack count independently asserted");
        check(baseline.textures.equals(actual.textures), "Same textures and count; no central duplicate");
        float dx = expected ? expectedOffset(w, 80) : 0;
        float dy = expected ? expectedOffset(h, 40) : 0;
        for (int i = 0; i < actual.points.size(); i++)
            check(new Vector3f(baseline.points.get(i)).add(dx, dy, 0).equals(actual.points.get(i), 1e-5f),
                    "Crosshair and every cooldown texture translated once");
        check(debugCalls == baselineDebugCalls, "Debug count unchanged");
        if (baselineDebug != null)
            check(new Matrix4f().translation(dx, dy, 0).mul(baselineDebug).equals(debugMatrix, 1e-5f),
                    "Debug projection center shifted in screen axes, before camera rotation");
    }
    private static int expectedOffset(int size, int amount) {
        if (size < 16) return 0;
        int center = size / 2;
        int point = center + amount;
        if (point < 8) point = 8;
        if (point > size - 8) point = size - 8;
        return point - center;
    }

    private static void visibilityAndReducedDebug(MinecraftClient client) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/client/gui/hud/InGameHud.class"))).accept(node, 0);
        int predicates = 0;
        for (var method : node.methods) {
            boolean readsHidden = false;
            for (var insn : method.instructions)
                if (insn instanceof org.objectweb.asm.tree.FieldInsnNode field && field.name.equals("hudHidden")) readsHidden = true;
            if (!readsHidden || !method.desc.equals("(Lnet/minecraft/client/MinecraftClient;)Z")) continue;
            var predicate = InGameHud.class.getDeclaredMethod(method.name, MinecraftClient.class);
            predicate.setAccessible(true);
            for (boolean hidden : new boolean[]{false, true}) {
                client.options.hudHidden = hidden;
                check((boolean)predicate.invoke(null, client) == !hidden, "Actual vanilla HUD layer visibility follows F1");
            }
            predicates++;
        }
        check(predicates == 2, "Both vanilla HUD layer F1 predicates found");
        client.options.hudHidden = false;
        debug = true;
        for (boolean reduced : new boolean[]{false, true}) {
            set(client.options, GameOptions.class, "reducedDebugInfo", SimpleOption.ofBoolean("probe", reduced));
            for (boolean playerReduced : new boolean[]{false, true}) {
                reducedPlayer = playerReduced;
                compare(client, 641, 361, true);
            }
        }
        reducedPlayer = false;
        set(client.options, GameOptions.class, "reducedDebugInfo", SimpleOption.ofBoolean("probe", false));
        debug = false;
    }

    private static void repeatedResizeAndReset(MinecraftClient client) throws Exception {
        for (int cycle = 0; cycle < 32; cycle++) {
            ClientDataHolder.reset();
            check(!OffsetCrosshair.shouldApply(client), "Every reset removes intervention");
            int w = cycle % 2 == 0 ? 321 : 17, h = cycle % 3 == 0 ? 15 : 181;
            sync("no_sprint");
            var baseline = new Context(client, w, h);
            draw(baseline);
            for (int duplicate = 0; duplicate < 50; duplicate++) {
                sync(ID, ID, "no_sprint");
                var actual = new Context(client, w, h);
                draw(actual);
                check(baseline.textures.equals(actual.textures), "Repeated sync preserves draw count");
                for (int i = 0; i < actual.points.size(); i++)
                    check(new Vector3f(baseline.points.get(i)).add(expectedOffset(w, 80), expectedOffset(h, 40), 0)
                            .equals(actual.points.get(i), 1e-5f), "Current resized GUI, no accumulated offset");
            }
            sync("no_sprint");
            var ended = new Context(client, w, h);
            draw(ended);
            check(baseline.points.equals(ended.points), "Offline end restores current vanilla coordinates");
        }
    }

    private static void cameraPairs(MinecraftClient client, GameRenderer renderer, Camera camera) throws Exception {
        var roll = java.util.Arrays.stream(GameRenderer.class.getDeclaredMethods())
                .filter(m -> m.getName().contains("cursecycle$rollProjection")).findFirst().orElseThrow();
        roll.setAccessible(true);
        var fov = java.util.Arrays.stream(GameRenderer.class.getDeclaredMethods())
                .filter(m -> m.getName().contains("cursecycle$sceneFov")).findFirst().orElseThrow();
        fov.setAccessible(true);
        for (String companion : new String[]{"tiny_fov", "upside_down_camera"}) {
            for (boolean offset : new boolean[]{true, false}) for (boolean paired : new boolean[]{true, false}) {
                sync("no_sprint");
                var baseline = new Context(client, 641, 361); draw(baseline);
                sync(offset ? ID : "no_sprint", paired ? companion : "no_sprint");
                var projection = new Matrix4f().perspective((float)Math.toRadians(80), 16f/9, .05f, 128);
                var expected = new Matrix4f(projection);
                if (paired && companion.equals("upside_down_camera")) expected.rotateZ((float)Math.PI);
                check(expected.equals((Matrix4f)roll.invoke(renderer, projection), 1e-6f), "Actual merged roll independent, both endings");
                var result = new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Double>("probe", true, 80D);
                fov.invoke(renderer, camera, 0f, true, result);
                check(result.getReturnValue() == (paired && companion.equals("tiny_fov") ? 15D : 80D), "Actual merged FOV handler independent, both endings");
                var actual = new Context(client, 641, 361); draw(actual);
                check(actual.textures.equals(baseline.textures), "Pair never adds a central duplicate");
                for (int i = 0; i < actual.points.size(); i++)
                    check(new Vector3f(baseline.points.get(i)).add(offset ? 80 : 0, offset ? 40 : 0, 0)
                            .equals(actual.points.get(i), 1e-5f), "Scene modifiers never rotate or scale the GUI offset");
            }
        }
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
            set(renderer, GameRenderer.class, "client", client);
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
            visibilityAndReducedDebug(client);
            repeatedResizeAndReset(client);
            cameraPairs(client, renderer, camera);
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
            System.out.println("OFFSET_CROSSHAIR_CLIENT_PROBE_PASSED comparisons=" + comparisons + " checks=" + checks);
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }
}
