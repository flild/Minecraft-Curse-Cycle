package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.DamageReceipt;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class DamageTeleportChallenge extends Challenge {
    public static final String ID = "damage_teleport";
    private static final int[] HEIGHT_OFFSETS = {0, 1, -1, 2, -2};

    public DamageTeleportChallenge() {
        super(ID, "Почти как эндермен", "Прошедший урон телепортирует на 5 блоков в сторону.",
                "После фактически прошедшего несмертельного урона игрок Survival/Adventure переносится "
                        + "на безопасную точку в 5 блоках по горизонтали. Направление случайное; допускается "
                        + "поправка высоты до 2 блоков. Проверяются восемь перемешанных направлений, "
                        + "для каждого высоты: исходная, +1, -1, +2, -2. Нужны загруженные чанки, "
                        + "безопасная твёрдая опора и свободный хитбокс внутри границы мира, без жидкости, "
                        + "огня, лавы, магмы, кактуса, ягодных кустов, рыхлого снега и порталов. "
                        + "Если подходящей точки нет, переноса нет. Потеря поглощения тоже считается уроном; "
                        + "спасение тотемом допускает перенос. Полностью заблокированный урон, i-frames "
                        + "без потери здоровья/поглощения и прямое изменение здоровья переноса не вызывают. "
                        + "Мёртвые игроки, пассажиры, Creative и Spectator исключены. "
                        + "Сохраняется направление взгляда, скорость и fallDistance обнуляются. "
                        + "Дополнительного урона и отложенных попыток нет; завершение не возвращает старую позицию.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    @Override
    public void onPlayerDamageApplied(MinecraftServer server, ServerPlayerEntity player, DamageReceipt damage) {
        if (!ChallengeActivity.active(player, ID) || player.hasVehicle() || player.getServerWorld() != damage.world) return;
        List<Integer> directions = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7));
        for (int i = directions.size() - 1; i > 0; i--) {
            int other = player.getRandom().nextInt(i + 1);
            int value = directions.get(i);
            directions.set(i, directions.get(other));
            directions.set(other, value);
        }
        findDestination(damage.position, directions, pos -> isSafe(player, damage.world, pos)).ifPresent(pos -> {
            player.teleport(damage.world, pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0;
            player.velocityModified = true;
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
        });
    }

    static List<Vec3d> candidates(Vec3d origin, List<Integer> directions) {
        List<Vec3d> result = new ArrayList<>(40);
        for (int direction : directions) {
            double angle = direction * Math.PI / 4;
            for (int height : HEIGHT_OFFSETS)
                result.add(origin.add(5 * Math.cos(angle), height, 5 * Math.sin(angle)));
        }
        return result;
    }

    static Optional<Vec3d> findDestination(Vec3d origin, List<Integer> directions, Predicate<Vec3d> safe) {
        return candidates(origin, directions).stream().filter(safe).findFirst();
    }

    public static boolean isSafe(ServerPlayerEntity player, ServerWorld world, Vec3d position) {
        Box body = player.getBoundingBox().offset(position.subtract(player.getPos()));
        Box feet = new Box(body.minX, body.minY - 1.0E-6, body.minZ, body.maxX, body.minY, body.maxZ);
        if (feet.minY < world.getBottomY() || body.maxY > world.getTopY()
                || !world.getWorldBorder().contains(body)) return false;
        int minX = MathHelper.floor(body.minX), maxX = MathHelper.floor(Math.nextDown(body.maxX));
        int minZ = MathHelper.floor(body.minZ), maxZ = MathHelper.floor(Math.nextDown(body.maxZ));
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++)
                if (!world.getChunkManager().isChunkLoaded(x, z)) return false;
        }
        if (!world.isSpaceEmpty(player, body)) return false;
        for (BlockPos pos : BlockPos.iterate(minX, MathHelper.floor(body.minY), minZ,
                maxX, MathHelper.floor(Math.nextDown(body.maxY)), maxZ)) {
            BlockState state = world.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || hazardous(state)) return false;
        }
        boolean supported = false;
        // Fence/wall collisions extend above their block cell. Check every actual support,
        // including waterlogged supports below the body's own cells.
        for (BlockPos pos : BlockPos.iterate(minX, MathHelper.floor(feet.minY) - 1, minZ,
                maxX, MathHelper.floor(feet.maxY), maxZ)) {
            if (!world.isInBuildLimit(pos)) continue;
            BlockState state = world.getBlockState(pos);
            boolean touches = state.getCollisionShape(world, pos, ShapeContext.of(player)).getBoundingBoxes().stream()
                    .anyMatch(box -> box.offset(pos).intersects(feet));
            if (!touches) continue;
            if (!state.getFluidState().isEmpty() || hazardous(state)) return false;
            supported = true;
        }
        return supported;
    }

    private static boolean hazardous(BlockState state) {
        return state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.LAVA)
                || state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.POWDER_SNOW)
                || state.isOf(Blocks.NETHER_PORTAL) || state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.END_GATEWAY);
    }
}
