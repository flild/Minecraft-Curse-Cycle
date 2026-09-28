package com.example.hardcorechallenges;

import com.example.hardcorechallenges.api.registry.ChallengeRegistry;
import com.example.hardcorechallenges.challenge.*;
import com.example.hardcorechallenges.command.ChallengeCommand;
import com.example.hardcorechallenges.state.ChallengePersistentState;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HardcoreChallenges implements ModInitializer {
	public static final String MOD_ID = "hardcorechallenges";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing HardcoreChallenges...");

		// Регистрация испытаний
		ChallengeRegistry.register(() -> new TestChallenge());
		ChallengeRegistry.register(() -> new TestViolationChallenge());
		ChallengeRegistry.register(() -> new TestMultiChallenge());
		ChallengeRegistry.register(() -> new TestWorldEventChallenge());

		// Регистрация сохранения состояния
		ChallengePersistentState.registerLifecycle();

		// Регистрация команд
		ChallengeCommand.register();

		LOGGER.info("HardcoreChallenges initialized successfully!");
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
