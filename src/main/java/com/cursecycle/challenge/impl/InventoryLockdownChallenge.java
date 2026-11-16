package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class InventoryLockdownChallenge extends Challenge {
    public static final String ID = "inventory_lockdown";

    // Технические нюансы; не включать в описание для игрока.
    // В Survival/Adventure блокируются слоты 9–35, броня и вторая рука: перенос, Shift-клик, двойной клик,
    // обмен F, надевание правой кнопкой и раздатчиком. Контейнеры и верстаки работают, но перенос в закрытые
    // слоты запрещён. Подбор и книга рецептов используют хотбар; при его заполнении лут остаётся на земле.
    // Смерть и выпадение штатные. Creative и Spectator исключены; завершение снимает ограничения.
    public InventoryLockdownChallenge() {
        super(ID, "Судебное решение",
                "Инвентарь опечатали, больше из него не достать вещи.",
                "До вещей в рюкзаке больше не добраться. Доступны только девять ячеек быстрого доступа, а надетое сменить нельзя, но хоть защищает",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    public static boolean isActiveForPlayer(PlayerEntity player) {
        if (player == null || player.isCreative() || player.isSpectator()) return false;
        if (player.getWorld().isClient()) {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                    && ClientDataHolder.getActiveChallengeIds().contains(ID);
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return false;
        Challenge challenge = ChallengeRegistry.get(ID);
        MinecraftServer server = serverPlayer.getServer();
        if (challenge == null || !challenge.isEnabled() || server == null) return false;
        CurseCycleState state = CurseCycleState.getServerState(server);
        return state.getActiveChallengeIds().contains(ID)
                || state.findPersonalActiveChallengeIds(player.getUuid()).contains(ID);
    }

    public static boolean isLockedIndex(int index) {
        return index >= PlayerInventory.getHotbarSize() && index <= PlayerInventory.OFF_HAND_SLOT;
    }

    public static boolean isLockedSlot(Slot slot) {
        // Screen slot IDs differ between inventory, chest, furnace, merchant, etc.
        return slot.inventory instanceof PlayerInventory inventory
                && isLockedIndex(slot.getIndex()) && isActiveForPlayer(inventory.player);
    }

    public static boolean blocksClick(ScreenHandler handler, int slotId, int button,
                                      SlotActionType action, PlayerEntity player) {
        if (!isActiveForPlayer(player)) return false;
        if (action == SlotActionType.SWAP && isLockedIndex(button)) return true;
        return slotId >= 0 && slotId < handler.slots.size() && isLockedSlot(handler.getSlot(slotId));
    }

    @Override
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) {
            player.sendMessage(Text.literal("[Curse-Cycle] Пломбы сорваны, можно снова забивать рюкзак хламом.")
                    .formatted(Formatting.GRAY), false);
        }
    }

    @Override
    public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        // Stateless restrictions: leaving does not end the challenge.
    }
    // The standard activation announcement already includes the requested challenge name.
}
