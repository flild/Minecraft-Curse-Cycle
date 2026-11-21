package validation;

import com.cursecycle.challenge.impl.BloodTributeChallenge;
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

/** Synthetic server samples render production panels in a real OpenGL client, outside the mod JAR. */
public final class BloodTributeProbe implements ClientModInitializer {
    private int ticks, remaining = BloodTributeChallenge.INTERVAL;
    private boolean started, paired;
    private final SimonSaysStatePayload simon = new SimonSaysStatePayload(SimonSaysStatePayload.COMMAND, "chat", 65, 100);

    private List<String> ids() { return paired ? List.of("blood_tribute", "simon_says") : List.of("blood_tribute"); }

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (!started) {
                    if (client.getOverlay() != null || client.currentScreen == null) return;
                    started = true;
                    GLFW.glfwHideWindow(client.getWindow().getHandle());
                    ClientDataHolder.updateState(26, 7600, ids(), "", List.of());
                    client.setScreen(new Screen(Text.literal("Blood tribute visual fixture")) {
                        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
                            context.fill(0, 0, width, height, 0xFF25252E);
                            context.drawGuiTexture(Identifier.ofVanilla("hud/hotbar"), width / 2 - 91, height - 22, 182, 22);
                            context.drawGuiTexture(Identifier.ofVanilla("hud/crosshair"), width / 2 - 7, height / 2 - 7, 15, 15);
                            CurseCycleHudOverlay.render(context, new CurseCycleConfig(), 7600, ids(), "");
                            float top = 6;
                            if (paired) {
                                SimonSaysOverlay.renderFeedback(context, simon, simon.remaining());
                                top += (SimonSaysOverlay.height(simon) + 4) * HudLayout.scale(width, height);
                            }
                            BloodTributeOverlay.renderFeedback(context, remaining, BloodTributeChallenge.INTERVAL, top);
                        }
                    });
                }
                switch (++ticks) {
                    case 5 -> capture(client, "full");
                    case 6 -> remaining = 250;
                    case 10 -> capture(client, "countdown");
                    case 11 -> remaining = 60;
                    case 15 -> capture(client, "warning");
                    case 16 -> remaining = 0;
                    case 20 -> capture(client, "overdue");
                    case 21 -> {
                        paired = true;
                        ClientDataHolder.updateState(26, 7600, ids(), "", List.of());
                    }
                    case 25 -> capture(client, "paired");
                    case 26 -> {
                        client.getWindow().setWindowedSize(1920, 1440);
                        client.options.getGuiScale().setValue(6);
                        client.onResolutionChanged();
                    }
                    case 32 -> {
                        if (client.getWindow().getScaleFactor() != 6) throw new AssertionError("GUI scale 6");
                        capture(client, "paired-scale-6");
                        System.out.println("BLOOD_TRIBUTE_VISUAL_PROBE_PASSED");
                        client.scheduleStop();
                    }
                }
                if (ticks > 100) throw new AssertionError("Probe timed out");
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.out.println("BLOOD_TRIBUTE_VISUAL_PROBE_FAILED");
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
        System.out.println("BLOOD_TRIBUTE_CAPTURE " + name);
    }
}
