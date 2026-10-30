package com.cursecycle.mixin;

import com.cursecycle.challenge.effect.EffectLedger;
import com.cursecycle.challenge.impl.EndlessRaidChallenge;
import com.cursecycle.state.EndlessRaidState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.raid.Raid;
import net.minecraft.village.raid.RaidManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RaidManager.class)
public abstract class EndlessRaidManagerMixin {
    @Shadow @Final private ServerWorld world;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cursecycle$reconcile(CallbackInfo ci) {
        // Before vanilla can spawn a wave or award a victory after loading stale state.
        EndlessRaidState.get(world.getServer()).reconcile(world);
    }

    @WrapOperation(method = "startRaid", at = @At(value = "INVOKE", target = "Lnet/minecraft/village/raid/RaidManager;getOrCreateRaid(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/village/raid/Raid;"))
    private Raid cursecycle$origin(RaidManager manager, ServerWorld raidWorld, BlockPos center,
                                  Operation<Raid> original, ServerPlayerEntity player, BlockPos start,
                                  @Share("newOwnRaid") LocalBooleanRef created) {
        Raid raid = original.call(manager, raidWorld, center);
        boolean own = EffectLedger.of(player).exclusivelyOwns(EndlessRaidChallenge.ID, StatusEffects.RAID_OMEN);
        created.set(own && manager.getRaid(raid.getRaidId()) == null);
        if (created.get()) EndlessRaidState.get(world.getServer()).record(world, raid);
        return raid;
    }

    @WrapOperation(method = "startRaid", at = @At(value = "INVOKE", target = "Lnet/minecraft/village/raid/Raid;start(Lnet/minecraft/server/network/ServerPlayerEntity;)Z"))
    private boolean cursecycle$noStack(Raid raid, ServerPlayerEntity player, Operation<Boolean> original,
                                      @Share("newOwnRaid") LocalBooleanRef created) {
        if (EffectLedger.of(player).exclusivelyOwns(EndlessRaidChallenge.ID, StatusEffects.RAID_OMEN)
                && !created.get()) return false;
        return original.call(raid, player);
    }
}
