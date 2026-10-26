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

    public AttackRecoilChallenge() {
        super(ID, "Как в контрстрайке", "Успешный удар в ближнем бою отбрасывает самого игрока назад.",
                "Успешный прямой удар по основной живой цели добавляет к текущей горизонтальной "
                        + "скорости импульс 0,6 блока за тик от цели. Вертикальная скорость не меняется. "
                        + "При совпадении горизонтальных координат отдача направлена против взгляда. "
                        + "Цель должна потерять здоровье или поглощение до возможного срабатывания тотема. "
                        + "Один взмах даёт одну отдачу, даже при sweep-атаке по группе. Промахи, блоки, "
                        + "стойки для брони, полностью заблокированные или отменённые удары, урон без "
                        + "потери здоровья и поглощения, снаряды, огонь, шипы и другие косвенные источники "
                        + "не вызывают отдачу. Приседание и сопротивление отбрасыванию её не уменьшают. "
                        + "Пассажир транспорта исключён: транспорт не ускоряется, игрок не спешивается. "
                        + "Урон и отбрасывание цели остаются ванильными. Действует на живых игроков "
                        + "Survival/Adventure во всех измерениях; Creative и Spectator исключены. "
                        + "Завершение отключает новые импульсы, не обнуляя уже полученную скорость.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
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
