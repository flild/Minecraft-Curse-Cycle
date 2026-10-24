package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FloorIsLavaChallenge extends Challenge {
    public static final String ID = "the_floor_is_lava";
    public static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of("cursecycle", ID));
    private static final float DAMAGE = 6.0F;
    private static final int DAMAGE_INTERVAL = 20;

    // Registered challenges are singletons. Each player has an independent clock, and a
    // replacement player entity after respawning must start receiving damage immediately.
    private final Map<UUID, DamagePulse> lastPulses = new HashMap<>();
    private record DamagePulse(ServerPlayerEntity player, int age) {}

    public FloorIsLavaChallenge() {
        super(ID, "Пол — это лава!", "Стоять на твёрдых блоках вредно для здоровья. Очень вредно.",
                "Каждый раз, когда игрок касается ногами твёрдого блока, полублока, ступенек, "
                        + "забора или аналогичной твёрдой опоры, он немедленно получает 3 сердца "
                        + "(6 единиц) урона, затем раз в секунду, пока остаётся на опоре. "
                        + "Повторное касание не ускоряет урон: между ударами проходит минимум 20 тиков. "
                        + "Броня, щит, защитные чары и сопротивление не уменьшают этот урон; "
                        + "огнестойкость и неуязвимость после удара или входа не спасают. "
                        + "Поглощение и тотемы работают по обычным правилам Minecraft. "
                        + "Можно плавать в воде или лаве без твёрдой опоры под ногами, летать на элитрах, "
                        + "сидеть в лодке или вагонетке, висеть на лестнице или лиане без опоры под стопами. "
                        + "Обычный урон лавы сохраняется. Игроки в Survival и Adventure затрагиваются "
                        + "во всех измерениях; Creative, Spectator, мобы и транспорт исключены.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    @Override
    public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) return;
        if (player.getVehicle() instanceof BoatEntity || player.getVehicle() instanceof AbstractMinecartEntity) return;

        Box box = player.getBoundingBox();
        // Same thin slice as Entity.updateSupportingBlockPos, queried at the current
        // position rather than using cached onGround/support data after a teleport.
        Box feet = new Box(box.minX, box.minY - 1.0E-6, box.minZ, box.maxX, box.minY, box.maxZ);
        if (player.getServerWorld().findSupportingBlockPos(player, feet).isEmpty()) return;

        DamagePulse last = lastPulses.get(player.getUuid());
        if (last != null && last.player() == player && player.age - last.age() < DAMAGE_INTERVAL) return;

        DamageSource source = new DamageSource(player.getRegistryManager()
                .get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE_TYPE));
        if (player.damage(source, DAMAGE)) {
            lastPulses.put(player.getUuid(), new DamagePulse(player, player.age));
        }
    }

    @Override
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) {
            lastPulses.clear();
        } else {
            lastPulses.remove(player.getUuid());
            player.sendMessage(Text.literal("[Curse-Cycle] Испытание «Пол — это лава!» завершено.")
                    .formatted(Formatting.GRAY), false);
        }
    }

    @Override
    public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) lastPulses.remove(player.getUuid());
    }
}
