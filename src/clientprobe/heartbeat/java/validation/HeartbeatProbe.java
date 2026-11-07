package validation;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.HeartbeatInput;
import com.cursecycle.network.HeartbeatPressPayload;
import com.cursecycle.network.HeartbeatStatePayload;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sun.misc.Unsafe;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Isolated fixtures, real transformed raw input and production drawing; no OpenGL/network transport. */
public final class HeartbeatProbe implements PreLaunchEntrypoint {
    public static final List<CustomPayload> sent = new ArrayList<>();
    public static boolean canSend = true;
    private static Unsafe unsafe;
    private static MinecraftClient client;
    private static Keyboard keyboard;
    private static Mouse mouse;
    private static GameRenderer renderer;
    private static Method mouseEvent, render;
    private static boolean alive = true;
    private static GameMode mode = GameMode.SURVIVAL;
    private static int checks;
    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
    }
    public static class Manager extends ClientPlayerInteractionManager {
        public Manager() { super(null, null); }
        @Override public GameMode getCurrentGameMode() { return mode; }
    }
    public static class PlainScreen extends Screen { public PlainScreen() { super(Text.empty()); } }
    public static class Context extends DrawContext {
        int draws, labels; boolean green, fail; Text label;
        final int width, height;
        Context(int width, int height) { super(client, null); this.width = width; this.height = height; }
        @Override public int getScaledWindowWidth() { return width; }
        @Override public int getScaledWindowHeight() { return height; }
        @Override public void fill(int x1, int y1, int x2, int y2, int color) {
            if (fail) throw new IllegalStateException("draw failure fixture");
            check(x1 >= 0 && x2 <= Math.max(22, width), "Phase bar fits viewport");
            draws++; green |= color == 0xFF55FF55;
        }
        @Override public void drawCenteredTextWithShadow(TextRenderer font, Text text, int x, int y, int color) {
            labels++; label = text;
        }
    }
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void set(Object target, Class<?> owner, String field, Object value) throws Exception {
        var f = owner.getDeclaredField(field); f.setAccessible(true); f.set(target, value);
    }
    private static void sync() {
        ClientDataHolder.updateState(1, 500, List.of("heartbeat", "silent_world", "no_sprint"), "", List.of());
        HeartbeatInput.receive(client, new HeartbeatStatePayload(7, 11, 115, 120, false));
    }
    private static void key(int code, int scan, int action) { keyboard.onKey(123, code, scan, action, 0); }
    private static void tap() { key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_PRESS); key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_RELEASE); }
    private static void count(int expected, String why) { check(sent.size() == expected, why + " count=" + sent.size()); }
    private static void draw(boolean visible, boolean accepted) throws Exception {
        for (int[] size : new int[][]{{640,360},{321,181},{160,100}}) {
            var context = new Context(size[0], size[1]);
            var before = new Matrix4f(context.getMatrices().peek().getPositionMatrix());
            render.invoke(renderer, RenderTickCounter.ZERO, true, new CallbackInfo("probe", false), context);
            check(context.draws == (visible ? 4 : 0) && context.labels == (visible ? 1 : 0), "Required feedback visibility");
            check(context.green == (visible && accepted), "Accepted beat feedback is authoritative");
            check(before.equals(context.getMatrices().peek().getPositionMatrix()), "Render matrix restored");
        }
    }
    private static Screen container() throws Exception {
        var screen = (GenericContainerScreen) unsafe.allocateInstance(GenericContainerScreen.class);
        set(screen, Screen.class, "children", new ArrayList<>());
        return screen;
    }
    private static void input() throws Exception {
        sync(); sent.clear();
        key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_PRESS); count(1, "Physical B sends one press");
        check(sent.getFirst().equals(new HeartbeatPressPayload(7, 11)), "Only server activation/session, no timestamp");
        for (int i = 0; i < 100; i++) key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_REPEAT);
        key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_PRESS); count(1, "Hold, repeat and duplicate PRESS ignored");
        key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_RELEASE); tap(); count(2, "Release permits another physical press");
        client.currentScreen = container(); tap(); count(3, "Container permits rhythm input");
        client.currentScreen = new PlainScreen(); tap(); count(4, "Nontext menu permits rhythm input");
        for (var type : List.of(ChatScreen.class, SignEditScreen.class, BookEditScreen.class)) {
            client.currentScreen = (Screen) unsafe.allocateInstance(type); tap();
            count(4, "Text screen does not submit a beat: " + type.getSimpleName());
        }
        // Anvil-like screens sometimes focus the editor directly, without a parent focus path.
        var anvil = (AnvilScreen) unsafe.allocateInstance(AnvilScreen.class);
        var field = (TextFieldWidget) unsafe.allocateInstance(TextFieldWidget.class);
        set(field, ClickableWidget.class, "focused", true);
        set(anvil, Screen.class, "children", List.of(field));
        client.currentScreen = anvil; tap(); count(4, "Focused anvil editor is text input");
        set(field, ClickableWidget.class, "focused", false); tap(); count(5, "Unfocused editor does not suppress beat");
        var edit = (EditBoxWidget) unsafe.allocateInstance(EditBoxWidget.class);
        set(edit, ClickableWidget.class, "focused", true);
        set(anvil, Screen.class, "children", List.of(edit)); tap(); count(5, "Multiline editor is text input");
        client.currentScreen = null;
        key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_PRESS); count(6, "World accepts press");
        sync(); key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_REPEAT); count(6, "Phase sample does not convert a hold to press");
        key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_RELEASE);
        HeartbeatInput.key.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_N));
        tap(); count(6, "Original B no longer submits after rebind");
        key(GLFW.GLFW_KEY_N, 49, GLFW.GLFW_PRESS); key(GLFW.GLFW_KEY_N, 49, GLFW.GLFW_RELEASE); count(7, "Rebound N works");
        HeartbeatInput.key.setBoundKey(InputUtil.Type.SCANCODE.createFromCode(48));
        key(GLFW.GLFW_KEY_UNKNOWN, 48, GLFW.GLFW_PRESS); key(GLFW.GLFW_KEY_UNKNOWN, 48, GLFW.GLFW_RELEASE); count(8, "Scancode binding works");
        HeartbeatInput.key.setBoundKey(InputUtil.Type.MOUSE.createFromCode(3));
        mouseEvent.invoke(mouse, 123L, 3, GLFW.GLFW_PRESS, 0);
        mouseEvent.invoke(mouse, 123L, 3, GLFW.GLFW_PRESS, 0); count(9, "Mouse rebind preserves physical edge");
        mouseEvent.invoke(mouse, 123L, 3, GLFW.GLFW_RELEASE, 0);
        client.currentScreen = (Screen) unsafe.allocateInstance(ChatScreen.class);
        mouseEvent.invoke(mouse, 123L, 3, GLFW.GLFW_PRESS, 0); count(9, "Text focus excludes mouse binding too");
        mouseEvent.invoke(mouse, 123L, 3, GLFW.GLFW_RELEASE, 0);
        client.currentScreen = null;
    }
    private static void lifecycleAndRendering() throws Exception {
        sync(); CurseCycleMod.getConfig().enableHudOverlay = false; draw(true, false);
        client.currentScreen = container(); draw(true, false);
        client.currentScreen = new PlainScreen(); draw(true, false);
        client.currentScreen = (Screen) unsafe.allocateInstance(ChatScreen.class); draw(false, false);
        client.currentScreen = null; client.options.hudHidden = true; draw(false, false);
        client.options.hudHidden = false;
        HeartbeatInput.receive(client, new HeartbeatStatePayload(7, 11, 120, 120, true)); draw(true, true);
        HeartbeatInput.receive(client, new HeartbeatStatePayload(7, 11, 0, 120, false)); draw(true, false);
        set(client, MinecraftClient.class, "paused", true);
        for (int i = 0; i < 100; i++) HeartbeatInput.tick(client);
        check(HeartbeatInput.age() == 0, "Integrated pause freezes phase prediction");
        set(client, MinecraftClient.class, "paused", false);
        for (int i = 0; i < 100; i++) HeartbeatInput.tick(client);
        check(HeartbeatInput.age() == 5, "Prediction capped when server samples stop");
        var failing = new Context(640, 360); failing.fail = true;
        var before = new Matrix4f(failing.getMatrices().peek().getPositionMatrix());
        try { render.invoke(renderer, RenderTickCounter.ZERO, true, new CallbackInfo("probe", false), failing); throw new AssertionError("Expected failure"); }
        catch (java.lang.reflect.InvocationTargetException e) { check(e.getCause() instanceof IllegalStateException, "Expected draw failure"); }
        check(before.equals(failing.getMatrices().peek().getPositionMatrix()), "Matrix restored on draw failure");
        for (var value : GameMode.values()) {
            mode = value; sync(); draw(value == GameMode.SURVIVAL || value == GameMode.ADVENTURE, false);
        }
        mode = GameMode.SURVIVAL; sync(); alive = false; draw(false, false); HeartbeatInput.tick(client);
        alive = true; draw(false, false); sync(); draw(true, false);
        client.player = (Player) unsafe.allocateInstance(Player.class); draw(false, false); sync(); draw(true, false);
        client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class); draw(false, false); sync(); draw(true, false);
        HeartbeatInput.receive(client, new HeartbeatStatePayload(7, 0, 0, 0, false)); draw(false, false);
        sync(); ClientDataHolder.reset(); draw(false, false);
        ClientDataHolder.updateState(1, 20, List.of("no_sprint"), "", List.of("heartbeat")); draw(false, false);
    }
    private static void focusTransportAndInactiveControl() throws Exception {
        HeartbeatInput.disconnect(); sent.clear(); sync();
        HeartbeatInput.key.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_B));
        keyboard.onKey(999, GLFW.GLFW_KEY_B, 48, GLFW.GLFW_PRESS, 0);
        count(0, "Another window cannot submit input or latch this window's key");
        tap(); count(1, "Correct window remains usable");
        set(client, MinecraftClient.class, "windowFocused", false);
        key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_PRESS); count(1, "Unfocused window cannot submit");
        set(client, MinecraftClient.class, "windowFocused", true);
        key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_PRESS); count(1, "Regaining focus does not turn a hold into a new press");
        key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_RELEASE); tap(); count(2, "Release after focus loss restores input");
        canSend = false; key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_PRESS); count(2, "Unsupported transport cannot submit");
        canSend = true; key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_PRESS); count(2, "Transport availability does not manufacture an edge");
        key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_RELEASE); tap(); count(3, "New edge uses available transport");
        ClientDataHolder.updateState(1, 20, List.of("no_sprint"), "", List.of());
        HeartbeatInput.receive(client, new HeartbeatStatePayload(7, 11, 120, 120, true));
        tap(); count(3, "Late phase sample cannot revive ended heartbeat"); draw(false, false);
        ClientDataHolder.reset(); tap(); count(3, "Unsynced connection cannot submit");
        sync(); key(GLFW.GLFW_KEY_B, 48, GLFW.GLFW_PRESS); count(4, "Reconnected phase accepts input");
        HeartbeatInput.disconnect(); sync(); tap(); count(5, "Disconnect clears the previous connection's held key");
        for (int i = 0; i < 32; i++) {
            ClientDataHolder.updateState(1, 20, List.of("no_sprint"), "", List.of());
            tap(); count(5 + i, "Inactive control has no heartbeat packet");
            sync(); tap(); count(6 + i, "Reactivation accepts exactly one physical edge");
        }
    }
    private static void audit() throws Exception {
        for (var pair : List.of(new String[]{"Keyboard", "onKey"}, new String[]{"Mouse", "onMouseButton"})) {
            var node = new ClassNode();
            new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/client/" + pair[0] + ".class"))).accept(node, 0);
            var method = node.methods.stream().filter(m -> m.name.equals(pair[1])).findFirst().orElseThrow();
            var calls = new ArrayList<String>();
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call.name);
            check(calls.stream().filter(n -> n.contains("cursecycle$heartbeat")).count() == 1, "One raw input hook");
            int production = -1, fixture = -1;
            for (int i = 0; i < calls.size(); i++) { if (calls.get(i).contains("cursecycle$heartbeat")) production = i; if (calls.get(i).contains("probe$stop")) fixture = i; }
            check(production >= 0 && fixture > production, "Production handler executes before test-only vanilla cancellation");
        }
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/client/render/GameRenderer.class"))).accept(node, 0);
        var method = node.methods.stream().filter(m -> m.name.equals("render")).findFirst().orElseThrow();
        int index = 0, screen = -1, injection = -1, flush = -1, count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.name.equals("renderWithTooltip")) screen = index;
                if (call.name.contains("cursecycle$heartbeat")) { injection = index; count++; }
                if (call.owner.endsWith("DrawContext") && call.name.equals("draw") && call.desc.equals("()V")) flush = index;
            }
            index++;
        }
        check(count == 1 && screen < injection && injection < flush, "Rhythm overlay runs once after screen/tooltip and before GUI flush");
    }
    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion(); net.minecraft.Bootstrap.initialize();
            var f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); unsafe = (Unsafe) f.get(null);
            client = (MinecraftClient) unsafe.allocateInstance(MinecraftClient.class);
            set(null, MinecraftClient.class, "instance", client);
            set(client, MinecraftClient.class, "options", unsafe.allocateInstance(GameOptions.class));
            set(client, MinecraftClient.class, "windowFocused", true);
            var window = (Window) unsafe.allocateInstance(Window.class); set(window, Window.class, "handle", 123L);
            set(client, MinecraftClient.class, "window", window);
            client.player = (Player) unsafe.allocateInstance(Player.class);
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class);
            client.interactionManager = (Manager) unsafe.allocateInstance(Manager.class);
            HeartbeatInput.key = new KeyBinding("key.cursecycle.heartbeat", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.cursecycle.category");
            keyboard = (Keyboard) unsafe.allocateInstance(Keyboard.class); set(keyboard, Keyboard.class, "client", client);
            mouse = (Mouse) unsafe.allocateInstance(Mouse.class); set(mouse, Mouse.class, "client", client);
            mouseEvent = Mouse.class.getDeclaredMethod("onMouseButton", long.class, int.class, int.class, int.class); mouseEvent.setAccessible(true);
            renderer = (GameRenderer) unsafe.allocateInstance(GameRenderer.class);
            render = java.util.Arrays.stream(GameRenderer.class.getDeclaredMethods()).filter(m -> m.getName().contains("cursecycle$heartbeat")).findFirst().orElseThrow();
            render.setAccessible(true);
            input(); lifecycleAndRendering(); focusTransportAndInactiveControl(); audit();
            System.out.println("HEARTBEAT_CLIENT_PROBE_PASSED checks=" + checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
