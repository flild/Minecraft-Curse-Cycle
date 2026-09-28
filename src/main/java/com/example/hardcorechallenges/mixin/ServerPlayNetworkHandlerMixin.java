package com.example.hardcorechallenges.mixin;

import com.example.hardcorechallenges.api.event.ChallengeEvents;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At("HEAD"))
    private void onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (this.player != null) {
            double newX = packet.getX(this.player.getX());
            double newY = packet.getY(this.player.getY());
            double newZ = packet.getZ(this.player.getZ());

            Vec3d oldPos = this.player.getPos();
            Vec3d newPos = new Vec3d(newX, newY, newZ);

            if (oldPos.squaredDistanceTo(newPos) > 0) {
                ChallengeEvents.ON_MOVE.invoker().onMove(this.player, oldPos, newPos);
            }
        }
    }
}
