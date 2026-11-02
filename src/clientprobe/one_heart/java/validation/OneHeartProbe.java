package validation;

import com.cursecycle.client.ClientDataHolder;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.profiler.DummyProfiler;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import sun.misc.Unsafe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Transformed getters and real HUD calls; world/network/GL are fixtures, no real client session. */
public final class OneHeartProbe implements PreLaunchEntrypoint {
    private static Client client;
    private static InGameHud hud;
    private static int checks;
    private static boolean alive = true;
    private static double externalMaximum = 48;
    private static float health = 2;
    private static float absorption;
    private static GameMode mode = GameMode.SURVIVAL;
    private static final HungerManager hunger = new HungerManager();
    private static final PlayerAbilities abilities = new PlayerAbilities();
    public static class Client extends MinecraftClient {
        public Client(RunArgs args) { super(args); }
        @Override public Entity getCameraEntity() { return player; }
        @Override public Profiler getProfiler() { return DummyProfiler.INSTANCE; }
    }
    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isCreative() { return mode == GameMode.CREATIVE; }
        @Override public boolean isSpectator() { return mode == GameMode.SPECTATOR; }
        @Override public PlayerAbilities getAbilities() { return abilities; }
        @Override public float getHealth() { return health; }
        @Override public float getAbsorptionAmount() { return absorption; }
        @Override public int getArmor() { return 0; }
        @Override public double getAttributeValue(RegistryEntry<EntityAttribute> type) { return externalMaximum; }
        @Override public boolean hasStatusEffect(RegistryEntry<StatusEffect> type) { return false; }
        @Override public boolean isFrozen() { return false; }
        @Override public World getWorld() { return OneHeartProbe.client.world; }
        @Override public HungerManager getHungerManager() { return hunger; }
        @Override public int getAir() { return 300; }
        @Override public int getMaxAir() { return 300; }
        @Override public boolean isSubmergedIn(net.minecraft.registry.tag.TagKey<net.minecraft.fluid.Fluid> fluid) { return false; }
        @Override public Entity getVehicle() { return null; }
    }
    public static class Manager extends ClientPlayerInteractionManager {
        public Manager() { super(null, null); }
        @Override public GameMode getCurrentGameMode() { return mode; }
    }
    public static class TestWorld extends ClientWorld {
        public TestWorld() { super(null, null, null, null, 0, 0, null, null, false, 0); }
        @Override public Properties getLevelProperties() { return new Properties(Difficulty.NORMAL, false, false); }
    }
    public static class Context extends DrawContext {
        final List<String> draws = new ArrayList<>();
        Context() { super(client, null); }
        @Override public int getScaledWindowWidth() { return 640; }
        @Override public int getScaledWindowHeight() { return 360; }
        @Override public void drawGuiTexture(Identifier id, int x, int y, int w, int h) { draws.add(id.toString()); }
        @Override public void drawGuiTexture(Identifier id, int tw, int th, int u, int v, int x, int y, int w, int h) { draws.add(id.toString()); }
    }
    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void sync(String... ids) { ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of()); }
    private static long hearts(String texture) throws Exception {
        var context = new Context();
        var method = InGameHud.class.getDeclaredMethod("renderStatusBars", DrawContext.class);
        method.setAccessible(true);
        method.invoke(hud, context);
        return context.draws.stream().filter(id -> id.equals("minecraft:hud/heart/" + texture)).count();
    }
    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion();
            net.minecraft.Bootstrap.initialize();
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var unsafe = (Unsafe) field.get(null);
            client = (Client) unsafe.allocateInstance(Client.class);
            client.player = (Player) unsafe.allocateInstance(Player.class);
            client.world = (TestWorld) unsafe.allocateInstance(TestWorld.class);
            set(client.world, World.class, "isClient", true);
            client.interactionManager = (Manager) unsafe.allocateInstance(Manager.class);
            set(client, MinecraftClient.class, "options", unsafe.allocateInstance(GameOptions.class));
            hud = (InGameHud) unsafe.allocateInstance(InGameHud.class);
            set(hud, InGameHud.class, "client", client);
            set(hud, InGameHud.class, "random", Random.create(1));
            // Load/transform before inspecting exported bytecode. Neither getter is overridden in Player.
            check(client.player.getMaxHealth() == 48, "Unsynced getter is vanilla");
            var node = new ClassNode();
            new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/entity/LivingEntity.class"))).accept(node, 0);
            int calls = 0;
            for (var method : node.methods) for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && (call.name.contains("cursecycle$maxHealth") || call.name.contains("cursecycle$maxAbsorption"))) {
                    check(List.of("getMaxHealth", "getMaxAbsorption").contains(method.name), "Only final getters intercepted");
                    calls++;
                }
            check(calls == 2, "Both client getter injections transformed once");
            InGameHud.class.getDeclaredMethod("renderStatusBars", DrawContext.class);
            var hudNode = new ClassNode();
            new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/client/gui/hud/InGameHud.class"))).accept(hudNode, 0);
            int hudCalls = 0;
            for (var method : hudNode.methods) for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && (call.name.contains("cursecycle$visibleMaximum") || call.name.contains("cursecycle$visibleAbsorption"))) {
                    check(method.name.equals("renderStatusBars"), "HUD corrections stay within status bars");
                    hudCalls++;
                }
            check(hudCalls == 2, "Both real HUD expressions transformed once");
            for (var currentMode : List.of(GameMode.SURVIVAL, GameMode.ADVENTURE)) {
                mode = currentMode;
                sync("one_heart");
                for (double maximum : new double[]{1, 2, 20, 48, 100}) {
                    externalMaximum = maximum;
                    check(client.player.getMaxHealth() == Math.min(maximum, 2), "Actual client health cap");
                    check(client.player.getMaxAbsorption() == 0, "Actual client absorption cap");
                }
                externalMaximum = 48;
                health = 2;
                check(hearts("container") == 1 && hearts("full") == 1, "Actual HUD draws exactly one heart at full HP");
                health = 1;
                check(hearts("half") == 1, "Actual HUD draws half heart at one HP");
                health = 20;
                absorption = 16;
                set(hud, InGameHud.class, "renderHealthValue", 20);
                check(hearts("container") == 1 && hearts("absorbing_full") == 0,
                        "Stale health animation and absorption packets cannot render extra hearts");
                health = 2;
                absorption = 0;
                set(hud, InGameHud.class, "renderHealthValue", 2);
                sync("one_heart", "no_healing");
                check(client.player.getMaxHealth() == 2, "Double ID preserves client cap");
            }
            for (var currentMode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
                mode = currentMode;
                check(client.player.getMaxHealth() == 48, "Excluded modes release cap");
                check(client.player.getMaxAbsorption() == 48, "Excluded modes release absorption cap");
            }
            mode = GameMode.SURVIVAL;
            sync("one_heart", "blind_hud");
            check(hearts("container") == 0, "Existing blind_hud still hides hearts");
            sync("one_heart");
            check(hearts("container") == 1, "Removing blind_hud keeps one-heart cap");
            alive = false;
            check(client.player.getMaxHealth() == 48, "Dead gate is vanilla");
            alive = true;
            sync("no_sprint");
            check(client.player.getMaxHealth() == 48 && client.player.getMaxAbsorption() == 48, "Stop restores current external maximum");
            check(hearts("container") == 24, "Actual HUD uses released external maximum");
            sync("one_heart");
            ClientDataHolder.reset();
            check(client.player.getMaxHealth() == 48, "Disconnect clears client restriction");
            System.out.println("ONE_HEART_CLIENT_PROBE_PASSED checks=" + checks);
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
