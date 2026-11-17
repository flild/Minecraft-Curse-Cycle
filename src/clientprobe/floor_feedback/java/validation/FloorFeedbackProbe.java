package validation;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.impl.FloorIsLavaChallenge;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.gui.MainMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Captures the actual rules screen with item rendering, scissoring and scroll at two window sizes. */
public final class FloorFeedbackProbe implements ClientModInitializer {
    private int ticks, checks;
    private boolean started;

    private void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static Object field(MinecraftClient client, String name) throws Exception {
        var field = MainMenuScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(client.currentScreen);
    }
    private void openRules(MinecraftClient client) throws Exception {
        ClientDataHolder.updateState(1, 24000, List.of(FloorIsLavaChallenge.ID), "", List.of());
        client.setScreen(new MainMenuScreen());
        var label = Text.translatable("text.cursecycle.menu.tab.active").getString();
        var active = client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance)
                .map(ButtonWidget.class::cast).filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
        active.onPress();
        check(client.currentScreen.mouseClicked((int) field(client, "left") + 60,
                (int) field(client, "bodyTop") + 12, 0), "Rules open through real entry click");
        check(((Challenge) field(client, "modal")).getId().equals(FloorIsLavaChallenge.ID), "Lava rules selected");
    }
    private void capture(MinecraftClient client, String name) throws Exception {
        var output = Path.of("..", "screenshots");
        Files.createDirectories(output);
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            image.writeTo(output.resolve(name + ".png"));
        }
        checks++;
    }

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (!started) {
                    if (client.getOverlay() != null || client.currentScreen == null) return;
                    started = true;
                    GLFW.glfwHideWindow(client.getWindow().getHandle());
                    openRules(client);
                }
                switch (++ticks) {
                    case 8 -> {
                        capture(client, "lava-rules");
                        check((int) field(client, "modalScroll") == 0, "Wide screen starts at top");
                        client.getWindow().setWindowedSize(854, 480);
                        client.options.getGuiScale().setValue(2);
                        client.onResolutionChanged();
                        openRules(client);
                    }
                    case 16 -> {
                        capture(client, "lava-rules-small");
                        client.currentScreen.mouseScrolled(100, 100, 0, -100);
                    }
                    case 24 -> {
                        capture(client, "lava-rules-small-bottom");
                        check((int) field(client, "modalScroll") > 0, "Small window scrolls through icon rows");
                        client.currentScreen.mouseScrolled(100, 100, 0, 100);
                    }
                    case 32 -> {
                        check((int) field(client, "modalScroll") == 0, "Scroll returns to rules heading");
                        check(client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape closes only rules");
                        check(field(client, "modal") == null, "Journal restored");
                        System.out.println("FLOOR_FEEDBACK_VISUAL_PROBE_PASSED checks=" + checks);
                        client.scheduleStop();
                    }
                }
                if (ticks > 120) throw new AssertionError("Rules capture timed out");
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.out.println("FLOOR_FEEDBACK_VISUAL_PROBE_FAILED");
                client.scheduleStop();
                throw new RuntimeException(failure);
            }
        });
    }
}
