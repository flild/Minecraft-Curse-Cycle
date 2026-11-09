package validation;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.gui.*;
import com.cursecycle.config.CurseCycleConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Real renderer, isolated options/config, programmatic UI actions; no world or player needed. */
public final class VisualProbe implements ClientModInitializer {
    private static final List<String> CANDIDATES = List.of("no_sprint", "gills", "bitter_experience",
            "the_floor_is_lava", "double_challenge", "no_block_breaking");
    private static final String DOUBLE = "double_challenge:heartbeat,no_sprint";
    private int ticks;
    private boolean started;
    private int checks;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (!started) {
                    if (client.getOverlay() != null || !(client.currentScreen instanceof TitleScreen)) return;
                    started = true;
                    client.options.pauseOnLostFocus = false;
                    client.options.getMaxFps().setValue(60);
                    GLFW.glfwHideWindow(client.getWindow().getHandle());
                    CurseCycleMod.getConfig().enableWheelAnimation = true;
                    ClientDataHolder.updateState(26, 7600, List.of("heartbeat", "blocked_direction"),
                            DOUBLE, List.of("no_sprint", DOUBLE, "gills", "endless_raid"), "forward");
                    client.setScreen(new WheelScreen(CANDIDATES, "the_floor_is_lava"));
                }
                ticks++;
                if (ticks > 550) throw new AssertionError("visual probe timed out");
                switch (ticks) {
                    case 35 -> capture(client, "wheel-spinning");
                    case 115 -> capture(client, "wheel-result");
                    case 120 -> client.setScreen(new WheelScreen(CANDIDATES, DOUBLE));
                    case 235 -> capture(client, "wheel-double");
                    case 240 -> client.setScreen(new MainMenuScreen());
                    case 245 -> capture(client, "journal-active");
                    case 250 -> press(client, "text.cursecycle.menu.tab.next");
                    case 255 -> capture(client, "journal-next");
                    case 260 -> press(client, "text.cursecycle.menu.tab.history");
                    case 265 -> capture(client, "journal-history");
                    case 270 -> press(client, "text.cursecycle.menu.tab.settings");
                    case 275 -> {
                        capture(client, "journal-settings");
                        var config = CurseCycleMod.getConfig();
                        boolean original = config.enableHudOverlay;
                        buttons(client).get(5).onPress();
                        check(config.enableHudOverlay != original, "HUD toggle");
                        buttons(client).get(5).onPress();
                        check(config.enableHudOverlay == original, "HUD toggle restore");
                        int x = config.hudPositionX;
                        buttons(client).get(7).onPress();
                        check(config.hudPositionX == x + 10, "HUD position button");
                        buttons(client).get(6).onPress();
                        check(config.hudPositionX == x, "HUD position restore");
                        boolean animation = config.enableWheelAnimation;
                        buttons(client).get(10).onPress();
                        check(config.enableWheelAnimation != animation, "wheel toggle");
                        buttons(client).get(10).onPress();
                    }
                    case 280 -> {
                        press(client, "text.cursecycle.menu.tab.active");
                        Screen menu = client.currentScreen;
                        int x = (menu.width - Math.min(480, menu.width - 20)) / 2;
                        int y = (menu.height - Math.min(310, menu.height - 20)) / 2;
                        check(menu.mouseClicked(x + 60, y + 80, 0), "open challenge rules");
                    }
                    case 285 -> capture(client, "journal-rules");
                    case 290 -> {
                        check(client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape closes rules");
                        check(buttons(client).size() >= 5, "journal tabs restored after modal");
                        client.setScreen(new Screen(Text.literal("HUD fixture")) {
                            @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
                                context.fill(0, 0, width, height, 0xFF25252E);
                                CurseCycleHudOverlay.render(context, new CurseCycleConfig(), 26, 7600,
                                        List.of("heartbeat", "blocked_direction"), DOUBLE);
                            }
                        });
                    }
                    case 295 -> capture(client, "hud");
                    case 300 -> {
                        client.getWindow().setWindowedSize(854, 480);
                        client.options.getGuiScale().setValue(2);
                        client.onResolutionChanged();
                        client.setScreen(new WheelScreen(CANDIDATES, "bitter_experience"));
                    }
                    case 415 -> capture(client, "wheel-small");
                    case 420 -> client.setScreen(new MainMenuScreen());
                    case 425 -> capture(client, "journal-small");
                    case 430 -> {
                        press(client, "text.cursecycle.menu.tab.settings");
                        check(buttons(client).stream().allMatch(b -> b.getY() >= 0 && b.getY() + b.getHeight() <= client.currentScreen.height),
                                "all settings controls fit smallest vanilla GUI");
                    }
                    case 435 -> capture(client, "settings-small");
                    case 440 -> client.setScreen(new WheelScreen(List.of(), "unknown_challenge"));
                    case 545 -> {
                        capture(client, "wheel-unknown");
                        check(!client.currentScreen.shouldPause(), "wheel does not pause world");
                        System.out.println("CURSECYCLE_VISUAL_PROBE_PASSED checks=" + checks);
                        client.scheduleStop();
                    }
                }
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.out.println("CURSECYCLE_VISUAL_PROBE_FAILED");
                client.scheduleStop();
                throw new RuntimeException(failure);
            }
        });
    }

    private List<ButtonWidget> buttons(MinecraftClient client) {
        return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance)
                .map(ButtonWidget.class::cast).toList();
    }

    private void press(MinecraftClient client, String key) {
        String label = Text.translatable(key).getString();
        ButtonWidget button = buttons(client).stream().filter(b -> b.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new AssertionError("missing button " + key));
        check(button.active, "enabled button " + key);
        button.onPress();
    }

    private void capture(MinecraftClient client, String name) throws Exception {
        Path output = Path.of("..", "screenshots");
        Files.createDirectories(output);
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            image.writeTo(output.resolve(name + ".png"));
        }
        checks++;
        System.out.println("VISUAL_CAPTURE " + name + " " + client.getWindow().getFramebufferWidth()
                + "x" + client.getWindow().getFramebufferHeight());
    }

    private void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
}
