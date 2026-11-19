package com.cursecycle.challenge.impl;

import com.cursecycle.challenge.*;
import com.cursecycle.state.CurseCycleState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

import java.util.*;

/** A persistent logical hunter; a generation token fences off stale, unloaded entity copies. */
public final class SkeletonHunterChallenge extends Challenge {
    public static final String ID = "skeleton_hunter";
    public static final int RESPAWN_TICKS = 3600;
    public static final int WARNING_TICKS = 100;
    private static final Map<MinecraftServer, Runtime> RUNTIMES = new IdentityHashMap<>();

    private static final class Runtime {
        CurseCycleState state;
        long activation;
        final Set<MobEntity> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<UUID, Pursuit> pursuits = new HashMap<>();
    }
    private static final class Pursuit {
        final ServerPlayerEntity player;
        final ServerWorld world;
        Vec3d anchor;
        long progress;
        long warned = Long.MIN_VALUE;
        Pursuit(ServerPlayerEntity p, long now) {
            player = p; world = p.getServerWorld(); progress = now;
        }
    }

    public SkeletonHunterChallenge() {
        super(ID, "За тобой выехали", "Особый скелет с луком постоянно преследует тебя.",
                "У каждого игрока свой охотник. После его смерти новый появится через три минуты. "
                        + "Перед появлением или переносом отставшего охотника есть предупреждение за пять секунд.",
                ChallengeDifficulty.HARD, ChallengeScope.GLOBAL);
    }

    private static Runtime runtime(MinecraftServer server) {
        var rt = RUNTIMES.computeIfAbsent(server, ignored -> new Runtime());
        var state = CurseCycleState.getServerState(server);
        if (rt.state != state || rt.activation != state.getActivationSerial()) {
            rt.pursuits.clear(); rt.state = state; rt.activation = state.getActivationSerial();
        }
        return rt;
    }
    private static long now(MinecraftServer server) { return server.getOverworld().getTime(); }
    private static NbtCompound records(CurseCycleState state) {
        var root = state.getChallengeData();
        if (!root.contains(ID)) root.put(ID, new NbtCompound());
        return root.getCompound(ID);
    }
    private static boolean assigned(CurseCycleState state, UUID owner) {
        var challenge = ChallengeRegistry.get(ID);
        return challenge != null && challenge.isEnabled() && (state.getActiveChallengeIds().contains(ID)
                || state.findPersonalActiveChallengeIds(owner).contains(ID));
    }
    private static NbtCompound record(MinecraftServer server, UUID owner) {
        return records(CurseCycleState.getServerState(server)).getCompound(owner.toString());
    }
    private static boolean matches(MobEntity mob) {
        if (!(mob instanceof SkeletonEntity) || !(mob.getWorld() instanceof ServerWorld world)) return false;
        var mark = (HunterMark) mob;
        var owner = mark.cursecycle$hunterOwner();
        if (owner == null) return false;
        var state = CurseCycleState.getServerState(world.getServer());
        var rec = record(world.getServer(), owner);
        return mark.cursecycle$hunterActivation() == state.getActivationSerial()
                && rec.containsUuid("hunter") && mob.getUuid().equals(rec.getUuid("hunter"))
                && rec.containsUuid("token") && rec.getUuid("token").equals(mark.cursecycle$hunterToken());
    }
    private static boolean valid(MobEntity mob) {
        return matches(mob) && assigned(CurseCycleState.getServerState(((ServerWorld) mob.getWorld()).getServer()),
                ((HunterMark) mob).cursecycle$hunterOwner());
    }
    public static ServerPlayerEntity target(MobEntity mob) {
        if (!valid(mob)) return null;
        var world = (ServerWorld) mob.getWorld();
        var player = world.getServer().getPlayerManager().getPlayer(((HunterMark) mob).cursecycle$hunterOwner());
        return player != null && player.getServerWorld() == world && ChallengeActivity.active(player, ID)
                && world.getDifficulty() != Difficulty.PEACEFUL ? player : null;
    }
    private static MobEntity find(MinecraftServer server, NbtCompound rec) {
        if (!rec.containsUuid("hunter")) return null;
        for (var world : server.getWorlds()) {
            var entity = world.getEntity(rec.getUuid("hunter")); // Loaded UUID index only; never loads a chunk.
            if (entity instanceof MobEntity mob && matches(mob)) return mob;
        }
        return null;
    }

