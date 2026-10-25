package com.cursecycle.mixin;

import com.cursecycle.challenge.effect.EffectLedger;
import com.cursecycle.challenge.effect.EffectLedgerOwner;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class EffectLedgerLivingEntityMixin implements EffectLedgerOwner {
    @Unique private EffectLedger cursecycle$ledger;

    @Override public EffectLedger cursecycle$effects() {
        if (cursecycle$ledger == null && (Object) this instanceof ServerPlayerEntity player)
            cursecycle$ledger = new EffectLedger(player);
        return cursecycle$ledger;
    }

    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"))
    private void cursecycle$externalAdd(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (cursecycle$ledger != null) cursecycle$ledger.externalAdd(effect, false);
    }

    @Inject(method = "setStatusEffect", at = @At("HEAD"))
    private void cursecycle$externalSet(StatusEffectInstance effect, Entity source, CallbackInfo ci) {
        if (cursecycle$ledger != null) cursecycle$ledger.externalAdd(effect, true);
    }

    @Inject(method = "removeStatusEffect", at = @At("HEAD"))
    private void cursecycle$externalRemove(RegistryEntry<StatusEffect> type, CallbackInfoReturnable<Boolean> cir) {
        if (cursecycle$ledger != null) cursecycle$ledger.externalRemove(type);
    }

    @Inject(method = "clearStatusEffects", at = @At("HEAD"))
    private void cursecycle$externalClear(CallbackInfoReturnable<Boolean> cir) {
        if (cursecycle$ledger != null) cursecycle$ledger.externalClear();
    }

    @Inject(method = "tickStatusEffects", at = @At("TAIL"))
    private void cursecycle$tick(CallbackInfo ci) {
        if (cursecycle$ledger != null) cursecycle$ledger.tick();
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void cursecycle$write(NbtCompound nbt, CallbackInfo ci) {
        if (cursecycle$ledger != null) cursecycle$ledger.write(nbt);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void cursecycle$read(NbtCompound nbt, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity) cursecycle$effects().read(nbt);
    }
}
