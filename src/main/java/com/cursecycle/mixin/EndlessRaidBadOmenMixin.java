package com.cursecycle.mixin;

import com.cursecycle.challenge.effect.EffectLedger;
import com.cursecycle.challenge.impl.EndlessRaidChallenge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.entity.effect.BadOmenStatusEffect")
public abstract class EndlessRaidBadOmenMixin {
    @WrapMethod(method = "applyUpdateEffect")
    private boolean cursecycle$convert(LivingEntity entity, int amplifier, Operation<Boolean> original) {
        if (!(entity instanceof ServerPlayerEntity player)) return original.call(entity, amplifier);
        var ledger = EffectLedger.of(player);
        boolean own = ledger.owns(EndlessRaidChallenge.ID, StatusEffects.BAD_OMEN);
        boolean exclusive = ledger.exclusivelyOwns(EndlessRaidChallenge.ID, StatusEffects.BAD_OMEN);
        if (exclusive) {
            if (!EndlessRaidChallenge.eligible(player)) {
                ledger.consumed(StatusEffects.BAD_OMEN);
                return false;
            }
            if (player.getServerWorld().getRaidAt(player.getBlockPos()) != null) return true;
        }
        boolean hadRaidOmen = player.hasStatusEffect(StatusEffects.RAID_OMEN);
        boolean keep = original.call(entity, amplifier);
        if (own && !keep) {
            ledger.consumed(StatusEffects.BAD_OMEN);
            if (exclusive && !hadRaidOmen) ledger.adopt(EndlessRaidChallenge.ID, StatusEffects.RAID_OMEN);
        }
        return keep;
    }
}
