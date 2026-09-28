package com.example.hardcorechallenges.challenge;

import com.example.hardcorechallenges.api.challenge.BaseChallenge;
import com.example.hardcorechallenges.api.challenge.ChallengeContext;
import com.example.hardcorechallenges.api.challenge.ChallengeDefinition;
import com.example.hardcorechallenges.api.challenge.ChallengeScope;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TestChallenge extends BaseChallenge {

    public static final Identifier ID = Identifier.of("hardcorechallenges", "test_challenge");

    private int ticksActive = 0;

    public TestChallenge() {
        super(new ChallengeDefinition(
                ID,
                "Test Challenge",
                "A simple challenge for testing the architecture. You take double damage!",
                "test",
                1,
                6000, // 5 minutes (5 * 60 * 20 ticks)
                ChallengeScope.GLOBAL
        ));
    }

    @Override
    public void onActivate(ChallengeContext context) {
        super.onActivate(context);
        if (context.server() != null) {
            context.server().getPlayerManager().broadcast(Text.literal("§c[!] Test Challenge Activated! Prepare yourself!"), false);
        }
    }

    @Override
    public void onDeactivate(ChallengeContext context) {
        super.onDeactivate(context);
        if (context.server() != null) {
            context.server().getPlayerManager().broadcast(Text.literal("§a[!] Test Challenge Deactivated! You survived!"), false);
        }
    }

    @Override
    public void tick(ChallengeContext context) {
        super.tick(context);
        ticksActive++;
        if (ticksActive % 200 == 0) { // Каждые 10 секунд
            if (context.server() != null) {
                System.out.println("[HardcoreChallenges] Test Challenge is active for " + ticksActive + " ticks.");
            }
        }
    }

    @Override
    public void onDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (getState() == com.example.hardcorechallenges.api.challenge.ChallengeState.ACTIVE) {
            player.sendMessage(Text.literal("§cOuch! The challenge makes it hurt more!"), true);
        }
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("TicksActive", ticksActive);
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("TicksActive")) {
            this.ticksActive = nbt.getInt("TicksActive");
        }
    }
}
