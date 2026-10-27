package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class CraftingTaxChallenge extends Challenge {
    public static final String ID = "crafting_tax";
    private static final Map<MinecraftServer, Map<UUID, Notice>> NOTICES = new WeakHashMap<>();
    private record Notice(long activation, int tick) {}

    public CraftingTaxChallenge() {
        super(ID, "Крафт по талонам", "Каждое выполнение рецепта крафта стоит один уровень опыта.",
                "Получение результата из личной сетки 2×2 или верстака 3×3 стоит один уровень "
                        + "за каждый израсходованный комплект ингредиентов, независимо от числа предметов. "
                        + "Без полного уровня результат и ингредиенты остаются на месте. Shift-клик выполняет "
                        + "только оплачиваемые крафты, результат которых целиком помещается в доступные слоты. "
                        + "Дробный прогресс опыта сохраняется по ванильным правилам оплаты уровней. "
                        + "Просмотр и книга рецептов бесплатны. Плавка, камнерез, кузнечный, ткацкий, "
                        + "картографический столы, торговля, наковальня и автоматический Crafter не облагаются налогом. "
                        + "Действует на живых игроков Survival/Adventure во всех измерениях; Creative и Spectator исключены. "
                        + "Оплата не наносит урон от bitter_experience. После завершения потраченные уровни не возвращаются.",
                ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }

    public static boolean canPay(PlayerEntity player) {
        if (!ChallengeActivity.active(player, ID) || player.experienceLevel >= 1) return true;
        var server = player.getServer();
        long activation = CurseCycleState.getServerState(server).getActivationSerial();
        var notices = NOTICES.computeIfAbsent(server, ignored -> new HashMap<>());
        var previous = notices.get(player.getUuid());
        int tick = server.getTicks();
        if (previous == null || previous.activation != activation || tick - previous.tick >= 40) {
            player.sendMessage(Text.literal("Нужен 1 уровень опыта"), true);
            notices.put(player.getUuid(), new Notice(activation, tick));
        }
        return false;
    }

    public static void pay(PlayerEntity player) {
        if (ChallengeActivity.active(player, ID)) player.addExperienceLevels(-1);
    }

    /** Vanilla quickMove can insert a fraction and drop the rest. Require space for one whole execution. */
    public static boolean fitsQuickMove(ScreenHandler handler, ItemStack result) {
        if (result.isEmpty()) return false;
        int start = handler instanceof PlayerScreenHandler ? 9 : 10;
        int remaining = result.getCount();
        for (int i = start; i < start + 36; i++) {
            var slot = handler.getSlot(i);
            if (InventoryLockdownChallenge.isLockedSlot(slot)) continue;
            var held = slot.getStack();
            if (held.isEmpty()) {
                if (slot.canInsert(result)) remaining -= slot.getMaxItemCount(result);
            } else if (result.isStackable() && ItemStack.areItemsAndComponentsEqual(held, result)) {
                remaining -= Math.max(0, slot.getMaxItemCount(result) - held.getCount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        var notices = NOTICES.get(server);
        if (notices != null && player != null) notices.remove(player.getUuid());
    }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) {
        onStop(server, oldPlayer);
    }
}
