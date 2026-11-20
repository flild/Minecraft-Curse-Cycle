package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Vanilla tracked attributes carry physics to clients; temporary modifiers never enter entity NBT. */
public final class LowGravityChallenge extends Challenge {
    public static final String ID = "low_gravity";
    public static final double GRAVITY = .25, JUMP = 1.25, KNOCKBACK = 1.5;
    public static final Identifier GRAVITY_MODIFIER = Identifier.of("cursecycle", "low_gravity/gravity");
    public static final Identifier JUMP_MODIFIER = Identifier.of("cursecycle", "low_gravity/jump");

    public LowGravityChallenge() {
        super(ID, "Был на луне?",
                "Гравитация ослабла.",
                "Гравитация ослабла и для тебя, и для мобов. Прыжки выше, падения медленнее, от ударов сильнее отбрасывает. Разбиться всё ещё можно.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }

    public static boolean active(LivingEntity entity) {
        if (!entity.isAlive() || entity.isRemoved() || entity.hasVehicle()) return false;
        if (entity instanceof PlayerEntity player
                && (player.isCreative() || player.isSpectator() || player.getAbilities().flying)) return false;
        if (!(entity instanceof PlayerEntity) && !(entity instanceof MobEntity)) return false;
        if (entity.getWorld().isClient) {
            // Also works for tracked mobs, without inferring their state from the local player's PERSONAL IDs.
            var gravity = entity.getAttributeInstance(EntityAttributes.GENERIC_GRAVITY);
            return gravity != null && gravity.hasModifier(GRAVITY_MODIFIER);
        }
        if (entity instanceof ServerPlayerEntity player) return ChallengeActivity.active(player, ID);
        var server = entity.getServer();
        var challenge = ChallengeRegistry.get(ID);
        return server != null && challenge != null && challenge.isEnabled()
                && CurseCycleState.getServerState(server).getActiveChallengeIds().contains(ID);
    }

    /** Called by each entity's ordinary tick and ENTITY_LOAD, including while the rule is disabled. No world scan. */
    public static void reconcile(LivingEntity entity) {
        if (entity.getWorld().isClient) return;
        boolean enabled = active(entity);
        // Do not instantiate default attribute instances on unrelated/inactive entities.
        if (!enabled && !entity.getAttributes().hasModifierForAttribute(EntityAttributes.GENERIC_GRAVITY, GRAVITY_MODIFIER)
                && !entity.getAttributes().hasModifierForAttribute(EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP_MODIFIER)) return;
        modifier(entity.getAttributeInstance(EntityAttributes.GENERIC_GRAVITY), GRAVITY_MODIFIER, GRAVITY, enabled);
        modifier(entity.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH), JUMP_MODIFIER, JUMP, enabled);
    }

    private static void modifier(EntityAttributeInstance attribute, Identifier id, double factor, boolean enabled) {
        if (attribute == null) return;
        if (!enabled) attribute.removeModifier(id);
        else if (!attribute.hasModifier(id)) attribute.addTemporaryModifier(new EntityAttributeModifier(
                id, factor - 1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void release(LivingEntity entity) {
        modifier(entity.getAttributeInstance(EntityAttributes.GENERIC_GRAVITY), GRAVITY_MODIFIER, GRAVITY, false);
        modifier(entity.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH), JUMP_MODIFIER, JUMP, false);
    }

    public static Vec3d knockback(Entity entity, Vec3d impulse) {
        return entity instanceof LivingEntity living && active(living) ? impulse.multiply(KNOCKBACK) : impulse;
    }

    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { reconcile(player); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) { release(player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) {
        release(oldPlayer);
        reconcile(newPlayer);
    }
}
