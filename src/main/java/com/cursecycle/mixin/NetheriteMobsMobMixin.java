package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.NetheriteMobSpawn;
import com.cursecycle.challenge.impl.NetheriteMobsChallenge;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class NetheriteMobsMobMixin implements NetheriteMobSpawn {
    @Unique private boolean cursecycle$birthRecorded;
    @Unique private boolean cursecycle$armorPending;

    @Override public void cursecycle$recordBirth(boolean eligible) {
        if (cursecycle$birthRecorded) return;
        cursecycle$birthRecorded = true;
        cursecycle$armorPending = eligible;
    }

    @Override public void cursecycle$finishBirth() {
        if (!cursecycle$armorPending) return;
        cursecycle$armorPending = false;
        NetheriteMobsChallenge.equip((MobEntity) (Object) this);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void cursecycle$save(NbtCompound nbt, CallbackInfo ci) {
        nbt.putBoolean("cursecycle:mob_birth_recorded", cursecycle$birthRecorded);
        nbt.putBoolean("cursecycle:netherite_armor_pending", cursecycle$armorPending);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void cursecycle$load(NbtCompound nbt, CallbackInfo ci) {
        cursecycle$birthRecorded = nbt.getBoolean("cursecycle:mob_birth_recorded");
        cursecycle$armorPending = nbt.getBoolean("cursecycle:netherite_armor_pending");
    }

    @Inject(method = "tryEquip", at = @At("HEAD"), cancellable = true)
    private void cursecycle$keepIssuedArmor(ItemStack incoming, CallbackInfoReturnable<ItemStack> cir) {
        var mob = (MobEntity) (Object) this;
        var slot = mob.getPreferredEquipmentSlot(incoming);
        if (slot.isArmorSlot() && NetheriteMobsChallenge.isIssued(mob.getEquippedStack(slot)))
            cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "getDropChance", at = @At("HEAD"), cancellable = true)
    private void cursecycle$noEquipmentDrop(EquipmentSlot slot, CallbackInfoReturnable<Float> cir) {
        if (NetheriteMobsChallenge.isIssued(((MobEntity) (Object) this).getEquippedStack(slot)))
            cir.setReturnValue(0F);
    }

    @Inject(method = "convertTo", at = @At("HEAD"))
    private void cursecycle$finishBeforeConversion(EntityType<?> type, boolean keepEquipment,
                                                  CallbackInfoReturnable<MobEntity> cir) {
        cursecycle$finishBirth();
    }

    @Inject(method = "convertTo", at = @At("RETURN"))
    private void cursecycle$transferIssuedArmor(EntityType<?> type, boolean keepEquipment,
                                               CallbackInfoReturnable<MobEntity> cir) {
        var result = cir.getReturnValue();
        if (result == null || keepEquipment) return;
        var source = (MobEntity) (Object) this;
        // Curing and some other conversions normally discard equipment or try to drop it.
        // Move only our permanent consequence; vanilla still handles every ordinary item.
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var stack = source.getEquippedStack(slot);
            if (NetheriteMobsChallenge.isIssued(stack)) {
                result.equipStack(slot, stack.copyAndEmpty());
                result.setEquipmentDropChance(slot, 0);
            }
        }
    }
}
