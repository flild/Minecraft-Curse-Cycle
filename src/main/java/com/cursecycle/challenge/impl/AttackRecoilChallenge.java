package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public class AttackRecoilChallenge extends Challenge {
    public static final String ID = "attack_recoil";
    public static final double HORIZONTAL_IMPULSE = 0.6;

    // Технические нюансы; не включать в описание для игрока.
    // Удар по живой цели даёт горизонтальный импульс в один блок за тик от цели, не меняя вертикальную
    // скорость. Один взмах даёт одну отдачу, включая размашистую атаку. Промахи, полностью заблокированные
    // удары, снаряды и отложенный огненный урон её не вызывают.
    public AttackRecoilChallenge() {
        super(ID, "Как в контрстрайке",
                "Теперь есть отдача от ударов.",
                "Каждый удачный удар в ближнем бою отбрасывает от противника.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }

    public static boolean eligible(ServerPlayerEntity player, Entity target) {
        return ChallengeActivity.active(player, ID) && !player.hasVehicle()
                && target instanceof LivingEntity living && living.isAlive() && !(target instanceof ArmorStandEntity);
    }

    /** Runs after the whole attack, including vanilla sprint damping and enchantment reactions. */
    public static void recoil(ServerPlayerEntity player, LivingEntity target) {
        if (!ChallengeActivity.active(player, ID) || player.hasVehicle()) return;
        double x = player.getX() - target.getX(), z = player.getZ() - target.getZ();
        double length = Math.hypot(x, z);
        if (length == 0) {
            double yaw = Math.toRadians(player.getYaw());
            x = Math.sin(yaw);
            z = -Math.cos(yaw);
        } else {
            x /= length;
            z /= length;
        }
        player.addVelocity(x * HORIZONTAL_IMPULSE, 0, z * HORIZONTAL_IMPULSE);
        // Vanilla tracker sends the final velocity to observers and to the affected player.
        player.velocityModified = true;
    }
}
