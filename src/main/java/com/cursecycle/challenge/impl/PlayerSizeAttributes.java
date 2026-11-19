package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.ChallengeActivity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** One atomic size transition for both rules, preserving foreign attributes and vanilla rendering. */
final class PlayerSizeAttributes {
    private PlayerSizeAttributes() {}

    static void reconcile(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) apply(serverPlayer,
                ChallengeActivity.active(serverPlayer, TinyPlayerChallenge.ID),
                ChallengeActivity.active(serverPlayer, GiantPlayerChallenge.ID));
    }

    static void stop(ServerPlayerEntity player, String stoppedId) {
        // CycleManager stops the old IDs before clearing them. Preserve an already applied other
        // rule, but do not start a suppressed rule just because its old ID is still in the state.
        var attributes = player.getAttributes();
        apply(player, !stoppedId.equals(TinyPlayerChallenge.ID)
                        && ChallengeActivity.active(player, TinyPlayerChallenge.ID)
                        && attributes.hasModifierForAttribute(EntityAttributes.GENERIC_SCALE, TinyPlayerChallenge.SCALE_MODIFIER),
                !stoppedId.equals(GiantPlayerChallenge.ID)
                        && ChallengeActivity.active(player, GiantPlayerChallenge.ID)
                        && attributes.hasModifierForAttribute(EntityAttributes.GENERIC_SCALE, GiantPlayerChallenge.SCALE_MODIFIER));
    }

    private static void modifier(EntityAttributeInstance attribute, Identifier id, double factor, boolean enabled) {
        if (!enabled) attribute.removeModifier(id);
        else if (!attribute.hasModifier(id)) attribute.addTemporaryModifier(new EntityAttributeModifier(
                id, factor - 1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    static void apply(ServerPlayerEntity player, boolean tiny, boolean giant) {
        giant &= !tiny;
        // Avoid creating attribute instances on unrelated players.
        if (!tiny && !giant
                && !player.getAttributes().hasModifierForAttribute(EntityAttributes.GENERIC_SCALE, TinyPlayerChallenge.SCALE_MODIFIER)
                && !player.getAttributes().hasModifierForAttribute(EntityAttributes.GENERIC_SCALE, GiantPlayerChallenge.SCALE_MODIFIER)
                && !player.getAttributes().hasModifierForAttribute(EntityAttributes.GENERIC_JUMP_STRENGTH, TinyPlayerChallenge.JUMP_MODIFIER)
                && !player.getAttributes().hasModifierForAttribute(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, TinyPlayerChallenge.BLOCK_MODIFIER)
                && !player.getAttributes().hasModifierForAttribute(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, TinyPlayerChallenge.ENTITY_MODIFIER)
                && !player.getAttributes().hasModifierForAttribute(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, GiantPlayerChallenge.BLOCK_MODIFIER)
                && !player.getAttributes().hasModifierForAttribute(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, GiantPlayerChallenge.ENTITY_MODIFIER)
                && !player.getAttributes().hasModifierForAttribute(EntityAttributes.GENERIC_STEP_HEIGHT, GiantPlayerChallenge.STEP_MODIFIER)) return;
        var scale = player.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        float before = player.getScale();
        if (scale.hasModifier(TinyPlayerChallenge.SCALE_MODIFIER) != tiny
                || scale.hasModifier(GiantPlayerChallenge.SCALE_MODIFIER) != giant) {
            var preview = new EntityAttributeInstance(scale.getAttribute(), ignored -> {});
            preview.setBaseValue(scale.getBaseValue());
            for (var m : scale.getModifiers()) if (!sizeModifier(m.id())) preview.addTemporaryModifier(m);
            modifier(preview, TinyPlayerChallenge.SCALE_MODIFIER, TinyPlayerChallenge.scaleFactor(player), tiny);
            modifier(preview, GiantPlayerChallenge.SCALE_MODIFIER, GiantPlayerChallenge.SCALE, giant);
            // Players are excluded from vanilla's automatic growth relocation. Do not grow into blocks.
            if ((float) preview.getValue() <= before || prepareGrowth(player, (float) preview.getValue())) {
                modifier(scale, TinyPlayerChallenge.SCALE_MODIFIER, TinyPlayerChallenge.scaleFactor(player), tiny);
                modifier(scale, GiantPlayerChallenge.SCALE_MODIFIER, GiantPlayerChallenge.SCALE, giant);
            }
        }
        // Pending giant growth grants no bonus. Tiny's jump/reach restrictions end immediately on stop.
        giant &= scale.hasModifier(GiantPlayerChallenge.SCALE_MODIFIER);
        modifier(player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH), TinyPlayerChallenge.JUMP_MODIFIER, TinyPlayerChallenge.JUMP, tiny);
        modifier(player.getAttributeInstance(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE), TinyPlayerChallenge.BLOCK_MODIFIER, TinyPlayerChallenge.RANGE, tiny);
        modifier(player.getAttributeInstance(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE), TinyPlayerChallenge.ENTITY_MODIFIER, TinyPlayerChallenge.RANGE, tiny);
        modifier(player.getAttributeInstance(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE), GiantPlayerChallenge.BLOCK_MODIFIER, GiantPlayerChallenge.RANGE, giant);
        modifier(player.getAttributeInstance(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE), GiantPlayerChallenge.ENTITY_MODIFIER, GiantPlayerChallenge.RANGE, giant);
        modifier(player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT), GiantPlayerChallenge.STEP_MODIFIER, GiantPlayerChallenge.STEP, giant);
        if (before != player.getScale()) player.calculateDimensions();
    }

    private static boolean sizeModifier(Identifier id) {
        return id.equals(TinyPlayerChallenge.SCALE_MODIFIER) || id.equals(GiantPlayerChallenge.SCALE_MODIFIER);
    }

    /** Preview clamped scale and loaded collision boxes without changing position or loading chunks. */
    private static boolean prepareGrowth(ServerPlayerEntity player, float scale) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return true;
        // Sleeping dimensions ignore scale; they cannot prove that an enlarged awake body fits.
        if (player.getPose() == EntityPose.SLEEPING) return false;
        for (var pose : new EntityPose[]{player.getPose(), EntityPose.STANDING, EntityPose.CROUCHING, EntityPose.SWIMMING}) {
            var dimensions = pose == EntityPose.SLEEPING ? player.getDimensions(pose)
                    : player.getBaseDimensions(pose).scaled(scale);
            var box = dimensions.getBoxAt(player.getPos()).contract(1.0E-7);
            var world = player.getServerWorld();
            if (box.minY < world.getBottomY() || box.maxY > world.getTopY() || !world.getWorldBorder().contains(box)) continue;
            boolean loaded = true;
            for (int x = MathHelper.floor(box.minX) >> 4; x <= MathHelper.floor(box.maxX) >> 4; x++)
                for (int z = MathHelper.floor(box.minZ) >> 4; z <= MathHelper.floor(box.maxZ) >> 4; z++)
                    loaded &= world.getChunkManager().isChunkLoaded(x, z);
            if (loaded && world.isSpaceEmpty(player, box)) {
                player.setPose(pose);
                return true;
            }
        }
        return false;
    }
}