    public static void loaded(Entity entity, ServerWorld world) {
        if (!(entity instanceof MobEntity mob) || ((HunterMark) mob).cursecycle$hunterOwner() == null) return;
        if (!valid(mob)) { mob.discard(); return; }
        runtime(world.getServer()).loaded.add(mob);
    }
    public static void unloaded(Entity entity, ServerWorld world) {
        if (!(entity instanceof MobEntity mob) || ((HunterMark) mob).cursecycle$hunterOwner() == null) return;
        var rt = RUNTIMES.get(world.getServer());
        if (rt != null) rt.loaded.remove(mob);
        // The removal reason is already set at UNLOAD; isAlive() would reject a living unloaded mob.
        if (mob.getHealth() > 0 && valid(mob)) snapshot(mob);
    }
    private static void snapshot(MobEntity mob) {
        var server = ((ServerWorld) mob.getWorld()).getServer();
        var saved = mob.writeNbt(new NbtCompound());
        saved.putString("id", "minecraft:skeleton");
        saved.remove("Passengers");
        record(server, ((HunterMark) mob).cursecycle$hunterOwner()).put("snapshot", saved);
        CurseCycleState.getServerState(server).markDirty();
    }
    public static void died(LivingEntity entity) {
        if (!(entity instanceof MobEntity mob) || !valid(mob)) return;
        var server = ((ServerWorld) mob.getWorld()).getServer();
        var owner = ((HunterMark) mob).cursecycle$hunterOwner();
        var rec = record(server, owner);
        rec.remove("hunter"); rec.remove("token"); rec.remove("snapshot");
        rec.putLong("respawnAt", now(server) + RESPAWN_TICKS);
        runtime(server).pursuits.remove(owner);
        CurseCycleState.getServerState(server).markDirty();
    }
    public static void forget(ServerPlayerEntity player) {
        var rt = RUNTIMES.get(player.getServer());
        if (rt != null) rt.pursuits.remove(player.getUuid());
        // Ownership and the death timer survive disconnect/death. The mob cannot target others.
    }
    public static void clear(MinecraftServer server) { RUNTIMES.remove(server); }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        var state = CurseCycleState.getServerState(server);
        var rt = runtime(server);
        var all = records(state);
        for (var key : new ArrayList<>(all.getKeys())) {
            if (!assigned(state, UUID.fromString(key))) { all.remove(key); state.markDirty(); }
        }
        for (var mob : new ArrayList<>(rt.loaded)) {
            if (!valid(mob)) mob.discard();
            else if (mob.isAlive()) snapshot(mob);
        }
        rt.pursuits.entrySet().removeIf(e -> !ChallengeActivity.active(e.getValue().player, ID)
                || server.getPlayerManager().getPlayer(e.getKey()) != e.getValue().player);
        for (var player : server.getPlayerManager().getPlayerList()) check(player);
    }

    public static void check(ServerPlayerEntity player) {
        var server = player.getServer();
        var rt = runtime(server);
        if (!ChallengeActivity.active(player, ID) || player.getServerWorld().getDifficulty() == Difficulty.PEACEFUL) {
            rt.pursuits.remove(player.getUuid()); return;
        }
        var state = CurseCycleState.getServerState(server);
        var all = records(state);
        String key = player.getUuidAsString();
        if (!all.contains(key)) { all.put(key, new NbtCompound()); state.markDirty(); }
        var rec = all.getCompound(key);
        long time = now(server);
        if (time < rec.getLong("respawnAt")) return;
        var pursuit = rt.pursuits.get(player.getUuid());
        if (pursuit == null || pursuit.player != player || pursuit.world != player.getServerWorld()) {
            pursuit = new Pursuit(player, time); rt.pursuits.put(player.getUuid(), pursuit);
        }
        var mob = find(server, rec);
        if (mob != null && !mob.isAlive()) return; // Only AFTER_DEATH schedules a replacement.
        boolean far = mob == null || mob.getWorld() != player.getWorld() || mob.squaredDistanceTo(player) > 48 * 48;
        if (mob != null && (pursuit.anchor == null || pursuit.anchor.squaredDistanceTo(mob.getPos()) > 4)) {
            pursuit.anchor = mob.getPos(); pursuit.progress = time;
        }
        boolean stuck = mob != null && mob.squaredDistanceTo(player) > 16 * 16 && time - pursuit.progress >= 1200;
        if (!far && !stuck) { pursuit.warned = Long.MIN_VALUE; return; }
        if (pursuit.warned == Long.MIN_VALUE) {
            player.sendMessage(Text.translatable("text.cursecycle.skeleton_hunter.warning"), false);
            pursuit.warned = time;
            return;
        }
        if (time - pursuit.warned < WARNING_TICKS) return;
        relocate(player, rec, mob, pursuit, time);
    }

    private static void relocate(ServerPlayerEntity player, NbtCompound rec, MobEntity old, Pursuit pursuit, long time) {
        var server = player.getServer();
        var world = player.getServerWorld();
        if (old != null) snapshot(old);
        // An absent entity with an assigned UUID is NOT a death or a fresh spawn.
        if (rec.containsUuid("hunter") && !rec.contains("snapshot")) return;
        var mob = rec.contains("snapshot")
                ? (SkeletonEntity) EntityType.loadEntityWithPassengers(rec.getCompound("snapshot").copy(), world, e -> e)
                : EntityType.SKELETON.create(world);
        if (mob == null) return;
        if (!rec.containsUuid("hunter")) {
            mob.initialize(world, world.getLocalDifficulty(player.getBlockPos()), SpawnReason.EVENT, null);
            mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(30);
            mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(.30);
            mob.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE).setBaseValue(96);
            mob.setHealth(30);
            mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            mob.setCustomName(Text.translatable("entity.cursecycle.skeleton_hunter"));
        }
        Vec3d destination = destination(player, mob);
        if (destination == null) return; // Wait; never substitute an unsafe or unloaded location.
        mob.setPersistent(); mob.setCanPickUpLoot(false);
        for (var slot : EquipmentSlot.values()) mob.setEquipmentDropChance(slot, 0);
        mob.refreshPositionAndAngles(destination.x, destination.y, destination.z, player.getYaw(), 0);
        mob.setVelocity(Vec3d.ZERO); mob.fallDistance = 0;
        var token = UUID.randomUUID();
        ((HunterMark) mob).cursecycle$markHunter(player.getUuid(), token,
                CurseCycleState.getServerState(server).getActivationSerial());
        // Invalidate the disk copy BEFORE spawning its successor, including a later load in another dimension.
        rec.putUuid("hunter", mob.getUuid()); rec.putUuid("token", token);
        CurseCycleState.getServerState(server).markDirty();
        if (old != null) old.discard();
        if (!world.spawnEntity(mob)) {
            var saved = mob.writeNbt(new NbtCompound()); saved.putString("id", "minecraft:skeleton");
            rec.put("snapshot", saved); return;
        }
        snapshot(mob);
        pursuit.anchor = mob.getPos(); pursuit.progress = time; pursuit.warned = Long.MIN_VALUE;
    }

    private static Vec3d destination(ServerPlayerEntity player, MobEntity mob) {
        int start = player.getRandom().nextInt(8);
        for (int radius : new int[]{12, 16, 20}) {
            for (int i = 0; i < 8; i++) {
                double angle = (start + i) * Math.PI / 4;
                for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4}) {
                    var pos = new Vec3d(Math.floor(player.getX() + radius * Math.cos(angle)) + .5,
                            Math.floor(player.getY()) + dy, Math.floor(player.getZ() + radius * Math.sin(angle)) + .5);
                    if (DamageTeleportChallenge.isSafe(mob, player.getServerWorld(), pos)) return pos;
                }
            }
        }
        return null;
    }

    @Override public void onStart(MinecraftServer server, ServerPlayerEntity player) { check(player); }
    @Override public void onPlayerLeave(MinecraftServer server, ServerPlayerEntity player) { forget(player); }
    @Override public void onStop(MinecraftServer server, ServerPlayerEntity player) {
        var all = records(CurseCycleState.getServerState(server));
        var mob = find(server, all.getCompound(player.getUuidAsString()));
        all.remove(player.getUuidAsString());
        CurseCycleState.getServerState(server).markDirty();
        if (mob != null) mob.discard();
        forget(player);
    }
}
