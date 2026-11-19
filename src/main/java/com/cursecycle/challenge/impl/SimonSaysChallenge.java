package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Server-observed actions; no C2S success packet or client clock. */
public final class SimonSaysChallenge extends Challenge {
    public static final String ID = "simon_says";
    public static final int MIN_INTERVAL = 1200, MAX_INTERVAL = 1800, WINDOW = 100, STOP_TICKS = 10;
    public static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of("cursecycle", ID));
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new HashMap<>();

    public enum Action { JUMP, CROUCH, STOP, SWAP, TURN, LOOK_UP, LOOK_DOWN, SWING,
        DOUBLE_JUMP, DOUBLE_CROUCH, WALK, SPRINT }

    public SimonSaysChallenge() {
        super(ID, "Саймон говорит", "Выполняй случайные команды за пять секунд.",
                "Каждые 60–90 секунд выполняй случайную команду: прыгни, присядь, остановись, смени предмет, "
                        + "развернись, посмотри вверх или вниз, махни рукой, прыгни или присядь дважды, "
                        + "пройди три блока или начни бежать. На выполнение пять секунд; "
                        + "за провал теряешь два сердца.",
                ChallengeDifficulty.MEDIUM, ChallengeScope.GLOBAL);
    }

    private static final class Session {
        final ServerPlayerEntity player;
        final Object world;
        final CurseCycleState state;
        final long activation;
        final int start;
        int lastTick, stillTicks, slot;
        long next, issued;
        Action action;
        Vec3d position;
        ItemStack held = ItemStack.EMPTY;
        boolean sneaking;
        float yaw;
        boolean lookUpArmed, lookDownArmed;
        boolean sprintArmed;
        int jumps, crouches, lastJumpTick;
        double walked;

        Session(ServerPlayerEntity p) {
            player = p;
            world = p.getWorld();
            state = CurseCycleState.getServerState(p.getServer());
            activation = state.getActivationSerial();
            start = lastTick = p.getServer().getTicks();
            next = interval(p);
        }
        long age() { return Integer.toUnsignedLong(player.getServer().getTicks() - start); }
        boolean matches(ServerPlayerEntity p) {
            return player == p && p.networkHandler.player == p && p.getWorld() == world
                    && CurseCycleState.getServerState(p.getServer()) == state
                    && state.getActivationSerial() == activation;
        }
    }

    private static int interval(ServerPlayerEntity p) {
        return MIN_INTERVAL + p.getRandom().nextInt(MAX_INTERVAL - MIN_INTERVAL + 1);
    }
    private static boolean active(ServerPlayerEntity p) {
        return !p.isRemoved() && ChallengeActivity.active(p, ID)
                && p.getServer().getPlayerManager().getPlayer(p.getUuid()) == p;
    }
    private static Session session(ServerPlayerEntity p) {
        var sessions = SESSIONS.computeIfAbsent(p.getServer(), ignored -> new HashMap<>());
        var s = sessions.get(p.getUuid());
        if (s == null || !s.matches(p)) {
            if (s != null) clearHud(s);
            s = new Session(p);
            sessions.put(p.getUuid(), s);
        }
        return s;
    }

    /** Only commands possible with the player's current companions and inventory. */
    public static List<Action> availableActions(ServerPlayerEntity p) {
        var actions = new ArrayList<>(List.of(Action.CROUCH, Action.DOUBLE_CROUCH, Action.TURN,
                Action.LOOK_UP, Action.LOOK_DOWN, Action.SWING));
        if (!ChallengeActivity.active(p, NoJumpingChallenge.ID) && HeavyBodyChallenge.onFoot(p)) {
            actions.add(Action.JUMP);
            actions.add(Action.DOUBLE_JUMP);
        }
        if (HeavyBodyChallenge.onFoot(p)) {
            actions.add(Action.WALK);
            if (!ChallengeActivity.active(p, NoSprintChallenge.ID) && !HeavyBodyChallenge.cannotSprint(p)
                    && p.getHungerManager().getFoodLevel() > 6) actions.add(Action.SPRINT);
        }
        if (!ChallengeActivity.active(p, "stuck_throttle") && !ChallengeActivity.active(p, AutoJumpChallenge.ID)
                && !ChallengeActivity.active(p, AdhdNoStandingStillChallenge.ID)) actions.add(Action.STOP);
        for (int i = 0; i < 9; i++) {
            if (i != p.getInventory().selectedSlot
                    && !ItemStack.areItemsAndComponentsEqual(p.getMainHandStack(), p.getInventory().getStack(i))) {
                actions.add(Action.SWAP);
                break;
            }
        }
        return actions;
    }

    private static void issue(Session s) {
        var actions = availableActions(s.player);
        s.action = actions.get(s.player.getRandom().nextInt(actions.size()));
        s.issued = s.age();
        // Interval is between command starts, not between a completion and the next command.
        s.next = s.issued + interval(s.player);
        s.position = s.player.getPos();
        s.slot = s.player.getInventory().selectedSlot;
        s.held = s.player.getMainHandStack().copy();
        s.sneaking = s.player.isSneaking();
        s.yaw = s.player.getYaw();
        s.lookUpArmed = s.player.getPitch() > -60;
        s.lookDownArmed = s.player.getPitch() < 60;
        s.sprintArmed = !s.player.isSprinting();
        s.jumps = s.crouches = 0;
        s.lastJumpTick = s.player.getServer().getTicks() - 1;
        s.walked = 0;
        s.stillTicks = 0;
        send(s);
    }
    private static void send(Session s) {
        if (s.action != null) s.player.sendMessage(Text.translatable("text.cursecycle.simon_says.command",
                Text.translatable("text.cursecycle.simon_says." + s.action.name().toLowerCase(Locale.ROOT)),
                Math.max(0, (WINDOW - (s.age() - s.issued) + 19) / 20)), true);
    }
    private static void clearHud(Session s) {
        if (s.action != null) s.player.sendMessage(Text.empty(), true);
    }
    private static void complete(Session s) {
        s.action = null;
        s.player.sendMessage(Text.translatable("text.cursecycle.simon_says.success"), true);
    }
    /** Reuse the existing hook after LivingEntity.jump installs a real impulse. */
    public static void jumped(ServerPlayerEntity p) {
        observed(p, Action.JUMP);
    }
    /** Called by the vanilla hand-swing receiver after its main-thread check. */
    public static void swung(ServerPlayerEntity p) {
        observed(p, Action.SWING);
    }
    private static void observed(ServerPlayerEntity p, Action action) {
        var sessions = SESSIONS.get(p.getServer());
        var s = sessions == null ? null : sessions.get(p.getUuid());
        if (s == null || !s.matches(p) || !active(p) || s.age() <= s.issued || s.age() > s.issued + WINDOW) return;
        if (action == Action.JUMP && s.action == Action.DOUBLE_JUMP) {
            int now = p.getServer().getTicks();
            if (s.lastJumpTick == now) return;
            s.lastJumpTick = now;
            if (++s.jumps == 2) complete(s);
        } else if (s.action == action) complete(s);
    }

    /** Ordinary sync is idempotent and cannot extend a command's deadline. */
    public static void sync(ServerPlayerEntity p) {
        if (active(p)) send(session(p));
        else forget(p);
    }
    public static void tick(MinecraftServer server) {
        var sessions = SESSIONS.get(server);
        if (sessions != null) for (var s : List.copyOf(sessions.values())) {
            if (!active(s.player) || !s.matches(s.player)) forget(s.player);
        }
        for (var p : server.getPlayerManager().getPlayerList()) {
            if (!active(p)) continue;
            var s = session(p);
            int now = server.getTicks();
            if (s.lastTick == now) continue;
            boolean consecutive = now - s.lastTick == 1;
            s.lastTick = now;
            long age = s.age();
            if (s.action == null) {
                if (age >= s.next) issue(s);
                continue;
            }
            // If the available set changes mid-window, cancel rather than impose an impossible penalty.
            if (!availableActions(p).contains(s.action) && s.action != Action.SWAP) {
                clearHud(s); s.action = null; continue;
            }
            if (age > s.issued + WINDOW) {
                s.action = null; // Before damage: callbacks may kill, teleport or stop this challenge.
                p.sendMessage(Text.translatable("text.cursecycle.simon_says.failure"), true);
                p.damage(new DamageSource(p.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE_TYPE)), 4F);
                continue;
            }
            boolean success = switch (s.action) {
                case JUMP, DOUBLE_JUMP, SWING -> false;
                case CROUCH -> p.isSneaking() && !s.sneaking;
                case DOUBLE_CROUCH -> p.isSneaking() && !s.sneaking && ++s.crouches >= 2;
                case STOP -> {
                    s.stillTicks = consecutive && p.getPos().squaredDistanceTo(s.position) <= 0.0001
                            ? s.stillTicks + 1 : 0;
                    yield s.stillTicks >= STOP_TICKS;
                }
                case SWAP -> p.getInventory().selectedSlot != s.slot
                        && !ItemStack.areItemsAndComponentsEqual(s.held, p.getMainHandStack());
                case TURN -> Math.abs(MathHelper.wrapDegrees(p.getYaw() - s.yaw)) >= 150;
                case LOOK_UP -> s.lookUpArmed && p.getPitch() <= -60;
                case LOOK_DOWN -> s.lookDownArmed && p.getPitch() >= 60;
                case WALK -> {
                    double distance = p.getPos().subtract(s.position).horizontalLength();
                    // Ignore discontinuities such as a teleport; ordinary footsteps still count after it.
                    if (consecutive && distance <= 1) s.walked += distance;
                    yield s.walked >= 3;
                }
                case SPRINT -> s.sprintArmed && p.isSprinting()
                        && p.getPos().subtract(s.position).horizontalLengthSquared() > 0.0001;
            };
            if (p.getPitch() > -60) s.lookUpArmed = true;
            if (p.getPitch() < 60) s.lookDownArmed = true;
            if (!p.isSprinting()) s.sprintArmed = true;
            s.sneaking = p.isSneaking();
            s.position = p.getPos();
            if (success) complete(s);
            else if ((age - s.issued) % 20 == 0) send(s);
        }
    }
    public static void forget(ServerPlayerEntity p) {
        var sessions = SESSIONS.get(p.getServer());
        var s = sessions == null ? null : sessions.get(p.getUuid());
        if (s == null || s.player != p) return;
        clearHud(s);
        sessions.remove(p.getUuid());
        if (sessions.isEmpty()) SESSIONS.remove(p.getServer());
    }
    public static void clear(MinecraftServer server) { SESSIONS.remove(server); }
    @Override public void onStart(MinecraftServer server, ServerPlayerEntity p) { sync(p); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity p) { forget(p); }
    @Override public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        forget(oldPlayer); sync(newPlayer);
    }
}
