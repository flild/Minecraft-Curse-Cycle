package com.cursecycle.mixin;

import com.cursecycle.challenge.effect.EffectLedger;
import com.cursecycle.challenge.impl.EndlessRaidChallenge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.entity.effect.RaidOmenStatusEffect")
public abstract class EndlessRaidOmenMixin {
    @WrapMethod(method = "applyUpdateEffect")
    private boolean cursecycle$consume(LivingEntity entity, int amplifier, Operation<Boolean> original) {
        if (!(entity instanceof ServerPlayerEntity player)) return original.call(entity, amplifier);
        var ledger = EffectLedger.of(player);
        boolean own = ledger.owns(EndlessRaidChallenge.ID, StatusEffects.RAID_OMEN);
        if (ledger.exclusivelyOwns(EndlessRaidChallenge.ID, StatusEffects.RAID_OMEN)
                && !EndlessRaidChallenge.eligible(player)) {
            ledger.consumed(StatusEffects.RAID_OMEN);
            player.clearStartRaidPos();
            return false;
        }
        boolean keep = original.call(entity, amplifier);
        if (own && !keep) ledger.consumed(StatusEffects.RAID_OMEN);
        return keep;
    }
}
