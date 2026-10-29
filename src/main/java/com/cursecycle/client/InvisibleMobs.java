package com.cursecycle.client;

import com.cursecycle.challenge.impl.InvisibleMobsChallenge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.world.GameMode;

import java.util.List;

/** Current-frame rendering only: never changes entity flags, effects or user options. */
public final class InvisibleMobs {
    private InvisibleMobs() {}

    public static boolean affects(boolean synced, List<String> ids, GameMode mode, boolean alive) {
        return synced && alive && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && ids.contains(InvisibleMobsChallenge.ID);
    }

    public static boolean isTarget(EntityType<?> type) {
        return type != null && type.getSpawnGroup() == SpawnGroup.MONSTER
                && type != EntityType.ENDER_DRAGON && type != EntityType.WITHER;
    }

    public static boolean shouldHide(MinecraftClient client, Entity entity) {
        return entity != null && isTarget(entity.getType())
                && client != null && client.world != null && client.player != null
                && client.interactionManager != null && !client.player.isRemoved()
                && affects(ClientDataHolder.isSynced(), ClientDataHolder.getActiveChallengeIds(),
                        client.interactionManager.getCurrentGameMode(), client.player.isAlive());
    }
}
