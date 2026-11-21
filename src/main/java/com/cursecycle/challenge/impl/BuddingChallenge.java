package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;

public final class BuddingChallenge extends Challenge {
    public static final String ID = "budding";
    public static final String CHILD_NBT = "cursecycle:budding_child";

    public BuddingChallenge() {
        super(ID, "Акция 1+2",
                "Убив одного, получаешь ещё двоих в подарок.",
                "На месте погибшего моба, в том числе мирного, появляются два маленьких зомби. Из них новые зомби уже не вылезут.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    /** A GLOBAL world consequence, independent of killer, player count and player game mode. */
    public static void died(LivingEntity entity) {
        if (!(entity instanceof MobEntity) || entity.isAlive()
                || !(entity.getWorld() instanceof ServerWorld world)) return;
        var challenge = ChallengeRegistry.get(ID);
        if (challenge == null || !challenge.isEnabled()
                || !CurseCycleState.getServerState(world.getServer()).getActiveChallengeIds().contains(ID)) return;
        var mark = (BuddingMark) entity;
        if (mark.cursecycle$isBuddingChild() || !mark.cursecycle$claimBuddingDeath()) return;

        // No radius search, delayed queue, chunk tickets or retries. Both births use the loaded death position.
        for (int i = 0; i < 2; i++) {
            var child = EntityType.ZOMBIE.create(world);
            if (child == null) continue;
            child.refreshPositionAndAngles(entity.getX(), entity.getY(), entity.getZ(), entity.getYaw(), 0);
            ((BuddingMark) child).cursecycle$markBuddingChild();
            // Vanilla equipment/attributes, guaranteed baby and no extra chicken jockey entity.
            child.initialize(world, world.getLocalDifficulty(child.getBlockPos()), SpawnReason.TRIGGERED,
                    new ZombieEntity.ZombieData(true, false));
            child.setBaby(true);
            world.spawnEntity(child);
        }
    }
}
