package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Server-owned, vanilla tracked attributes; no saved size or client-local activation guesses. */
public final class TinyPlayerChallenge extends Challenge {
    public static final String ID = "tiny_player";
    public static final double JUMP = .7, RANGE = .6;
    public static final Identifier SCALE_MODIFIER = Identifier.of("cursecycle", "tiny_player/scale");
    public static final Identifier JUMP_MODIFIER = Identifier.of("cursecycle", "tiny_player/jump");
    public static final Identifier BLOCK_MODIFIER = Identifier.of("cursecycle", "tiny_player/block_range");
    public static final Identifier ENTITY_MODIFIER = Identifier.of("cursecycle", "tiny_player/entity_range");

    public TinyPlayerChallenge() {
        super(ID, "Мелкий, но проблемный", "Рост один блок, ниже прыжки и меньше дальность взаимодействия.",
                "Твой рост — один блок: можно пройти под низким потолком. Прыжок слабее, а до блоков и существ "
                        + "нужно подойти ближе. После окончания в тесноте ты ползёшь до выхода на простор.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    public static double scaleFactor(PlayerEntity player) {
        return 1.0 / player.getBaseDimensions(EntityPose.STANDING).height();
    }

    /** Also runs while inactive, so disabled rules, mode changes and replacement state release ownership. */
    public static void reconcile(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        apply(serverPlayer, ChallengeActivity.active(serverPlayer, ID));
    }

    private static void modifier(EntityAttributeInstance attribute, Identifier id, double factor, boolean enabled) {
        if (!enabled) attribute.removeModifier(id);
        else if (!attribute.hasModifier(id)) attribute.addTemporaryModifier(new EntityAttributeModifier(
                id, factor - 1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void apply(ServerPlayerEntity player, boolean enabled) {
        if (!enabled
                && !player.getAttributes().hasModifierForAttribute(EntityAttributes.GENERIC_SCALE, SCALE_MODIFIER)
                && !player.getAttributes().hasModifierForAttribute(EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP_MODIFIER)
                && !player.getAttributes().hasModifierForAttribute(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, BLOCK_MODIFIER)
                && !player.getAttributes().hasModifierForAttribute(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, ENTITY_MODIFIER)) return;
        var scale = player.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        float before = player.getScale();
        if (enabled) modifier(scale, SCALE_MODIFIER, scaleFactor(player), true);
        else if (scale.hasModifier(SCALE_MODIFIER) && prepareRestoration(player, scale)) scale.removeModifier(SCALE_MODIFIER);
        modifier(player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH), JUMP_MODIFIER, JUMP, enabled);
        modifier(player.getAttributeInstance(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE), BLOCK_MODIFIER, RANGE, enabled);
        modifier(player.getAttributeInstance(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE), ENTITY_MODIFIER, RANGE, enabled);
        if (before != player.getScale()) player.calculateDimensions();
    }

    /** Preview the real clamped attribute without mutating it or losing foreign base/modifiers. */
    private static boolean prepareRestoration(ServerPlayerEntity player, EntityAttributeInstance scale) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return true;
        var restored = new EntityAttributeInstance(scale.getAttribute(), ignored -> {});
        restored.setBaseValue(scale.getBaseValue());
        for (var modifier : scale.getModifiers()) if (!modifier.id().equals(SCALE_MODIFIER)) restored.addTemporaryModifier(modifier);
        for (var pose : new EntityPose[]{player.getPose(), EntityPose.STANDING, EntityPose.CROUCHING, EntityPose.SWIMMING}) {
            // Sleeping dimensions deliberately ignore scale in vanilla.
            var dimensions = pose == EntityPose.SLEEPING ? player.getDimensions(pose)
                    : player.getBaseDimensions(pose).scaled((float) restored.getValue());
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
        // A narrow cage can fit the tiny body but no restored pose. Retry on the ordinary tick;
        // jump/range restrictions end now. Never teleport through walls or force-load chunks.
        return false;
    }

    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { reconcile(player); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) { apply(player, false); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) {
        apply(oldPlayer, false);
        reconcile(newPlayer);
    }
}
