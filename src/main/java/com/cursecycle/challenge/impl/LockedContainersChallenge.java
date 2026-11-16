package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class LockedContainersChallenge extends Challenge {
    public static final String ID = "locked_containers";

    // Технические нюансы; не включать в описание для игрока.
    // Запрещён доступ к инвентарям обычных, двойных сундуков и сундуков-ловушек, всех шалкеров и
    // эндер-сундуков, включая сундуки в лодках и вагонетках. Вещи не удаляются; перенос и разрушение
    // разрешены. Бочки, печи и другие контейнеры не блокируются.
    public LockedContainersChallenge() {
        super(ID, "Сундуки не для тебя",
                "Сундуки, шалкеры и эндер-сундуки недоступны.",
                "Сундуки, шалкеровые ящики и эндер-сундуки больше не открываются. Их содержимое пока не для тебя.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    private static boolean forbidden(Inventory inventory) {
        // Vanilla combines the two halves of a chest in DoubleInventory; a barrel
        // uses the same screen type as a single chest, but a different inventory.
        return inventory instanceof ChestBlockEntity || inventory instanceof DoubleInventory
                || inventory instanceof EnderChestInventory || inventory instanceof ShulkerBoxBlockEntity
                || inventory instanceof ChestBoatEntity || inventory instanceof ChestMinecartEntity;
    }

    public static boolean blocks(ScreenHandler handler, PlayerEntity player) {
        if (handler == null || !ChallengeActivity.active(player, ID)) return false;
        return handler.slots.stream().anyMatch(slot -> forbidden(slot.inventory));
    }

    public static void closeForbidden(ServerPlayerEntity player) {
        if (blocks(player.currentScreenHandler, player)) player.closeHandledScreen();
    }

    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { closeForbidden(player); }
    @Override public void onTick(MinecraftServer server, ServerPlayerEntity player) { closeForbidden(player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) { closeForbidden(newPlayer); }
}
