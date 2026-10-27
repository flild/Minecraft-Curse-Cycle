package com.cursecycle;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.ForcedThirdPersonCamera;
import com.cursecycle.client.gui.CurseCycleHudOverlay;
import com.cursecycle.client.gui.MainMenuScreen;
import com.cursecycle.client.gui.WheelScreen;
import com.cursecycle.challenge.impl.NoSprintChallenge;
import com.cursecycle.network.CurseCycleSyncPayload;
import com.cursecycle.network.TriggerWheelPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientDataHolder.reset();
            ForcedThirdPersonCamera.apply(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientDataHolder.reset();
            ForcedThirdPersonCamera.apply(client);
        });

        // Register Keybinding 'H' to open main menu
        openMenuKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cursecycle.open_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.cursecycle.category"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            com.cursecycle.client.HeavyBodyInput.tick(client);
            com.cursecycle.client.MovementMemoryInput.validate(client);
            ForcedThirdPersonCamera.apply(client);
            while (openMenuKeyBinding.wasPressed()) {
                if (client.currentScreen == null && client.player != null && ClientDataHolder.isSynced()) {
                    client.setScreen(new MainMenuScreen());
                }
            }

            if (client.player != null && NoSprintChallenge.isNoSprintActiveForPlayer(client.player)) {
                if (client.player.isSprinting()) {
                    client.player.setSprinting(false);
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(com.cursecycle.network.FoodHistoryPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientDataHolder.updateFoodHistory(payload.ids())));

        // Register S2C Packet Listeners
        ClientPlayNetworking.registerGlobalReceiver(com.cursecycle.network.HeavyBodyPayload.ID, (payload, context) ->
                context.client().execute(() -> com.cursecycle.client.HeavyBodyInput.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(com.cursecycle.network.MovementMemoryPayload.ID, (payload, context) ->
                context.client().execute(() -> com.cursecycle.client.MovementMemoryInput.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(CurseCycleSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientDataHolder.updateState(
                    payload.currentDay(),
                    payload.ticksUntilNext(),
                    payload.activeChallengeIds(),
                    payload.nextChallengeId(),
                    payload.historyChallengeIds(),
                    payload.blockedDirection()
                );
                ForcedThirdPersonCamera.apply(context.client());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TriggerWheelPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (!CurseCycleMod.getConfig().enableWheelAnimation) return;
                if (context.client().currentScreen == null || !(context.client().currentScreen instanceof WheelScreen)) {
                    context.client().setScreen(new WheelScreen(payload.candidateIds(), payload.winnerId()));
                }
            });
        });

        // HUD Render Callback
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (!ClientDataHolder.isSynced()) return;
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
