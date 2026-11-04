package validation;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.InvertedKeyboardInput;
import com.cursecycle.client.MovementMemoryInput;
import com.cursecycle.network.MovementMemoryPayload;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.world.GameMode;
import sun.misc.Unsafe;

import java.util.List;

/** Real transformed input and gates; synthetic client/world/player accessors, no window or connection. */
public final class InvertedKeyboardProbe implements PreLaunchEntrypoint {
    private static final String ID = "inverted_keyboard";
    private static boolean alive = true, riding, water, lava, climbing, elytra;
    private static GameMode mode = GameMode.SURVIVAL;
    private static final PlayerAbilities ABILITIES = new PlayerAbilities();
    private static MinecraftClient client;
    private static KeyBinding[] keys;
    private static int checks, ticks;

    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean hasVehicle() { return riding; }
        @Override public boolean isTouchingWater() { return water; }
        @Override public boolean isInLava() { return lava; }
        @Override public boolean isClimbing() { return climbing; }
        @Override public boolean isFallFlying() { return elytra; }
        @Override public PlayerAbilities getAbilities() { return ABILITIES; }
    }
    public static class Manager extends ClientPlayerInteractionManager {
        public Manager() { super(null, null); }
        @Override public GameMode getCurrentGameMode() { return mode; }
    }
    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
    private static void set(Class<?> owner, Object object, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static void sync(String... ids) {
        ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of());
    }
    private static void press(int mask) {
        for (int i = 0; i < keys.length; i++) keys[i].setPressed((mask & (1 << i)) != 0);
    }
    private static void tick(KeyboardInput input, boolean slow, float factor) {
        input.tick(slow, factor); ticks++;
    }
    private static void forward(KeyboardInput input, boolean inverted) {
        press(1); tick(input, false, .3f);
        check(input.movementForward == (inverted ? -1 : 1), "Current eligibility and fresh input");
    }
    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion(); net.minecraft.Bootstrap.initialize();
            var field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
            var unsafe = (Unsafe) field.get(null);
            client = (MinecraftClient) unsafe.allocateInstance(MinecraftClient.class);
            set(MinecraftClient.class, null, "instance", client);
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class);
            client.player = (Player) unsafe.allocateInstance(Player.class);
            client.interactionManager = (Manager) unsafe.allocateInstance(Manager.class);
            var options = (GameOptions) unsafe.allocateInstance(GameOptions.class);
            set(MinecraftClient.class, client, "options", options);
            String[] names = {"forwardKey", "backKey", "leftKey", "rightKey", "jumpKey", "sneakKey", "sprintKey"};
            keys = new KeyBinding[names.length];
            for (int i = 0; i < names.length; i++) {
                keys[i] = new KeyBinding("probe." + names[i], 300 + i, KeyBinding.MOVEMENT_CATEGORY);
                set(GameOptions.class, options, names[i], keys[i]);
            }
            var input = new KeyboardInput(options); client.player.input = input;
            check(java.util.Arrays.stream(KeyboardInput.class.getDeclaredMethods())
                    .anyMatch(m -> m.getName().contains("cursecycle$invertKeyboard")), "Production Mixin transformed KeyboardInput");
            // No test Mixin or replacement of shouldInvert: every tick passes through the real gate.
            for (boolean active : new boolean[]{false, true, true, false, true}) {
                sync(active ? ID : "no_sprint");
                for (int mask = 0; mask < 128; mask++) for (float factor : new float[]{1, .3f, .2f}) {
                    press(mask);
                    for (int repeat = 0; repeat < 3; repeat++) {
                        tick(input, factor != 1, factor);
                        float f = (((mask & 1) != 0 ? 1 : 0) - ((mask & 2) != 0 ? 1 : 0)) * factor;
                        float s = (((mask & 4) != 0 ? 1 : 0) - ((mask & 8) != 0 ? 1 : 0)) * factor;
                        check(input.movementForward == f * (active ? -1 : 1)
                                && input.movementSideways == s * (active ? -1 : 1), "Axes and opposing keys");
                        check(input.pressingForward == ((mask & (active ? 2 : 1)) != 0)
                                && input.pressingBack == ((mask & (active ? 1 : 2)) != 0)
                                && input.pressingLeft == ((mask & (active ? 8 : 4)) != 0)
                                && input.pressingRight == ((mask & (active ? 4 : 8)) != 0), "Boat direction flags");
                        check(input.jumping == ((mask & 16) != 0) && input.sneaking == ((mask & 32) != 0)
                                && keys[6].isPressed() == ((mask & 64) != 0), "Jump, sneak, sprint binding unchanged");
                        check(input.movementForward * input.movementForward + input.movementSideways * input.movementSideways
                                == f * f + s * s, "Pre-normalization vector length unchanged");
                    }
                }
            }
            for (var currentMode : GameMode.values()) {
                mode = currentMode; sync(ID, ID, "no_sprint");
                forward(input, mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
            }
            mode = GameMode.SURVIVAL; alive = false; forward(input, false); alive = true;
            // Unsafe bypasses Entity's passenger/change-listener initialization. Set only the removal gate field.
            set(net.minecraft.entity.Entity.class, client.player, "removalReason", net.minecraft.entity.Entity.RemovalReason.KILLED);
            forward(input, false);
            client.player = (Player) unsafe.allocateInstance(Player.class); client.player.input = input; forward(input, true);
            var other = new KeyboardInput(options); forward(other, false); forward(input, true);
            var world = client.world; client.world = null; forward(input, false); client.world = world;
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class); forward(input, true);
            var manager = client.interactionManager; client.interactionManager = null; forward(input, false); client.interactionManager = manager;
            var player = client.player; client.player = null; forward(input, false); client.player = player;
            ClientDataHolder.reset(); forward(input, false); sync("no_sprint"); forward(input, false); sync(ID); forward(input, true);
            for (int i = 0; i < 6; i++) {
                riding = i == 0; water = i == 1; lava = i == 2; climbing = i == 3; elytra = i == 4; ABILITIES.flying = i == 5;
                forward(input, true);
            }
            riding = water = lava = climbing = elytra = ABILITIES.flying = false;
            for (int cycle = 0; cycle < 32; cycle++) {
                sync(ID, ID); forward(input, true); sync("no_sprint"); forward(input, false);
                ClientDataHolder.reset(); forward(input, false);
            }
            // Rebinding changes only user bindings, and never gets undone by stop/reset.
            for (int code : new int[]{265, 266}) {
                keys[0].setBoundKey(InputUtil.Type.KEYSYM.createFromCode(code)); KeyBinding.updateKeysByCode();
                KeyBinding.unpressAll(); KeyBinding.setKeyPressed(InputUtil.Type.KEYSYM.createFromCode(code), true);
                sync(ID); tick(input, false, .3f); check(input.movementForward == -1, "Current rebound key inverted");
                sync("no_sprint"); tick(input, false, .3f); check(input.movementForward == 1, "Stop preserves user binding");
                ClientDataHolder.reset(); check(keys[0].matchesKey(code, 0), "Reset preserves current binding");
            }
            // Actual production order must constrain the result of inversion, then preserve the companion after end.
            ClientDataHolder.updateState(1, 20, List.of(ID, "blocked_direction"), "", List.of(), "forward");
            press(2); tick(input, false, .3f); check(input.movementForward == 0 && !input.pressingForward, "Block applies after inversion");
            press(1); tick(input, false, .3f); check(input.movementForward == -1 && input.pressingBack, "Opposite result remains allowed");
            ClientDataHolder.updateState(1, 20, List.of("blocked_direction"), "", List.of(), "forward");
            press(1); tick(input, false, .3f); check(input.movementForward == 0, "Ending inversion keeps direction block");
            // Prime real memory via its normal S2C receiver; hold sprint to avoid unrelated setSprinting on Unsafe player.
            sync(ID, "movement_memory"); MovementMemoryInput.receive(new MovementMemoryPayload(71, -1, false));
            press(1 | 64); tick(input, false, .3f); check(input.movementForward == -1, "Memory sees inverted forward");
            press(64); tick(input, true, .3f); check(input.movementForward == -.3f && input.pressingBack, "Memory repeats inverted direction after release");
            sync("movement_memory"); press(1 | 64); tick(input, false, .3f);
            press(64); tick(input, false, .3f); check(input.movementForward == 1, "Ending inversion preserves active memory");
            sync(ID); press(64); tick(input, false, .3f); check(input.movementForward == 0, "Ending memory retains no stale movement");
            check(!InvertedKeyboardInput.shouldInvert(null, input), "Null client rejected");
            System.out.println("INVERTED_KEYBOARD_REVIEW_CLIENT_PASSED checks=" + checks + " ticks=" + ticks
                    + " real gate; synthetic client/player/world; no window/network/travel");
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
