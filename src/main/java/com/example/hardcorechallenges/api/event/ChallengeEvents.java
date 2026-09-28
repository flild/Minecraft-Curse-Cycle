package com.example.hardcorechallenges.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class ChallengeEvents {

    // DAMAGE
    public interface Damage {
        void onDamage(ServerPlayerEntity player, DamageSource source, float amount);
    }
    public static final Event<Damage> ON_DAMAGE = EventFactory.createArrayBacked(Damage.class,
            (listeners) -> (player, source, amount) -> {
                for (Damage listener : listeners) {
                    listener.onDamage(player, source, amount);
                }
            }
    );

    // MOVEMENT
    public interface Movement {
        void onMove(ServerPlayerEntity player, Vec3d from, Vec3d to);
    }
    public static final Event<Movement> ON_MOVE = EventFactory.createArrayBacked(Movement.class,
            (listeners) -> (player, from, to) -> {
                for (Movement listener : listeners) {
                    listener.onMove(player, from, to);
                }
            }
    );

    // JUMP
    public interface Jump {
        void onJump(ServerPlayerEntity player);
    }
    public static final Event<Jump> ON_JUMP = EventFactory.createArrayBacked(Jump.class,
            (listeners) -> (player) -> {
                for (Jump listener : listeners) {
                    listener.onJump(player);
                }
            }
    );

    // SPRINT
    public interface Sprint {
        void onSprintStart(ServerPlayerEntity player);
        void onSprintStop(ServerPlayerEntity player);
    }
    public static final Event<Sprint> ON_SPRINT = EventFactory.createArrayBacked(Sprint.class,
            (listeners) -> new Sprint() {
                @Override
                public void onSprintStart(ServerPlayerEntity player) {
                    for (Sprint listener : listeners) {
                        listener.onSprintStart(player);
                    }
                }

                @Override
                public void onSprintStop(ServerPlayerEntity player) {
                    for (Sprint listener : listeners) {
                        listener.onSprintStop(player);
                    }
                }
            }
    );

    private ChallengeEvents() {}
}
