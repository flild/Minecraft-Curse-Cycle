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

    public InventoryLockdownChallenge() {
        super(ID, "Пломба на карманах", "Инвентарь заблокирован, работает только хотбар.",
                "Во время действия испытания главный инвентарь игрока блокирован. "
                        + "Из него нельзя забирать предметы, в него нельзя ничего положить, "
                        + "а также нельзя перетаскивать вещи внутри него. Хотбар (нижняя панель из 9 слотов) "
                        + "остается полностью интерактивным. То же самое касается слотов брони и второй руки — "
                        + "взаимодействие с ними блокируется, чтобы игроки не могли легко снять или сменить снаряжение. "
                        + "Сундуки, печи и верстаки работают штатно, но складывать лут в заблокированный инвентарь напрямую нельзя. "
                        + "В Survival и Adventure заблокированы слоты 9–35, броня и вторая рука, включая Shift-клик, "
                        + "двойной клик, обмен клавишей F и надевание брони правой кнопкой или раздатчиком. "
                        + "Подбор с земли и книга рецептов используют только хотбар; если он заполнен, лут остаётся на земле. "
                        + "Смерть и выпадение вещей работают по обычным правилам. Creative и Spectator исключены. "
                        + "После завершения испытания все ограничения снимаются.",
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
                || state.getPersonalActiveChallengeIds(player.getUuid()).contains(ID);
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
