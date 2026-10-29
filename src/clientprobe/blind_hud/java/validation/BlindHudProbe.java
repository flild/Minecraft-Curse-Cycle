package validation;

import com.cursecycle.client.BlindHud;
import com.cursecycle.client.ClientDataHolder;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.JumpingMount;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.HorseEntity;
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

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Executes the three transformed resource methods, capturing actual vanilla texture/text output. */
public final class BlindHudProbe implements PreLaunchEntrypoint {
    private static final String ID = "blind_hud";
    private static GameMode mode = GameMode.SURVIVAL;
    private static boolean alive = true, frozen, hardcore;
    private static float health = 13, absorption = 5;
    private static int armor = 13, air = 120, checks;
    private static RegistryEntry<StatusEffect> effect;
    private static Client client;
    private static Horse mount;
    private static boolean riding;
    private static HungerManager hunger = new HungerManager();
    private static InGameHud hud;
    private static Unsafe unsafe;

    public static class Client extends MinecraftClient {
        public Client(RunArgs args) { super(args); }
        @Override public Entity getCameraEntity() { return player; }
        @Override public Profiler getProfiler() { return DummyProfiler.INSTANCE; }
    }
    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
        @Override public float getHealth() { return health; }
        @Override public float getAbsorptionAmount() { return absorption; }
        @Override public int getArmor() { return armor; }
        @Override public double getAttributeValue(RegistryEntry<EntityAttribute> type) { return 20; }
        @Override public boolean hasStatusEffect(RegistryEntry<StatusEffect> type) { return type == effect; }
        @Override public boolean isFrozen() { return frozen; }
        @Override public World getWorld() { return BlindHudProbe.client.world; }
        @Override public HungerManager getHungerManager() { return hunger; }
        @Override public int getAir() { return air; }
        @Override public int getMaxAir() { return 300; }
        @Override public boolean isSubmergedIn(TagKey<Fluid> fluid) { return air < 300; }
        @Override public Entity getVehicle() { return riding ? mount : null; }
        @Override public float getMountJumpStrength() { return .6f; }
    }
    public static class Horse extends HorseEntity {
        public Horse() { super(null, null); }
        @Override public float getHealth() { return 17; }
        @Override public double getAttributeValue(RegistryEntry<EntityAttribute> type) { return 30; }
        @Override public boolean isLiving() { return true; }
        @Override public int getJumpCooldown() { return 0; }
    }
    public static class Manager extends ClientPlayerInteractionManager {
        public Manager() { super(null, null); }
        @Override public GameMode getCurrentGameMode() { return mode; }
        @Override public boolean hasExperienceBar() { return mode.isSurvivalLike(); }
    }
    public static class TestWorld extends ClientWorld {
        public TestWorld() { super(null, null, null, null, 0, 0, null, null, false, 0); }
        @Override public Properties getLevelProperties() { return new Properties(Difficulty.NORMAL, hardcore, false); }
    }
    public static class Font extends TextRenderer {
        public Font() { super(null, false); }
        @Override public int getWidth(String text) { return text.length() * 6; }
    }
    public static class Context extends DrawContext {
        final List<String> draws = new ArrayList<>();
        Context() { super(client, null); }
        @Override public int getScaledWindowWidth() { return 640; }
        @Override public int getScaledWindowHeight() { return 360; }
        @Override public void drawGuiTexture(Identifier id, int x, int y, int w, int h) {
            draws.add(id + ":" + x + ":" + y + ":" + w + ":" + h);
        }
        @Override public void drawGuiTexture(Identifier id, int tw, int th, int u, int v, int x, int y, int w, int h) {
            draws.add(id + ":" + x + ":" + y + ":" + w + ":" + h);
        }
        @Override public int drawText(TextRenderer font, String text, int x, int y, int color, boolean shadow) {
            draws.add("text:" + text + ":" + x + ":" + y + ":" + color);
            return 0;
        }
    }
    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }
    private static void sync(String... ids) { ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of()); }
    private static List<String> draw(String name) throws Exception {
        var context = new Context();
        Method method;
        switch (name) {
            case "renderStatusBars", "renderMountHealth" -> {
                method = InGameHud.class.getDeclaredMethod(name, DrawContext.class);
                method.setAccessible(true);
                method.invoke(hud, context);
            }
            case "renderExperienceBar" -> {
                method = InGameHud.class.getDeclaredMethod(name, DrawContext.class, int.class);
                method.setAccessible(true);
                method.invoke(hud, context, 229);
            }
            case "renderMountJumpBar" -> {
                method = InGameHud.class.getDeclaredMethod(name, JumpingMount.class, DrawContext.class, int.class);
                method.setAccessible(true);
                method.invoke(hud, mount, context, 229);
            }
            default -> {
                method = InGameHud.class.getDeclaredMethod(name, DrawContext.class, RenderTickCounter.class);
                method.setAccessible(true);
                method.invoke(hud, context, RenderTickCounter.ZERO);
            }
        }
        return context.draws;
    }
    private static List<String> resources() throws Exception {
        var result = new ArrayList<String>();
        for (String name : List.of("renderStatusBars", "renderExperienceBar", "renderExperienceLevel")) result.addAll(draw(name));
        return result;
    }
    private static void audit() throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/client/gui/hud/InGameHud.class"))).accept(node, 0);
        int calls = 0;
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.contains("cursecycle$hidePersonalResources")) {
                check(List.of("renderStatusBars", "renderExperienceBar", "renderExperienceLevel").contains(method.name),
                        "Only the specified resource paths have the production filter");
                calls++;
            }
        }
        check(calls == 3, "All three render paths transformed exactly once");
    }
    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion();
            net.minecraft.Bootstrap.initialize();
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
            client = (Client) unsafe.allocateInstance(Client.class);
            client.player = (Player) unsafe.allocateInstance(Player.class);
            client.world = (TestWorld) unsafe.allocateInstance(TestWorld.class);
            client.interactionManager = (Manager) unsafe.allocateInstance(Manager.class);
            var options = (GameOptions) unsafe.allocateInstance(GameOptions.class);
            set(client, MinecraftClient.class, "options", options);
            set(client, MinecraftClient.class, "textRenderer", unsafe.allocateInstance(Font.class));
            mount = (Horse) unsafe.allocateInstance(Horse.class);
            hud = (InGameHud) unsafe.allocateInstance(InGameHud.class);
            set(hud, InGameHud.class, "client", client);
            set(hud, InGameHud.class, "random", Random.create(1));
            hunger.setFoodLevel(11);
            client.player.experienceLevel = 17;
            client.player.experienceProgress = .65f;
            audit();
            for (var currentMode : GameMode.values()) {
                mode = currentMode;
                for (var status : java.util.Arrays.<RegistryEntry<StatusEffect>>asList(null, StatusEffects.POISON,
                        StatusEffects.WITHER, StatusEffects.REGENERATION, StatusEffects.HUNGER)) {
                    effect = status;
                    for (boolean ice : new boolean[]{false, true}) {
                        frozen = ice;
                        for (boolean hard : new boolean[]{false, true}) {
                            hardcore = hard;
                            sync("no_sprint");
                            var baseline = resources();
                            check(!baseline.isEmpty(), "Baseline resource draws exist");
                            sync(ID, ID, "gills", "one_heart", "crafting_tax", "offset_crosshair");
                            var actual = resources();
                            if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                                check(actual.isEmpty(), "Every resource texture/text suppressed, including variants/absorption/air");
                            else check(actual.equals(baseline), "Excluded modes retain original output");
                            check(client.player.experienceLevel == 17 && client.player.experienceProgress == .65f
                                    && health == 13 && absorption == 5 && armor == 13 && air == 120
                                    && hunger.getFoodLevel() == 11 && !options.hudHidden, "Resources and F1 unchanged");
                        }
                    }
                }
            }
            mode = GameMode.SURVIVAL;
            effect = null;
            frozen = hardcore = false;
            sync("no_sprint");
            var initial = resources();
            for (String kind : List.of("heart/", "armor_", "food_", "air", "experience_bar", "text:17:"))
                check(initial.stream().anyMatch(s -> s.contains(kind)), "Positive baseline for " + kind);
            check(initial.stream().anyMatch(s -> s.contains("absorbing")), "Absorption baseline");
            sync(ID);
            check(resources().isEmpty(), "Active filter");
            health = 5; absorption = 1; armor = 3; air = 60; hunger.setFoodLevel(5);
            client.player.experienceLevel = 4;
            client.player.experienceProgress = .2f;
            check(resources().isEmpty(), "Updates remain hidden");
            sync("no_sprint");
            var current = resources();
            check(!initial.equals(current) && current.stream().anyMatch(s -> s.startsWith("text:4:")), "Stop shows current resources, not a snapshot");
            for (boolean hidden : new boolean[]{false, true}) {
                options.hudHidden = hidden;
                sync(ID);
                check(resources().isEmpty() && options.hudHidden == hidden, "F1 option never overwritten");
                sync("no_sprint");
                check(options.hudHidden == hidden, "Stopping keeps user F1");
            }
            options.hudHidden = false;
            sync(ID);
            alive = false;
            check(!BlindHud.shouldApply(client) && !resources().isEmpty(), "Death removes filter");
            alive = true;
            client.player = (Player) unsafe.allocateInstance(Player.class);
            check(BlindHud.shouldApply(client) && resources().isEmpty(), "Respawn uses new object");
            set(client.player, Entity.class, "removalReason", Entity.RemovalReason.DISCARDED);
            check(!BlindHud.shouldApply(client), "Removed player excluded");
            client.player = (Player) unsafe.allocateInstance(Player.class);
            client.world = (TestWorld) unsafe.allocateInstance(TestWorld.class);
            check(resources().isEmpty(), "New dimension keeps filter");
            var world = client.world;
            client.world = null;
            check(!BlindHud.shouldApply(client), "Menu without world");
            client.world = world;
            var player = client.player;
            client.player = null;
            check(!BlindHud.shouldApply(client), "Missing player");
            client.player = player;
            var manager = client.interactionManager;
            client.interactionManager = null;
            check(!BlindHud.shouldApply(client), "Missing manager");
            client.interactionManager = manager;
            ClientDataHolder.reset();
            check(!BlindHud.shouldApply(client) && !resources().isEmpty(), "Disconnect/sync loss removes filter");
            sync("no_sprint");
            check(!BlindHud.shouldApply(client), "Ending offline stays ended");
            riding = true;
            sync("no_sprint");
            var mountHealth = draw("renderMountHealth");
            var mountJump = draw("renderMountJumpBar");
            check(!mountHealth.isEmpty() && !mountJump.isEmpty(), "Positive transport baseline");
            sync(ID);
            check(mountHealth.equals(draw("renderMountHealth")) && mountJump.equals(draw("renderMountJumpBar")), "Transport draws unchanged");
            check(resources().isEmpty(), "Own resources still hidden on transport");
            System.out.println("BLIND_HUD_CLIENT_PROBE_PASSED checks=" + checks);
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
}
