package com.cursecycle.challenge.impl;

import net.minecraft.entity.player.PlayerEntity;

/** Associates each HungerManager with its current player, including alive respawns. */
public interface InfiniteDietHungerOwner {
    void cursecycle$setDietOwner(PlayerEntity player);
}
