package com.cursecycle.mixin;

import com.cursecycle.challenge.impl.FloorIsLavaChallenge;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayerEntity.class)
public abstract class FloorIsLavaServerPlayerMixin {
    @Shadow private int joinInvulnerabilityTicks;

    // Bypassing invulnerability via a damage tag would also disable vanilla totems.
    // Ignore only login protection, only for this damage source, without changing
    // the player's protection against other damage or dimension-transfer handling.
    @Redirect(method = "damage", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;joinInvulnerabilityTicks:I",
            opcode = Opcodes.GETFIELD))
    private int floorDamageLoginProtection(ServerPlayerEntity player, DamageSource source, float amount) {
        return source.isOf(FloorIsLavaChallenge.DAMAGE_TYPE) ? 0 : joinInvulnerabilityTicks;
    }
}
