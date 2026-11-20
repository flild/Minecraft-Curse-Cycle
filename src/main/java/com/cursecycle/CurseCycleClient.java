package com.cursecycle;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.ForcedThirdPersonCamera;
import com.cursecycle.client.gui.CurseCycleHudOverlay;
import com.cursecycle.client.gui.MainMenuScreen;
import com.cursecycle.client.gui.WheelScreen;
import com.cursecycle.challenge.impl.NoSprintChallenge;
import com.cursecycle.diagnostics.StartupDiagnostics;
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
        StartupDiagnostics.initialize("client", this::initializeClient);
    }

    private void initializeClient(StartupDiagnostics diagnostics) {
        com.cursecycle.client.BlackHoleRenderer.initialize();
        com.cursecycle.client.MeteorShowerRenderer.initialize();
        ClientPlayNetworking.registerGlobalReceiver(com.cursecycle.network.MeteorShowerStatePayload.ID, (payload, context) ->
                context.client().execute(() -> com.cursecycle.client.MeteorShowerVisuals.receive(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(com.cursecycle.network.BlackHoleStatePayload.ID, (payload, context) ->
                context.client().execute(() -> com.cursecycle.client.BlackHoleVisuals.receive(context.client(), payload)));
        diagnostics.stage("registering connection events");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            com.cursecycle.client.BlackHoleVisuals.clear();
            com.cursecycle.client.MeteorShowerVisuals.clear();
            ClientDataHolder.reset();
            com.cursecycle.client.ClientTimingSettings.reset();
            ForcedThirdPersonCamera.apply(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            com.cursecycle.client.BlackHoleVisuals.clear();
            com.cursecycle.client.MeteorShowerVisuals.clear();
            ClientDataHolder.reset();
            com.cursecycle.client.ClientTimingSettings.reset();
            ForcedThirdPersonCamera.apply(client);
        });

        // Register Keybinding 'H' to open main menu
        diagnostics.stage("registering key bindings");
        com.cursecycle.client.HeartbeatInput.key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cursecycle.heartbeat", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.cursecycle.category"));
        openMenuKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cursecycle.open_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.cursecycle.category"
        ));

        diagnostics.stage("registering client ticks");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            com.cursecycle.client.BlackHoleVisuals.tick(client);
            com.cursecycle.client.MeteorShowerVisuals.tick(client);
            com.cursecycle.client.HeartbeatInput.tick(client);
            com.cursecycle.client.HeavyBodyInput.tick(client);
            com.cursecycle.client.MovementMemoryInput.validate(client);
            ForcedThirdPersonCamera.apply(client);
            while (openMenuKeyBinding.wasPressed()) {
                if (client.currentScreen == null && client.player != null && ClientDataHolder.isSynced()) {
                    client.setScreen(new MainMenuScreen());
                    if (ClientPlayNetworking.canSend(com.cursecycle.network.TimingSettingsRequestPayload.ID))
                        ClientPlayNetworking.send(new com.cursecycle.network.TimingSettingsRequestPayload(false, 0, 0, 0));
                }
            }

            if (client.player != null && NoSprintChallenge.isNoSprintActiveForPlayer(client.player)) {
                if (client.player.isSprinting()) {
                    client.player.setSprinting(false);
                }
            }
        });

        diagnostics.stage("registering client network receivers");
        ClientPlayNetworking.registerGlobalReceiver(com.cursecycle.network.TimingSettingsPayload.ID, (payload, context) ->
                context.client().execute(() -> com.cursecycle.client.ClientTimingSettings.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(com.cursecycle.network.FoodHistoryPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientDataHolder.updateFoodHistory(payload.ids())));

        // Register S2C Packet Listeners
        ClientPlayNetworking.registerGlobalReceiver(com.cursecycle.network.HeavyBodyStatePayload.ID, (payload, context) ->
                context.client().execute(() -> com.cursecycle.client.HeavyBodyInput.receive(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(com.cursecycle.network.HeartbeatStatePayload.ID, (payload, context) ->
                context.client().execute(() -> com.cursecycle.client.HeartbeatInput.receive(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(com.cursecycle.network.AutoJumpPayload.ID, (payload, context) ->
                context.client().execute(() -> com.cursecycle.client.AutoJumpMotion.receive(context.client().player, payload)));
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
                if (!CurseCycleMod.getConfig().enableWheelAnimation) {
                    if (context.client().currentScreen instanceof WheelScreen) context.client().setScreen(null);
                    WheelScreen.announceResult(payload.winnerId());
                    return;
                }
                context.client().setScreen(new WheelScreen(payload.candidateIds(), payload.winnerId()));
            });
        });

        // HUD Render Callback
        diagnostics.stage("registering HUD renderer");
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (!ClientDataHolder.isSynced()) return;
            CurseCycleHudOverlay.render(
                drawContext,
                CurseCycleMod.getConfig(),
                ClientDataHolder.getTicksUntilNext(),
                ClientDataHolder.getActiveChallengeIds(),
                ClientDataHolder.getNextChallengeId()
            );
        });
    }
}
