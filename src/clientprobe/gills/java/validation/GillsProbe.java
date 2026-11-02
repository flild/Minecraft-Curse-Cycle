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
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.fluid.Fluid;
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

/** Runs transformed breathing and actual vanilla air HUD; fixtures replace world/network/GL only. */
public final class GillsProbe implements PreLaunchEntrypoint {
    private static Client client;
    private static InGameHud hud;
    private static int air = 120, checks;
    private static boolean alive = true;
    private static GameMode mode = GameMode.SURVIVAL;
    private static final HungerManager hunger = new HungerManager();
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
        @Override public float getHealth() { return 20; }
        @Override public float getAbsorptionAmount() { return 0; }
        @Override public int getArmor() { return 0; }
        @Override public double getAttributeValue(RegistryEntry<EntityAttribute> type) { return 20; }
        @Override public boolean hasStatusEffect(RegistryEntry<StatusEffect> type) { return false; }
        @Override public boolean isFrozen() { return false; }
        @Override public World getWorld() { return GillsProbe.client.world; }
        @Override public HungerManager getHungerManager() { return hunger; }
        @Override public int getAir() { return air; }
        @Override public int getMaxAir() { return 300; }
        @Override public boolean isSubmergedIn(TagKey<Fluid> fluid) { return false; }
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
    private static long bubbles() throws Exception {
        var context = new Context();
        var method = InGameHud.class.getDeclaredMethod("renderStatusBars", DrawContext.class);
        method.setAccessible(true);
        method.invoke(hud, context);
        return context.draws.stream().filter(id -> id.contains("air")).count();
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
            var land = LivingEntity.class.getDeclaredMethod("getNextAirOnLand", int.class);
            var water = LivingEntity.class.getDeclaredMethod("getNextAirUnderwater", int.class);
            land.setAccessible(true);
            water.setAccessible(true);
            var node = new ClassNode();
            new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/entity/LivingEntity.class"))).accept(node, 0);
            int calls = 0;
            for (var method : node.methods) for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.name.contains("cursecycle$exclusiveBreathing")) {
                    check(List.of("getNextAirUnderwater", "getNextAirOnLand").contains(method.name), "Only breathing methods intercepted");
                    calls++;
                }
            check(calls == 2, "Both real client methods transformed once");
            for (var currentMode : List.of(GameMode.SURVIVAL, GameMode.ADVENTURE)) {
                mode = currentMode;
                sync("gills");
                for (int value : new int[]{0, 1, 100, 299, 300}) {
                    check((int) land.invoke(client.player, value) == value, "Active client freezes vanilla land recovery");
                    check((int) water.invoke(client.player, value) == value, "Active client freezes vanilla underwater loss");
                }
                air = 120;
                check(bubbles() == 4, "Vanilla renders four air bubbles on dry land at 120");
                air = 300;
                check(bubbles() == 0, "Full air on dry land has no bubbles");
                air = 120;
                sync("gills", "blind_hud");
                check(bubbles() == 0, "blind_hud hides gills bubbles");
                sync("gills");
                check(bubbles() == 4, "Removing blind_hud leaves gills bubbles");
            }
            for (var currentMode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
                mode = currentMode;
                check((int) land.invoke(client.player, 100) == 104, "Excluded modes retain vanilla breathing");
            }
            mode = GameMode.SURVIVAL;
            alive = false;
            check((int) land.invoke(client.player, 100) == 104, "Dead gate restores vanilla");
            alive = true;
            sync("no_sprint");
            check((int) land.invoke(client.player, 100) == 104, "Stop restores vanilla land recovery");
            ClientDataHolder.reset();
            check((int) land.invoke(client.player, 100) == 104, "Disconnect/unsynced restores vanilla");
            System.out.println("GILLS_CLIENT_PROBE_PASSED checks=" + checks);
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
