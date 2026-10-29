package com.cursecycle.mixin;

import com.cursecycle.client.InvisibleMobs;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class InvisibleMobsEntityRenderDispatcherMixin {
    // The complete renderer call includes model, features (armor/held items/eyes),
    // name and outline vertices. Fire and F3+B are separate dispatcher calls.
    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private boolean cursecycle$renderMob(EntityRenderer<?> renderer, Entity entity, float yaw, float delta,
                                        MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        return !InvisibleMobs.shouldHide(MinecraftClient.getInstance(), entity);
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/entity/Entity;FFLnet/minecraft/world/WorldView;F)V"))
    private boolean cursecycle$renderMobShadow(MatrixStack matrices, VertexConsumerProvider vertices, Entity entity,
                                              float opacity, float delta, WorldView world, float radius) {
        return !InvisibleMobs.shouldHide(MinecraftClient.getInstance(), entity);
    }
}
