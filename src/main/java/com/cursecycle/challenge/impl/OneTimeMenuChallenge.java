package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.network.FoodHistoryPayload;
import com.cursecycle.state.CurseCycleState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.*;

public class OneTimeMenuChallenge extends Challenge {
    public static final String ID = "one_time_menu";
    public OneTimeMenuChallenge() {
        super(ID, "Одноразовое меню", "Каждый вид еды можно съесть только один раз за испытание.",
                "Каждый тип еды можно успешно съесть только один раз за текущую активацию. Сырая и приготовленная еда — разные виды, а имя, зачарования и содержимое компонентов не делают тот же предмет новым видом. Каждый игрок ведёт собственный список. Смерть, выход и перезапуск список не сбрасывают. Молоко и зелья к еде не относятся; один кусок торта считается использованием вида «торт».", ChallengeDifficulty.EASY, ChallengeScope.GLOBAL);
    }
    private static final Map<UUID, Long> feedback = new HashMap<>();
    public static boolean blocked(PlayerEntity player, String food) {
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) return false;
        if (player.getWorld().isClient())
            return ClientDataHolder.isSynced() && ClientDataHolder.getActiveChallengeIds().contains(ID)
                    && ClientDataHolder.hasEatenFood(food);
        if (!(player instanceof ServerPlayerEntity p) || !ChallengeActivity.active(p, ID)) return false;
        boolean seen = history(p).contains(NbtString.of(food));
        if (seen) {
            long tick = p.getServer().getTicks();
            if (tick - feedback.getOrDefault(p.getUuid(), tick - 40) >= 40) {
                feedback.put(p.getUuid(), tick);
                p.sendMessage(Text.literal("Этот вид уже съеден"), true);
            }
        }
        return seen;
    }
    private static NbtList history(ServerPlayerEntity player) {
        return CurseCycleState.getServerState(player.getServer()).getChallengeData().getCompound(ID)
                .getList(player.getUuidAsString(), NbtElement.STRING_TYPE);
    }
    public static void consumed(PlayerEntity player, String food) {
        if (!(player instanceof ServerPlayerEntity p) || !ChallengeActivity.active(p, ID)) return;
        var state = CurseCycleState.getServerState(p.getServer());
        var root = state.getChallengeData();
        var data = root.getCompound(ID);
        var list = data.getList(p.getUuidAsString(), NbtElement.STRING_TYPE);
        if (!list.contains(NbtString.of(food))) list.add(NbtString.of(food));
        data.put(p.getUuidAsString(), list);
        root.put(ID, data);
        state.markDirty();
        sync(p);
    }
    private static void sync(ServerPlayerEntity player) {
        List<String> ids = new ArrayList<>();
        for (var entry : history(player)) ids.add(entry.asString());
        ServerPlayNetworking.send(player, new FoodHistoryPayload(ids));
    }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { if (player != null) sync(player); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) { sync(newPlayer); }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) feedback.remove(player.getUuid());
    }
}
