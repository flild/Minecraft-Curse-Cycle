package com.example.hardcorechallenges.client;

import com.example.hardcorechallenges.client.network.ClientNetworkHandler;
import com.example.hardcorechallenges.client.ui.ChallengeHudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class HardcoreChallengesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		ClientNetworkHandler.registerReceivers();
		HudRenderCallback.EVENT.register(new ChallengeHudRenderer());
		com.example.hardcorechallenges.client.keybind.Keybindings.register();
	}
}
