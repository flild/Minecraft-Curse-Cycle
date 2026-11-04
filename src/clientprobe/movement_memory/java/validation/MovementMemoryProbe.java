package validation;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.MovementMemoryInput;
import com.cursecycle.network.MovementMemoryPayload;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
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

/** Actual transformed input and eligibility. Synthetic accessors; no window, connection or travel. */
public final class MovementMemoryProbe implements PreLaunchEntrypoint {
    private static boolean alive = true, riding, water, lava, climbing, elytra, sprinting;
    private static GameMode mode = GameMode.SURVIVAL;
    private static final PlayerAbilities ABILITIES = new PlayerAbilities();
    private static MinecraftClient client;
    private static KeyBinding[] keys;
    private static int checks, ticks;
    private static long serial = 100;
    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isCreative() { return mode == GameMode.CREATIVE; }
        @Override public boolean isSpectator() { return mode == GameMode.SPECTATOR; }
        @Override public net.minecraft.world.World getWorld() { return MovementMemoryProbe.client.world; }
        @Override public boolean hasVehicle() { return riding; }
        @Override public boolean isTouchingWater() { return water; }
        @Override public boolean isInLava() { return lava; }
        @Override public boolean isClimbing() { return climbing; }
        @Override public boolean isFallFlying() { return elytra; }
        @Override public PlayerAbilities getAbilities() { return ABILITIES; }
        @Override public void setSprinting(boolean value) { sprinting = value; }
        @Override public boolean isSprinting() { return sprinting; }
    }
    public static class Manager extends ClientPlayerInteractionManager {
        public Manager() { super(null, null); }
        @Override public GameMode getCurrentGameMode() { return mode; }
    }
    private static void check(boolean condition, String why) {
        checks++; if (!condition) throw new AssertionError(why);
    }
    private static void set(Class<?> owner, Object object, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static Object get(Class<?> owner, Object object, String name) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static long sequence() throws Exception {
        return (long) get(MovementMemoryInput.Memory.class, get(MovementMemoryInput.class, null, "MEMORY"), "sequence");
    }
    private static void sync(List<String> ids, String direction) {
        ClientDataHolder.updateState(1, 20, ids, "", List.of(), direction);
        MovementMemoryInput.receive(new MovementMemoryPayload(++serial, -1, false));
    }
    private static void press(int mask) {
        for (int i = 0; i < keys.length; i++) keys[i].setPressed((mask & (1 << i)) != 0);
    }
    private static void tick(KeyboardInput input, int mask, float factor) {
        press(mask); input.tick(factor != 1, factor); ticks++;
    }
    private static void arm(KeyboardInput input) {
        sync(List.of("movement_memory"), ""); tick(input, 1, 1); tick(input, 0, 1);
        check(input.movementForward == 1, "Armed through real input/gate");
    }
    private static void zero(KeyboardInput input) {
        tick(input, 0, 1); check(input.movementForward == 0 && input.movementSideways == 0, "Cleared until new nonzero input");
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
            check(java.util.Arrays.stream(KeyboardInput.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("cursecycle$invertKeyboard")), "Actual input Mixin");
            for (boolean inverted : new boolean[]{false, true}) for (String blocked : new String[]{"", "forward", "left", "right"})
                for (int mask = 0; mask < 128; mask++) for (float initial : new float[]{1, .3f, .2f}) {
                    sync(inverted ? List.of("movement_memory", "inverted_keyboard", "blocked_direction")
                            : List.of("movement_memory", "blocked_direction"), blocked);
                    tick(input, 0, 1); check(input.movementForward == 0 && input.movementSideways == 0, "Activation does not move");
                    tick(input, mask, initial);
                    float f = ((mask & 1) != 0 ? 1 : 0) - ((mask & 2) != 0 ? 1 : 0);
                    float s = ((mask & 4) != 0 ? 1 : 0) - ((mask & 8) != 0 ? 1 : 0);
                    if (inverted) { f = -f; s = -s; }
                    float ef = blocked.equals("forward") ? Math.min(0, f) : f;
                    float es = blocked.equals("left") ? Math.min(0, s) : blocked.equals("right") ? Math.max(0, s) : s;
                    tick(input, 64, 1); long released = sequence();
                    for (int repeat = 0; repeat < 24; repeat++) {
                        float factor = repeat % 2 == 0 ? .3f : 1;
                        int fresh = 64 | (repeat % 2 == 0 ? 32 : 16);
                        tick(input, fresh, factor);
                        check(input.movementForward == ef * factor && input.movementSideways == es * factor, "Held signs, current scale, inversion then memory then block");
                        check(input.pressingForward == (ef > 0) && input.pressingBack == (ef < 0)
                                && input.pressingLeft == (es > 0) && input.pressingRight == (es < 0), "Fresh synthetic horizontal flags");
                        check(input.jumping == ((fresh & 16) != 0) && input.sneaking == ((fresh & 32) != 0), "Jump and sneak never retained");
                        check(sequence() == released, "No repeat request while holding or after zero vector");
                    }
                    MovementMemoryInput.receive(new MovementMemoryPayload(serial - 1, released, false));
                    MovementMemoryInput.receive(new MovementMemoryPayload(serial, released - 1, false));
                    tick(input, 64, 1); check(input.movementForward == ef && input.movementSideways == es, "Stale end rejected");
                    MovementMemoryInput.receive(new MovementMemoryPayload(serial, released, false)); zero(input); zero(input);
                }
            // Replacement, sprint latch, activation, and cancellation without retaining physical velocity.
            arm(input); tick(input, 2 | 16, 1); check(input.movementForward == -1 && input.jumping, "Immediate reverse");
            tick(input, 0, .2f); check(input.movementForward == -.2f, "New release owns reverse direction");
            arm(input); sprinting = true; tick(input, 0, 1); check(!sprinting, "Released sprint not held");
            sprinting = true; tick(input, 64, 1); check(sprinting, "Current sprint key preserved");
            for (var next : GameMode.values()) {
                mode = next == GameMode.SURVIVAL ? GameMode.ADVENTURE : GameMode.SURVIVAL;
                arm(input); mode = next; zero(input); mode = GameMode.SURVIVAL; zero(input);
            }
            for (int excluded = 0; excluded < 7; excluded++) {
                arm(input); water = excluded == 0; lava = excluded == 1; climbing = excluded == 2;
                riding = excluded == 3; elytra = excluded == 4; ABILITIES.flying = excluded == 5; alive = excluded != 6;
                MovementMemoryInput.validate(client); zero(input);
                water = lava = climbing = riding = elytra = ABILITIES.flying = false; alive = true; zero(input);
            }
            arm(input); client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class); zero(input);
            arm(input); client.player = (Player) unsafe.allocateInstance(Player.class); client.player.input = input; zero(input);
            arm(input); set(net.minecraft.entity.Entity.class, client.player, "removalReason", net.minecraft.entity.Entity.RemovalReason.KILLED); zero(input);
            client.player = (Player) unsafe.allocateInstance(Player.class); client.player.input = input;
            arm(input); client.player.input = new KeyboardInput(options); zero(client.player.input instanceof KeyboardInput k ? k : input);
            client.player.input = input;
            arm(input); zero(new KeyboardInput(options)); zero(input);
            arm(input); var world = client.world; client.world = null; zero(input); client.world = world; zero(input);
            arm(input); var manager = client.interactionManager; client.interactionManager = null; zero(input); client.interactionManager = manager; zero(input);
            arm(input); var player = client.player; client.player = null; zero(input); client.player = player; zero(input);
            arm(input); client.currentScreen = (net.minecraft.client.gui.screen.Screen) unsafe.allocateInstance(net.minecraft.client.gui.screen.ChatScreen.class);
            MovementMemoryInput.validate(client); zero(input); client.currentScreen = null; zero(input);
            arm(input);
            var screenHandler = java.util.Arrays.stream(MinecraftClient.class.getDeclaredMethods()).filter(m -> m.getName().contains("cursecycle$clearMovementMemory")).findFirst().orElseThrow();
            screenHandler.setAccessible(true);
            screenHandler.invoke(client, unsafe.allocateInstance(net.minecraft.client.gui.screen.ChatScreen.class),
                    new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("setScreen", false));
            zero(input); // Actual injected handler, without running the rest of window-dependent setScreen.
            for (int cycle = 0; cycle < 32; cycle++) {
                arm(input); ClientDataHolder.reset();
                check(get(MovementMemoryInput.class, null, "player") == null, "Reset releases old context");
                zero(input);
                sync(List.of("movement_memory", "inverted_keyboard"), ""); tick(input, 1, 1); tick(input, 0, 1);
                check(input.movementForward == -1, "Both mechanisms active");
                ClientDataHolder.updateState(1, 20, List.of("inverted_keyboard"), "", List.of());
                zero(input); tick(input, 1, 1); check(input.movementForward == -1, "Ending memory preserves inversion");
            }
            for (int code : new int[]{265, 266}) {
                sync(List.of("movement_memory"), ""); keys[0].setBoundKey(InputUtil.Type.KEYSYM.createFromCode(code));
                KeyBinding.updateKeysByCode(); KeyBinding.unpressAll(); KeyBinding.setKeyPressed(InputUtil.Type.KEYSYM.createFromCode(code), true);
                input.tick(false, .3f); ticks++; check(input.movementForward == 1, "Current rebound key");
                KeyBinding.setKeyPressed(InputUtil.Type.KEYSYM.createFromCode(code), false); input.tick(false, .3f); ticks++;
                check(input.movementForward == 1, "Memory after real rebound release");
                ClientDataHolder.reset(); check(keys[0].matchesKey(code, 0), "Never rewrites binding"); zero(input);
            }
            sync(List.of("movement_memory", "blocked_direction"), "forward");
            tick(input, 1, 1); tick(input, 0, 1); check(input.movementForward == 0, "Block applies after memory");
            ClientDataHolder.updateState(1, 20, List.of("movement_memory"), "", List.of());
            tick(input, 0, 1); check(input.movementForward == 1, "Ending block retains remembered pre-block input");
            ClientDataHolder.updateState(1, 20, List.of("blocked_direction"), "", List.of(), "forward");
            zero(input); tick(input, 1, 1); check(input.movementForward == 0, "Ending memory retains block");
            set(net.minecraft.world.World.class, client.world, "isClient", true);
            sync(List.of("movement_memory", "heavy_body"), ""); tick(input, 1, 1); tick(input, 0, 1);
            check(com.cursecycle.client.HeavyBodyInput.moving(client), "Heavy body observes synthetic final movement");
            MovementMemoryInput.receive(new MovementMemoryPayload(serial, sequence(), false)); zero(input);
            check(!com.cursecycle.client.HeavyBodyInput.moving(client), "End of memory also ends final movement intent");
            check(!MovementMemoryInput.shouldRemember(null, input), "Null client rejected");
            System.out.println("MOVEMENT_MEMORY_REVIEW_CLIENT_PASSED checks=" + checks + " ticks=" + ticks
                    + " real gates and Mixin; synthetic context; no window/network/travel");
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
