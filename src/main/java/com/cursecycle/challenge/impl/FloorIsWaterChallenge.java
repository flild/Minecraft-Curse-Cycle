package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeDifficulty;
import com.cursecycle.challenge.ChallengeScope;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public class FloorIsWaterChallenge extends Challenge {
    public static final String ID = "floor_is_water";

    public FloorIsWaterChallenge() {
        super(ID, "Водобоязнь", "Касание воды приводит к мгновенной смерти.",
                "Вода — это смерть. Во время действия испытания любое соприкосновение игрока "
                        + "с водой (как стоячей, так и текущей, включая воду в затопленных блоках) "
                        + "мгновенно убивает его. Броня, зелья и тотемы не спасают. "
                        + "Дождь, капли капельника и вода в котле не считаются блоками воды. "
                        + "Использовать зелья разрешено. Игроки в Creative и Spectator игнорируются.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    @Override
    public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) return;

        Box box = player.getBoundingBox();
        var world = player.getServerWorld();
        // Inspect actual fluid at the current position rather than the cached touchingWater flag.
        // Only the few blocks intersecting the player's hitbox are visited, including its sides/head.
        for (BlockPos pos : BlockPos.iterate(
                MathHelper.floor(box.minX), MathHelper.floor(box.minY), MathHelper.floor(box.minZ),
                MathHelper.ceil(box.maxX) - 1, MathHelper.ceil(box.maxY) - 1, MathHelper.ceil(box.maxZ) - 1)) {
            FluidState fluid = world.getFluidState(pos);
            if (fluid.isIn(FluidTags.WATER) && pos.getY() + fluid.getHeight(world, pos) > box.minY) {
                // The vanilla /kill path bypasses armor, resistance, totems and login invulnerability.
                player.kill();
                // ServerPlayerEntity rejects even /kill while awaiting a dimension-transfer ack.
                // Contact is still lethal during that window, using the normal player death hook.
                if (player.isAlive()) {
                    player.setHealth(0.0F);
                    player.onDeath(player.getDamageSources().genericKill());
                }
                return;
            }
        }
    }

    @Override
    public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) {
            player.sendMessage(Text.literal("[Curse-Cycle] Испытание «Водобоязнь» завершено.")
                    .formatted(Formatting.GRAY), false);
        }
    }

    @Override
    public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) {
        // No handlers, modifiers or player state to clean up; disconnecting is not completion.
    }
}
