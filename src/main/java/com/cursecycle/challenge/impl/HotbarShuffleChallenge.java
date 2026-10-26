package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeActivity;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;

public class HotbarShuffleChallenge extends Challenge {
    public static final String ID = "hotbar_shuffle";
    public static final int INTERVAL_TICKS = 100;

    // Values contain no server/player references. Neither clocks nor RNG are persisted.
    private final Map<MinecraftServer, Map<UUID, Timer>> timers = new WeakHashMap<>();
    private record Timer(long activation, Clock clock) {}

    public HotbarShuffleChallenge() {
        super(ID, "Курчу верчу запутать хочу", "Каждые 5 секунд хотбар перемешивается.",
                "Каждые 5 секунд девять слотов хотбара перемешиваются независимо у каждого игрока. Стеки не делятся и не объединяются, выбранный номер слота остаётся прежним — предмет в руке может смениться. Основной инвентарь, броня, вторая рука и предмет на курсоре не меняются.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    public static final class Clock {
        private long next;
        public Clock(long now) { next = now + INTERVAL_TICKS; }
        public boolean due(long now) {
            if (now < next) return false;
            next = now + INTERVAL_TICKS;
            return true;
        }
    }

    /** Fisher-Yates, conditioned on an observable change. Equal stacks include count/components. */
    public static <T> List<T> shuffled(List<T> original, BiPredicate<T, T> equal, IntUnaryOperator nextInt) {
        if (original.isEmpty() || original.stream().allMatch(stack -> equal.test(original.getFirst(), stack)))
            return original;
        while (true) {
            var result = new ArrayList<>(original);
            for (int i = result.size() - 1; i > 0; i--) {
                int j = nextInt.applyAsInt(i + 1);
                T swap = result.get(i);
                result.set(i, result.get(j));
                result.set(j, swap);
            }
            for (int i = 0; i < result.size(); i++)
                if (!equal.test(original.get(i), result.get(i))) return result;
        }
    }

    private void shuffle(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        var before = new ArrayList<ItemStack>(PlayerInventory.getHotbarSize());
        for (int i = 0; i < PlayerInventory.getHotbarSize(); i++) before.add(inventory.getStack(i));
        var after = shuffled(before, ItemStack::areEqual, player.getRandom()::nextInt);
        if (after == before) return;

        if (player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND
                && !ItemStack.areEqual(before.get(inventory.selectedSlot), after.get(inventory.selectedSlot))) {
            // Cancel without onStoppedUsing: releasing a bow/crossbow here could consume ammunition.
            player.clearActiveItem();
        }
        // END_SERVER_TICK and slot-click handling run on the same server thread. No intermediate sync.
        for (int i = 0; i < after.size(); i++) inventory.setStack(i, after.get(i));
        inventory.markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { onTick(server, player); }

    @Override public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player.isRemoved() || !ChallengeActivity.active(player, ID)) { onStop(server, player); return; }
        long activation = CurseCycleState.getServerState(server).getActivationSerial();
        var players = timers.computeIfAbsent(server, ignored -> new HashMap<>());
        var timer = players.get(player.getUuid());
        if (timer == null || timer.activation != activation) {
            players.put(player.getUuid(), new Timer(activation, new Clock(server.getTicks())));
        } else if (timer.clock.due(server.getTicks())) shuffle(player);
    }

    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        var players = timers.get(server);
        if (players == null || player == null) return;
        players.remove(player.getUuid());
        if (players.isEmpty()) timers.remove(server);
    }

    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) { onStop(server, player); }
    @Override public void onPlayerJoin(MinecraftServer server, ServerPlayerEntity player) {
        onStop(server, player);
        onStart(server, player);
    }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) {
        if (!alive) onStop(server, oldPlayer);
        // A live End return may replace the entity; UUID and the remaining interval stay the same.
        onTick(server, newPlayer);
    }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) timers.clear();
    }
}
