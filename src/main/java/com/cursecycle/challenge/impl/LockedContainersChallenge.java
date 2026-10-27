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

    public LockedContainersChallenge() {
        super(ID, "Сундуки не для тебя", "Сундуки, шалкеры и эндер-сундуки недоступны.",
                "Нельзя открывать и использовать инвентари обычных и сундуков-ловушек, двойных сундуков, "
                        + "шалкеровых ящиков любого цвета и эндер-сундуков. Запрет включает сундуки в лодках "
                        + "и вагонетках. Предметы не удаляются: переносить и ломать эти блоки, пользоваться "
                        + "бочками, печами и прочими контейнерами разрешено. При активации запрещённый экран "
                        + "закрывается с обычным возвратом предмета на курсоре. Автоматизация и перенос "
                        + "шалкеров как предметов работают. Creative и Spectator исключены. "
                        + "После завершения хранилища снова доступны.",
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
