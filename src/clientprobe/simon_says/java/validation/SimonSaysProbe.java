package validation;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.gui.*;
import com.cursecycle.config.CurseCycleConfig;
import com.cursecycle.network.SimonSaysStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Synthetic server samples exercise the production renderer with real fonts and OpenGL. */
public final class SimonSaysProbe implements ClientModInitializer {
    private int ticks;
    private boolean started;
    private SimonSaysStatePayload sample = new SimonSaysStatePayload(SimonSaysStatePayload.WAITING, "", 1460, 1600);

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (!started) {
                    if (client.getOverlay() != null || client.currentScreen == null) return;
                    started = true;
                    GLFW.glfwHideWindow(client.getWindow().getHandle());
                    ClientDataHolder.updateState(26, 7600, List.of("simon_says", "heartbeat"), "", List.of());
                    client.setScreen(new Screen(Text.literal("Simon Says visual fixture")) {
                        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
                            context.fill(0, 0, width, height, 0xFF25252E);
                            context.drawGuiTexture(Identifier.ofVanilla("hud/hotbar"), width / 2 - 91, height - 22, 182, 22);
                            context.drawGuiTexture(Identifier.ofVanilla("hud/crosshair"), width / 2 - 7, height / 2 - 7, 15, 15);
                            CurseCycleHudOverlay.render(context, new CurseCycleConfig(), 7600,
                                    List.of("simon_says", "heartbeat"), "");
                            HeartbeatOverlay.renderFeedback(context, Text.translatable("text.cursecycle.heartbeat.beat", "B"),
                                    121, 120, false);
                            SimonSaysOverlay.renderFeedback(context, sample, sample.remaining());
                        }
                    });
                }
                switch (++ticks) {
                    case 5 -> capture(client, "waiting");
                    case 6 -> sample = new SimonSaysStatePayload(SimonSaysStatePayload.COMMAND, "chat", 100, 100);
                    case 10 -> capture(client, "chat");
                    case 11 -> sample = new SimonSaysStatePayload(SimonSaysStatePayload.COMMAND, "stop", 15, 100);
                    case 15 -> capture(client, "urgent");
                    case 16 -> sample = new SimonSaysStatePayload(SimonSaysStatePayload.SUCCESS, "", 40, 40);
                    case 20 -> capture(client, "success");
                    case 21 -> sample = new SimonSaysStatePayload(SimonSaysStatePayload.FAILURE, "", 40, 40);
                    case 25 -> capture(client, "failure");
                    case 26 -> {
                        sample = new SimonSaysStatePayload(SimonSaysStatePayload.COMMAND, "chat", 50, 100);
                        client.getWindow().setWindowedSize(1920, 1440);
                        client.options.getGuiScale().setValue(6);
                        client.onResolutionChanged();
                    }
                    case 32 -> {
                        if (client.getWindow().getScaleFactor() != 6) throw new AssertionError("GUI scale 6");
                        capture(client, "chat-scale-6");
                        System.out.println("SIMON_SAYS_VISUAL_PROBE_PASSED");
                        client.scheduleStop();
                    }
                }
                if (ticks > 100) throw new AssertionError("Probe timed out");
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.out.println("SIMON_SAYS_VISUAL_PROBE_FAILED");
                client.scheduleStop();
                throw new RuntimeException(failure);
            }
        });
    }

    private static void capture(MinecraftClient client, String name) throws Exception {
        Path output = Path.of("..", "screenshots");
        Files.createDirectories(output);
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            image.writeTo(output.resolve(name + ".png"));
        }
        System.out.println("SIMON_CAPTURE " + name);
    }
}
