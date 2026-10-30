package com.cursecycle.client;

import com.cursecycle.challenge.impl.HeartbeatChallenge;
import com.cursecycle.network.HeartbeatPressPayload;
import com.cursecycle.network.HeartbeatStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/** Raw edges run before Screen dispatch, including containers; vanilla events are never consumed. */
public final class HeartbeatInput {
    private HeartbeatInput() {}
    public static KeyBinding key;
    private static final Set<InputUtil.Key> DOWN = new HashSet<>();
    private static HeartbeatStatePayload state;
    private static Object player, world;
    private static int extrapolated;

    public static boolean active(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null
                || !client.player.isAlive() || client.player.isRemoved() || !ClientDataHolder.isSynced()
                || !ClientDataHolder.getActiveChallengeIds().contains(HeartbeatChallenge.ID)) return false;
        var mode = client.interactionManager.getCurrentGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }
    public static boolean textInput(Screen screen) {
        if (screen instanceof ChatScreen || screen instanceof AbstractSignEditScreen || screen instanceof BookEditScreen) return true;
        return focusedText(screen);
    }
    private static boolean focusedText(Element element) {
        if ((element instanceof TextFieldWidget || element instanceof EditBoxWidget) && element.isFocused()) return true;
        // Some vanilla screens focus their editor directly rather than through getFocused().
        if (element instanceof ParentElement parent)
            for (var child : parent.children()) if (focusedText(child)) return true;
        return false;
    }
    public static void receive(MinecraftClient client, HeartbeatStatePayload payload) {
        if (payload.session() == 0 || !active(client)) { reset(); return; }
        state = payload;
        player = client.player;
        world = client.world;
        extrapolated = 0;
    }
    public static void tick(MinecraftClient client) {
        if (!valid(client)) { reset(); return; }
        // Samples arrive every five server ticks. Cap prediction if the server stalls;
        // integrated pause neither advances the client clock nor the server phase.
        if (!client.isPaused()) extrapolated = Math.min(5, extrapolated + 1);
    }
    public static boolean valid(MinecraftClient client) {
        return active(client) && state != null && player == client.player && world == client.world;
    }
    public static HeartbeatStatePayload state() { return state; }
    public static long age() { return state.age() + extrapolated; }
    private static void edge(MinecraftClient client, InputUtil.Key physical, boolean matches, int action) {
        if (action == GLFW.GLFW_RELEASE) { DOWN.remove(physical); return; }
        if (action != GLFW.GLFW_PRESS || !DOWN.add(physical) || !matches) return;
        if (valid(client) && client.isWindowFocused() && !textInput(client.currentScreen)
                && ClientPlayNetworking.canSend(HeartbeatPressPayload.ID))
            ClientPlayNetworking.send(new HeartbeatPressPayload(state.activation(), state.session()));
    }
    public static void keyboard(long window, int code, int scan, int action) {
        var client = MinecraftClient.getInstance();
        if (client == null || key == null || window != client.getWindow().getHandle()) return;
        edge(client, InputUtil.fromKeyCode(code, scan), key.matchesKey(code, scan), action);
    }
    public static void mouse(long window, int button, int action) {
        var client = MinecraftClient.getInstance();
        if (client == null || key == null || window != client.getWindow().getHandle()) return;
        edge(client, InputUtil.Type.MOUSE.createFromCode(button), key.matchesMouse(button), action);
    }
    public static void reset() { state = null; player = world = null; extrapolated = 0; }
    public static void disconnect() { reset(); DOWN.clear(); }
}
