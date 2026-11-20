package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import com.cursecycle.challenge.FloorIsLavaPlacements;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.particle.ParticleTypes;
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
    private static final float DAMAGE = 3.0F;
    private static final int DAMAGE_INTERVAL = 20;

    // Registered challenges are singletons. Each player has an independent clock, and a
    // replacement player entity after respawning must start receiving damage immediately.
    private final Map<UUID, DamagePulse> lastPulses = new HashMap<>();
    private record DamagePulse(ServerPlayerEntity player, int age) {}

    // Технические нюансы; не включать в описание для игрока.
    // Отмеченные установки текущего раунда, кровати, шерсть, ковры и снопы сена безопасны.
    // Учёт установок ограничен и не сохраняется между раундами/запусками. Остальная твёрдая опора
    // включает блоки, полублоки, ступени и заборы. Урон — 3 единицы при касании и далее раз в 20
    // тиков; повторное касание не ускоряет его. Броня, щит, защитные чары, сопротивление, огнестойкость и
    // неуязвимость после удара или входа не защищают. Поглощение и тотемы работают штатно. Без опоры под
    // ногами плавание, полёт на элитрах, лодки, вагонетки, лестницы и лианы не вызывают этот урон. Урон самой
    // лавы сохраняется. Затрагиваются только игроки Survival/Adventure во всех измерениях, не мобы и не
    // транспорт.
    public FloorIsLavaChallenge() {
        super(ID, "Пол — это лава!",
                "Пол обжигает. Ставь блоки во время испытания, чтобы создать безопасную опору.",
                "Касание твёрдого пола отнимает полтора сердца каждую секунду. Блоки, поставленные игроками во время текущего испытания, безопасны. После завершения испытания или перезапуска сервера отметки сбрасываются. Учёт ограничен: при его переполнении новые блоки остаются опасными. Кровати, шерсть, шерстяные ковры и снопы сена всегда безопасны; в кровати можно спать.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    @Override
    public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) return;
        if (player.isSleeping()) return;
        if (player.getVehicle() instanceof BoatEntity || player.getVehicle() instanceof AbstractMinecartEntity) return;

        Box box = player.getBoundingBox();
        // Same thin slice as Entity.updateSupportingBlockPos, queried at the current
        // position rather than using cached onGround/support data after a teleport.
        Box feet = new Box(box.minX, box.minY - 1.0E-6, box.minZ, box.maxX, box.minY, box.maxZ);
        var world = player.getServerWorld();
        var support = world.findSupportingBlockPos(player, feet);
        if (support.isEmpty()) return;
        if (FloorIsLavaPlacements.isSafe(world, support.get())) return;
        var floor = world.getBlockState(support.get());
        if (floor.isIn(BlockTags.BEDS) || floor.isIn(BlockTags.WOOL)
                || floor.isIn(BlockTags.WOOL_CARPETS) || floor.isOf(Blocks.HAY_BLOCK)) return;

        DamagePulse last = lastPulses.get(player.getUuid());
        if (last != null && last.player() == player && player.age - last.age() < DAMAGE_INTERVAL) return;

        DamageSource source = new DamageSource(player.getRegistryManager()
                .get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE_TYPE));
        if (player.damage(source, DAMAGE)) {
            lastPulses.put(player.getUuid(), new DamagePulse(player, player.age));
            var center = box.getCenter();
            world.spawnParticles(ParticleTypes.LAVA, center.x, box.minY + 0.05, center.z,
                    3, 0.2, 0.02, 0.2, 0);
        }
    }

    @Override
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) {
            FloorIsLavaPlacements.clear(server);
            lastPulses.clear();
        } else {
            // The shared marks may still protect another PERSONAL recipient. The
            // activation serial and server tick clear them when the round actually ends.
            lastPulses.remove(player.getUuid());
            player.sendMessage(Text.literal("[Curse-Cycle] Испытание «Пол — это лава!» завершено.")
                    .formatted(Formatting.GRAY), false);
        }
    }

    @Override
    public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) lastPulses.remove(player.getUuid());
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            FloorIsLavaPlacements.clearAll();
            lastPulses.clear();
        }
    }

    public static void clear(MinecraftServer server) {
        FloorIsLavaPlacements.clear(server);
        var challenge = com.cursecycle.challenge.ChallengeRegistry.get(ID);
        if (challenge instanceof FloorIsLavaChallenge lava)
            lava.lastPulses.values().removeIf(pulse -> pulse.player().getServer() == server);
    }
}
