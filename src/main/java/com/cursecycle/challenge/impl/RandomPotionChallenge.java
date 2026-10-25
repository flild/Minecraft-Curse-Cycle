package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.challenge.effect.EffectLedger;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.*;

public class RandomPotionChallenge extends Challenge {
    public static final String ID = "random_potion";
    public RandomPotionChallenge() {
        super(ID, "Лотырея от провизора", "Каждые 30 секунд — случайный эффект зелья.",
                "Каждые 30 секунд серверного времени каждый игрок независимо получает случайный эффект зелья I уровня. Первый выбор происходит сразу. Обычный эффект длится до следующего выбора, мгновенный применяется один раз. Список эффектов фиксирован в правилах; дополнительные штрафы не назначаются. Равновероятный пул: minecraft:speed, slowness, strength, instant_health, instant_damage, jump_boost, regeneration, resistance, fire_resistance, water_breathing, invisibility, night_vision, weakness, poison, slow_falling, wind_charged, weaving, oozing, infested. Все перечисленные короткие ID имеют пространство minecraft. Молоко снимает вклад до очередного выбора. Creative и Spectator исключены.", ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }
    public static final List<String> POOL = List.of("speed", "slowness", "strength", "instant_health", "instant_damage",
            "jump_boost", "regeneration", "resistance", "fire_resistance", "water_breathing", "invisibility",
            "night_vision", "weakness", "poison", "slow_falling", "wind_charged", "weaving", "oozing", "infested");
    private final Map<MinecraftServer, Map<UUID, Timer>> timers = new WeakHashMap<>();
    private Map<UUID, Timer> timers(MinecraftServer server) { return timers.computeIfAbsent(server, s -> new HashMap<>()); }

    public static final class Clock {
        private long next;
        public Clock(long now) { next = now + 600; }
        public boolean due(long now) {
            if (now < next) return false;
            next = now + 600;
            return true;
        }
    }
    private record Timer(long activation, Clock clock) {}

    private void select(ServerPlayerEntity player) {
        EffectLedger.of(player).remove(ID);
        RegistryEntry<StatusEffect> effect = Registries.STATUS_EFFECT.getEntry(
                Identifier.ofVanilla(POOL.get(player.getRandom().nextInt(POOL.size())))).orElseThrow();
        if (effect.value().isInstant()) effect.value().applyInstantEffect(null, null, player, 0, 1.0);
        else EffectLedger.of(player).maintain(ID, effect, 0, 600, 0);
    }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { onTick(server, player); }
    @Override public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (!ChallengeActivity.active(player, ID)) { onStop(server, player); return; }
        long activation = CurseCycleState.getServerState(server).getActivationSerial();
        Timer timer = timers(server).get(player.getUuid());
        if (timer == null || timer.activation != activation) {
            timers(server).put(player.getUuid(), new Timer(activation, new Clock(server.getTicks())));
            select(player);
        } else if (timer.clock.due(server.getTicks())) select(player);
    }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return;
        timers(server).remove(player.getUuid());
        EffectLedger.of(player).remove(ID);
    }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) { onStop(server, player); }
    @Override public void onPlayerJoin(MinecraftServer server, ServerPlayerEntity player) {
        onStop(server, player);
        onStart(server, player);
    }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer,
                                          ServerPlayerEntity newPlayer, boolean alive) {
        if (!alive) onStop(server, newPlayer);
        onTick(server, newPlayer);
    }
}
