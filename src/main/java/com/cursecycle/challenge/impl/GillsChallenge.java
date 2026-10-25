package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;

public class GillsChallenge extends Challenge {
    public static final String ID = "gills";
    public GillsChallenge() {
        super(ID, "Зато под водой дышать можно", "Воздух заканчивается на суше и восстанавливается под водой.",
                "Воздух расходуется на суше: полного запаса хватает на 15 секунд, затем каждую секунду наносится 2 единицы урона удушьем. Погружение глаз в воду восстанавливает воздух и позволяет дышать без ограничения. Дождь, мокрые ноги и котёл не заменяют погружение головы. Водное дыхание, Морская сила и зачарование Подводное дыхание не отменяют сухопутную потерю воздуха.", ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }
    private final Map<MinecraftServer, Map<UUID, Integer>> lastTicks = new WeakHashMap<>();
    public static boolean active(PlayerEntity player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) return false;
        if (player.getWorld().isClient()) return ClientDataHolder.isSynced() && ClientDataHolder.getActiveChallengeIds().contains(ID);
        return ChallengeActivity.active(player, ID);
    }
    public static final class AirSupply {
        public int air, dryTicks;
        public AirSupply(int air, int dryTicks) { this.air = Math.clamp(air, 0, 300); this.dryTicks = Math.clamp(dryTicks, 0, 19); }
        public boolean step(boolean water) {
            if (water) { air = Math.min(300, air + 4); dryTicks = 0; return false; }
            if (air > 0) { air--; dryTicks = 0; return false; }
            if (++dryTicks >= 20) { dryTicks = 0; return true; }
            return false;
        }
    }
    private static NbtCompound data(ServerPlayerEntity player) {
        var state = CurseCycleState.getServerState(player.getServer());
        var root = state.getChallengeData();
        var players = root.getCompound(ID);
        var data = players.getCompound(player.getUuidAsString());
        if (!data.contains("air")) { data.putInt("air", 300); data.putInt("dryTicks", 0); }
        players.put(player.getUuidAsString(), data);
        root.put(ID, players);
        return data;
    }
    public static void updateBreathing(ServerPlayerEntity player) {
        if (!ChallengeActivity.active(player, ID)) return;
        var data = data(player);
        var supply = new AirSupply(data.getInt("air"), data.getInt("dryTicks"));
        boolean damage = supply.step(player.isSubmergedIn(FluidTags.WATER));
        data.putInt("air", supply.air);
        data.putInt("dryTicks", supply.dryTicks);
        CurseCycleState.getServerState(player.getServer()).markDirty();
        player.setAir(supply.air);
        if (damage) player.damage(player.getDamageSources().drown(), 2F);
    }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null && ChallengeActivity.active(player, ID)) {
            player.setAir(data(player).getInt("air"));
            CurseCycleState.getServerState(server).markDirty();
        }
    }
    @Override public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null || !ChallengeActivity.active(player, ID)) return;
        var ticks = lastTicks.computeIfAbsent(server, s -> new HashMap<>());
        if (Objects.equals(ticks.put(player.getUuid(), server.getTicks()), server.getTicks())) return;
        updateBreathing(player);
    }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) {
        if (!alive) CurseCycleState.getServerState(server).getChallengeData().getCompound(ID).remove(newPlayer.getUuidAsString());
        onStart(server, newPlayer);
    }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) player.setAir(Math.clamp(player.getAir(), 0, player.getMaxAir()));
    }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) { }
}
