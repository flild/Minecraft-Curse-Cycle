package validation;

import com.cursecycle.client.*;
import com.cursecycle.challenge.impl.HeavyBodyChallenge;
import com.cursecycle.network.*;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.*;
import net.minecraft.client.option.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.world.GameMode;
import sun.misc.Unsafe;
import java.util.*;

/** Actual CLIENT transforms and production gates; synthetic accessors, no window/network/travel. */
public final class HeavyBodyProbe implements PreLaunchEntrypoint {
    private static MinecraftClient client;
    private static boolean alive = true, water, lava, climbing, riding, elytra, sprinting, ground = true;
    private static GameMode mode = GameMode.SURVIVAL;
    private static final PlayerAbilities ABILITIES = new PlayerAbilities();
    private static int checks, ticks;
    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public net.minecraft.world.World getWorld() { return HeavyBodyProbe.client.world; }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isCreative() { return mode == GameMode.CREATIVE; }
        @Override public boolean isSpectator() { return mode == GameMode.SPECTATOR; }
        @Override public boolean isTouchingWater() { return water; }
        @Override public boolean isInLava() { return lava; }
        @Override public boolean isClimbing() { return climbing; }
        @Override public boolean hasVehicle() { return riding; }
        @Override public boolean isFallFlying() { return elytra; }
        @Override public boolean isOnGround() { return ground; }
        @Override public PlayerAbilities getAbilities() { return ABILITIES; }
        @Override public void setSprinting(boolean value) { sprinting = value; }
        @Override public boolean isSprinting() { return sprinting; }
        @Override public float getMovementSpeed() { return .25F; }
        @Override protected float getOffGroundSpeed() { return .02F; }
    }
    public static class Manager extends ClientPlayerInteractionManager {
        public Manager() { super(null, null); }
        @Override public GameMode getCurrentGameMode() { return mode; }
    }
    private static void set(Class<?> owner, Object object, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static Object get(Class<?> owner, Object object, String name) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void check(boolean condition, String why) { checks++; if (!condition) throw new AssertionError(why); }
    private static void near(float expected, float actual) { check(Math.abs(expected - actual) < .00001, "Expected " + expected + " actual " + actual); }
    private static void sync(String... ids) { ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of()); }
    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion(); net.minecraft.Bootstrap.initialize();
            var field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); var unsafe = (Unsafe) field.get(null);
            client = (MinecraftClient) unsafe.allocateInstance(MinecraftClient.class); set(MinecraftClient.class, null, "instance", client);
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class); set(net.minecraft.world.World.class, client.world, "isClient", true);
            client.player = (Player) unsafe.allocateInstance(Player.class); client.interactionManager = (Manager) unsafe.allocateInstance(Manager.class);
            var options = (GameOptions) unsafe.allocateInstance(GameOptions.class); set(MinecraftClient.class, client, "options", options);
            String[] names = {"forwardKey", "backKey", "leftKey", "rightKey", "jumpKey", "sneakKey", "sprintKey"};
            var keys = new KeyBinding[names.length];
            for (int i = 0; i < names.length; i++) { keys[i] = new KeyBinding("probe." + names[i], 300 + i, KeyBinding.MOVEMENT_CATEGORY); set(GameOptions.class, options, names[i], keys[i]); }
            var input = new KeyboardInput(options); client.player.input = input;
            var speed = LivingEntity.class.getDeclaredMethod("getMovementSpeed", float.class); speed.setAccessible(true);
            check(Arrays.stream(LivingEntity.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("cursecycle$heavyJump")), "CLIENT jump handler transformed");
            for (boolean inverted : new boolean[]{false, true}) for (String blocked : new String[]{"", "forward", "left", "right"})
                for (int mask = 0; mask < 128; mask++) for (float factor : new float[]{1, .3f, .2f}) {
                    var ids = new ArrayList<>(List.of("heavy_body", "blocked_direction")); if (inverted) ids.add("inverted_keyboard");
                    ClientDataHolder.updateState(1, 20, ids, "", List.of(), blocked);
                    for (int k = 0; k < keys.length; k++) keys[k].setPressed((mask & (1 << k)) != 0);
                    input.tick(factor != 1, factor); ticks++;
                    float forward = ((mask & 1) != 0 ? 1 : 0) - ((mask & 2) != 0 ? 1 : 0);
                    float sideways = ((mask & 4) != 0 ? 1 : 0) - ((mask & 8) != 0 ? 1 : 0);
                    if (inverted) { forward = -forward; sideways = -sideways; }
                    if (blocked.equals("forward")) forward = Math.min(0, forward);
                    if (blocked.equals("left")) sideways = Math.min(0, sideways);
                    if (blocked.equals("right")) sideways = Math.max(0, sideways);
                    near(forward * factor, input.movementForward); near(sideways * factor, input.movementSideways);
                    check(HeavyBodyInput.moving(client) == (forward != 0 || sideways != 0), "Observes actual transformed final input");
                    near(.15F, (float) speed.invoke(client.player, .6F));
                    check(input.jumping == ((mask & 16) != 0) && input.sneaking == ((mask & 32) != 0), "No extra input restrictions");
                }
            sync("heavy_body"); input.movementForward = 1; input.movementSideways = 0;
            for (int excluded = 0; excluded < 10; excluded++) {
                alive = excluded != 0; mode = excluded == 1 ? GameMode.CREATIVE : excluded == 2 ? GameMode.SPECTATOR : GameMode.SURVIVAL;
                water = excluded == 3; lava = excluded == 4; climbing = excluded == 5; riding = excluded == 6; elytra = excluded == 7; ABILITIES.flying = excluded == 8;
                if (excluded == 9) set(net.minecraft.entity.Entity.class, client.player, "removalReason", net.minecraft.entity.Entity.RemovalReason.KILLED);
                check(!HeavyBodyInput.moving(client), "Excluded final intent " + excluded); near(.25F, (float) speed.invoke(client.player, .6F));
            }
            client.player = (Player) unsafe.allocateInstance(Player.class); client.player.input = input;
            alive = true; mode = GameMode.ADVENTURE; water = lava = climbing = riding = elytra = ABILITIES.flying = false;
            near(.15F, (float) speed.invoke(client.player, .6F)); ground = false; near(.02F, (float) speed.invoke(client.player, .6F)); ground = true;
            client.currentScreen = (net.minecraft.client.gui.screen.ChatScreen) unsafe.allocateInstance(net.minecraft.client.gui.screen.ChatScreen.class);
            check(!HeavyBodyInput.moving(client), "GUI cancels intent"); client.currentScreen = null;
            sync("heavy_body", "movement_memory"); MovementMemoryInput.receive(new MovementMemoryPayload(90, -1, false));
            for (var key : keys) key.setPressed(false); keys[0].setPressed(true); input.tick(false, .3f); ticks++;
            keys[0].setPressed(false); input.tick(false, .3f); ticks++;
            check(HeavyBodyInput.moving(client), "Memory movement is own effective intent");
            ClientDataHolder.updateState(1, 20, List.of("heavy_body"), "", List.of()); input.tick(false, .3f); ticks++;
            check(!HeavyBodyInput.moving(client), "Ending memory stops final intent and retains heavy"); near(.15F, (float) speed.invoke(client.player, .6F));
            sync("heavy_body", "silent_world"); check(SilentWorld.shouldMute(client), "Silent pair gate"); near(.15F, (float) speed.invoke(client.player, .6F));
            var listener = unsafe.allocateInstance(net.minecraft.client.sound.SoundListener.class);
            var mute = Arrays.stream(listener.getClass().getDeclaredMethods()).filter(m -> m.getName().contains("cursecycle$muteOutput")).findFirst().orElseThrow(); mute.setAccessible(true);
            near(0, (float) mute.invoke(listener, .35F));
            sync("heavy_body"); near(.35F, (float) mute.invoke(listener, .35F)); near(.15F, (float) speed.invoke(client.player, .6F));
            sync("silent_world"); check(SilentWorld.shouldMute(client), "Ending heavy preserves mute"); near(.25F, (float) speed.invoke(client.player, .6F));
            var intent = (HeavyBodyInput.Intent) get(HeavyBodyInput.class, null, "INTENT"); var sent = new ArrayList<HeavyBodyPayload>();
            for (int cycle = 0; cycle < 32; cycle++) {
                sync("heavy_body"); HeavyBodyInput.receive(new HeavyBodyPayload(100 + cycle, false));
                sent.clear(); intent.report(client.player, client.world, true, sent::add);
                for (int repeat = 0; repeat < 500; repeat++) { HeavyBodyInput.receive(new HeavyBodyPayload(100 + cycle, false)); intent.report(client.player, client.world, true, sent::add); }
                check(sent.size() == 1, "Periodic context does not spam intent");
                intent.report(client.player, client.world, false, sent::add); check(sent.size() == 2 && !sent.getLast().moving(), "One release transition");
                ClientDataHolder.reset(); check(get(HeavyBodyInput.Intent.class, intent, "player") == null && get(HeavyBodyInput.Intent.class, intent, "world") == null, "Reset releases references");
                HeavyBodyInput.receive(new HeavyBodyPayload(100 + cycle, false)); intent.report(client.player, client.world, true, sent::add);
                check(sent.size() == 2, "Late context after end cannot reactivate intent");
            }
            System.out.println("HEAVY_BODY_REVIEW_CLIENT_PASSED checks=" + checks + " ticks=" + ticks + " actual transforms/gates; synthetic context; no window/network/travel/OpenAL");
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
