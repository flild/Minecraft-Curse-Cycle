package com.cursecycle;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.gui.CurseCycleHudOverlay;
import com.cursecycle.client.gui.MainMenuScreen;
import com.cursecycle.client.gui.WheelScreen;
import com.cursecycle.network.CurseCycleSyncPayload;
import com.cursecycle.network.TriggerWheelPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class CurseCycleClient implements ClientModInitializer {

    public static KeyBinding openMenuKeyBinding;

    @Override
    public void onInitializeClient() {
        CurseCycleMod.LOGGER.info("Initializing Curse-Cycle Client...");

        // Register Keybinding 'H' to open main menu
        openMenuKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cursecycle.open_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.cursecycle.category"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKeyBinding.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new MainMenuScreen());
                }
            }
        });

        // Register S2C Packet Listeners
        ClientPlayNetworking.registerGlobalReceiver(CurseCycleSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientDataHolder.updateState(
                    payload.currentDay(),
                    payload.ticksUntilNext(),
                    payload.activeChallengeIds(),
                    payload.nextChallengeId(),
                    payload.historyChallengeIds()
                );
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TriggerWheelPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().currentScreen == null || !(context.client().currentScreen instanceof WheelScreen)) {
                    context.client().setScreen(new WheelScreen(payload.candidateIds(), payload.winnerId()));
                }
            });
        });

        // HUD Render Callback
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            CurseCycleHudOverlay.render(
                drawContext,
                CurseCycleMod.getConfig(),
                ClientDataHolder.getCurrentDay(),
                ClientDataHolder.getTicksUntilNext(),
                ClientDataHolder.getActiveChallengeIds(),
                ClientDataHolder.getNextChallengeId()
            );
        });
    }
}
