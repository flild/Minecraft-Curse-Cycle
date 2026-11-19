package validation;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.MovementMemoryInput;
import com.cursecycle.client.StuckThrottleInput;
import com.cursecycle.network.MovementMemoryPayload;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import sun.misc.Unsafe;

import java.util.List;

/** Real production KeyboardInput + sprint Mixins and vanilla yaw rotation; synthetic client/accessors. */
public final class StuckThrottleProbe implements PreLaunchEntrypoint {
    private static final String ID = "stuck_throttle";
    private static boolean alive = true, riding, water, lava, climbing, elytra, sprinting;
    private static GameMode mode = GameMode.SURVIVAL;
    private static final PlayerAbilities ABILITIES = new PlayerAbilities();
    private static final HungerManager HUNGER = new HungerManager();
    private static Vec3d velocity = Vec3d.ZERO;
    private static MinecraftClient client;
    private static KeyBinding[] keys;
    private static int checks, ticks;

    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean hasVehicle() { return riding; }
        @Override public boolean isTouchingWater() { return water; }
        @Override public boolean isSubmergedInWater() { return water; }
        @Override public boolean isInLava() { return lava; }
        @Override public boolean isClimbing() { return climbing; }
        @Override public boolean isFallFlying() { return elytra; }
        @Override public PlayerAbilities getAbilities() { return ABILITIES; }
        @Override public HungerManager getHungerManager() { return HUNGER; }
        @Override public boolean isUsingItem() { return false; }
        @Override public boolean hasStatusEffect(RegistryEntry<StatusEffect> effect) { return false; }
        @Override public void setSprinting(boolean value) { sprinting = value; }
        @Override public boolean isSprinting() { return sprinting; }
        @Override public Vec3d getVelocity() { return velocity; }
        @Override public void setVelocity(Vec3d value) { velocity = value; }
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
    private static void sync(String... ids) {
        ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of(), "forward");
    }
    private static void tick(KeyboardInput input, int mask, float factor) {
        for (int i = 0; i < 7; i++) keys[i].setPressed((mask & (1 << i)) != 0);
        input.tick(factor != 1, factor); ticks++;
    }
    private static void idle(KeyboardInput input, boolean forced) {
        tick(input, 0, 1);
        check(input.movementForward == (forced ? 1 : 0) && input.pressingForward == forced && !input.pressingBack,
                "Current eligibility, with no keys");
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
            String[] names = {"forwardKey", "backKey", "leftKey", "rightKey", "jumpKey", "sneakKey", "sprintKey", "attackKey", "useKey"};
            keys = new KeyBinding[names.length];
            for (int i = 0; i < names.length; i++) {
                keys[i] = new KeyBinding("probe." + names[i], 300 + i, KeyBinding.MOVEMENT_CATEGORY);
                set(GameOptions.class, options, names[i], keys[i]);
            }
            var input = new KeyboardInput(options); client.player.input = input;
            check(java.util.Arrays.stream(KeyboardInput.class.getDeclaredMethods())
                    .anyMatch(m -> m.getName().contains("cursecycle$invertKeyboard")), "Production KeyboardInput Mixin");
            check(java.util.Arrays.stream(ClientPlayerEntity.class.getDeclaredMethods())
                    .anyMatch(m -> m.getName().contains("cursecycle$requireExplicitSprint")), "Production sprint Mixin");
            var canSprint = ClientPlayerEntity.class.getDeclaredMethod("canStartSprinting"); canSprint.setAccessible(true);
            for (boolean inverted : new boolean[]{false, true})
                for (String blocked : new String[]{"", "forward", "left", "right"})
                    for (int mask = 0; mask < 128; mask++) for (float factor : new float[]{1, .3f, .2f}) {
                        ClientDataHolder.updateState(1, 20, inverted ? List.of(ID, "inverted_keyboard", "blocked_direction")
                                : List.of(ID, "blocked_direction"), "", List.of(), blocked);
                        keys[7].setPressed((mask & 1) != 0); keys[8].setPressed((mask & 2) != 0);
                        for (int repeat = 0; repeat < 3; repeat++) {
                            tick(input, mask, factor);
                            float sideways = (((mask & 4) != 0 ? 1 : 0) - ((mask & 8) != 0 ? 1 : 0)) * factor;
                            if (inverted) sideways = -sideways;
                            if (blocked.equals("left")) sideways = Math.min(0, sideways);
                            if (blocked.equals("right")) sideways = Math.max(0, sideways);
                            check(input.movementForward == factor && input.pressingForward && !input.pressingBack,
                                    "Release, back, opposing keys, inversion and blocked forward cannot stop throttle");
                            check(input.movementSideways == sideways, "Lateral input still respects inversion and block");
                            check(input.jumping == ((mask & 16) != 0) && input.sneaking == ((mask & 32) != 0), "Jump and sneak available");
                            for (int i = 0; i < 7; i++) check(keys[i].isPressed() == ((mask & (1 << i)) != 0), "No KeyBinding mutation");
                            check(keys[7].isPressed() == ((mask & 1) != 0) && keys[8].isPressed() == ((mask & 2) != 0), "Attack/use untouched");
                        }
                    }
            sync(ID); idle(input, true);
            check(!(boolean) canSprint.invoke(client.player), "Synthetic edge cannot start double-tap sprint");
            tick(input, 64, 1); check((boolean) canSprint.invoke(client.player), "Explicit sprint retains vanilla permission");
            sync("test_easy"); tick(input, 1, 1); check((boolean) canSprint.invoke(client.player), "No sprint change outside challenge");
            sync(ID); idle(input, true);
            for (int yaw : new int[]{0, 90, 180, 270}) {
                client.player.setYaw(yaw); velocity = Vec3d.ZERO;
                client.player.updateVelocity(1, new Vec3d(input.movementSideways, 0, input.movementForward));
                double angle = Math.toRadians(yaw);
                check(Math.abs(velocity.x + Math.sin(angle)) < 1e-5 && Math.abs(velocity.z - Math.cos(angle)) < 1e-5,
                        "Real vanilla updateVelocity follows changed yaw");
            }
            for (var currentMode : GameMode.values()) {
                mode = currentMode; idle(input, mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
            }
            mode = GameMode.SURVIVAL; alive = false; idle(input, false); alive = true;
            set(net.minecraft.entity.Entity.class, client.player, "removalReason", net.minecraft.entity.Entity.RemovalReason.KILLED);
            idle(input, false);
            client.player = (Player) unsafe.allocateInstance(Player.class); client.player.input = input; idle(input, true);
            var other = new KeyboardInput(options); idle(other, false);
            var world = client.world; client.world = null; idle(input, false); client.world = world;
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class); idle(input, true);
            var manager = client.interactionManager; client.interactionManager = null; idle(input, false); client.interactionManager = manager;
            var player = client.player; client.player = null; idle(input, false); client.player = player;
            ClientDataHolder.reset(); idle(input, false); sync("test_easy"); idle(input, false); sync(ID); idle(input, true);
            for (int i = 0; i < 6; i++) {
                riding = i == 0; water = i == 1; lava = i == 2; climbing = i == 3; elytra = i == 4; ABILITIES.flying = i == 5;
                idle(input, true);
            }
            riding = water = lava = climbing = elytra = ABILITIES.flying = false;
            for (var screen : new net.minecraft.client.gui.screen.Screen[]{
                    (ChatScreen) unsafe.allocateInstance(ChatScreen.class), (InventoryScreen) unsafe.allocateInstance(InventoryScreen.class)}) {
                client.currentScreen = screen; KeyBinding.unpressAll(); idle(input, true);
                sync("test_easy"); idle(input, false); sync(ID);
            }
            client.currentScreen = null;
            for (boolean inverted : new boolean[]{false, true}) {
                ClientDataHolder.reset();
                ClientDataHolder.updateState(1, 20, inverted ? List.of(ID, "inverted_keyboard", "movement_memory", "blocked_direction")
                        : List.of(ID, "movement_memory", "blocked_direction"), "", List.of(), "forward");
                MovementMemoryInput.receive(new MovementMemoryPayload(71, -1, false));
                tick(input, (inverted ? 1 : 2) | 64, 1); tick(input, 64, 1);
                check(input.movementForward == 1 && !input.pressingBack, "Remembered backwards loses to forced forward");
                MovementMemoryInput.receive(new MovementMemoryPayload(72, -1, false));
                tick(input, 64, 1);
                var memoryField = MovementMemoryInput.class.getDeclaredField("MEMORY"); memoryField.setAccessible(true);
                check(!((MovementMemoryInput.Memory) memoryField.get(null)).holding(), "Synthetic forward never creates a memory request");
            }
            for (int cycle = 0; cycle < 32; cycle++) {
                sync(ID, ID); idle(input, true); sync("test_easy"); idle(input, false);
                ClientDataHolder.reset(); idle(input, false);
            }
            sync(ID); tick(input, 2, 1); sync("test_easy"); tick(input, 2, 1);
            check(input.movementForward == -1 && input.pressingBack && !input.pressingForward, "Stop restores physically held back key");
            for (int code : new int[]{265, 266}) {
                keys[0].setBoundKey(InputUtil.Type.KEYSYM.createFromCode(code)); KeyBinding.updateKeysByCode();
                KeyBinding.unpressAll(); KeyBinding.setKeyPressed(InputUtil.Type.KEYSYM.createFromCode(code), true);
                sync(ID); input.tick(false, .3f); check(input.movementForward == 1, "Rebound input remains forced");
                sync("test_easy"); input.tick(false, .3f); check(input.movementForward == 1, "Stop preserves actual key assignment");
                check(keys[0].matchesKey(code, 0), "Current binding preserved");
            }
            check(!StuckThrottleInput.shouldForce(null, input), "Null context excluded");
            System.out.println("STUCK_THROTTLE_CLIENT_PASSED checks=" + checks + " ticks=" + ticks
                    + " transformed input/sprint and vanilla yaw; synthetic client/world; no renderer/network/travel");
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
