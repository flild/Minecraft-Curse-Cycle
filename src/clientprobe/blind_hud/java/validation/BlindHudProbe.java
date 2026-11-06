package validation;

import com.cursecycle.client.BlindHud;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.HeartbeatInput;
import com.cursecycle.client.gui.HeartbeatOverlay;
import com.cursecycle.network.HeartbeatStatePayload;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
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
    private static int width = 640, height = 360, renderCalls;
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
        @Override public float getAttackCooldownProgress(float delta) { return .5f; }
        @Override public boolean hasReducedDebugInfo() { return false; }
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
    public static class Debug extends DebugHud {
        public Debug() { super(null); }
        @Override public boolean shouldShowDebugHud() { return false; }
    }
    public static class Context extends DrawContext {
        final List<String> draws = new ArrayList<>();
        final List<org.joml.Vector3f> points = new ArrayList<>();
        Context() { super(client, null); }
        @Override public int getScaledWindowWidth() { return width; }
        @Override public int getScaledWindowHeight() { return height; }
        private void point(int x, int y) {
            points.add(getMatrices().peek().getPositionMatrix().transformPosition(new org.joml.Vector3f(x, y, 0)));
        }
        @Override public void drawGuiTexture(Identifier id, int x, int y, int w, int h) {
            point(x, y);
            draws.add(id + ":" + x + ":" + y + ":" + w + ":" + h);
        }
        @Override public void drawGuiTexture(Identifier id, int tw, int th, int u, int v, int x, int y, int w, int h) {
            point(x, y);
            draws.add(id + ":" + x + ":" + y + ":" + w + ":" + h);
        }
        @Override public int drawText(TextRenderer font, String text, int x, int y, int color, boolean shadow) {
            draws.add("text:" + text + ":" + x + ":" + y + ":" + color);
            return 0;
        }
        @Override public void fill(int x1, int y1, int x2, int y2, int color) {
            draws.add("fill:" + x1 + ":" + y1 + ":" + x2 + ":" + y2 + ":" + color);
        }
        @Override public void drawCenteredTextWithShadow(TextRenderer font, Text text, int x, int y, int color) {
            draws.add("label:" + text + ":" + x + ":" + y);
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
        renderCalls++;
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
    private static Context crosshair() throws Exception {
        var context = new Context();
        context.getMatrices().translate(3, -4, 2);
        var before = new org.joml.Matrix4f(context.getMatrices().peek().getPositionMatrix());
        var method = InGameHud.class.getDeclaredMethod("renderCrosshair", DrawContext.class, RenderTickCounter.class);
        method.setAccessible(true);
        method.invoke(hud, context, RenderTickCounter.ZERO);
        renderCalls++;
        check(before.equals(context.getMatrices().peek().getPositionMatrix()) && context.getMatrices().isEmpty(),
                "Crosshair restores other HUD matrix and stack");
        return context;
    }
    private static void pairBoundariesAndRepeatedSync(GameOptions options) throws Exception {
        set(options, GameOptions.class, "perspective", Perspective.FIRST_PERSON);
        set(options, GameOptions.class, "reducedDebugInfo", SimpleOption.ofBoolean("probe", false));
        set(options, GameOptions.class, "attackIndicator", new SimpleOption<>("probe", SimpleOption.emptyTooltip(),
                (text, value) -> text, new SimpleOption.PotentialValuesBasedCallbacks<>(List.of(AttackIndicator.values()),
                com.mojang.serialization.Codec.INT.xmap(i -> AttackIndicator.values()[i], Enum::ordinal)),
                AttackIndicator.CROSSHAIR, value -> {}));
        set(hud, InGameHud.class, "debugHud", unsafe.allocateInstance(Debug.class));
        riding = false;
        for (int[] size : new int[][]{{640,360}, {101,73}, {320,180}, {15,15}}) {
            width = size[0]; height = size[1];
            sync("no_sprint");
            var baseline = crosshair();
            check(baseline.draws.size() == 3, "Positive vanilla crosshair and partial attack indicator");
            for (var ids : List.of(List.of(ID), List.of(ID,"offset_crosshair"), List.of("offset_crosshair"))) {
                sync(ids.toArray(String[]::new));
                var actual = crosshair();
                check(actual.draws.equals(baseline.draws), "Blind HUD preserves crosshair/attack textures, both pair endings");
                int dx = ids.contains("offset_crosshair") && width >= 16 ? Math.clamp(width/2+80,8,width-8)-width/2 : 0;
                int dy = ids.contains("offset_crosshair") && height >= 16 ? Math.clamp(height/2+40,8,height-8)-height/2 : 0;
                for (int i=0; i<actual.points.size(); i++)
                    check(new org.joml.Vector3f(baseline.points.get(i)).add(dx,dy,0).equals(actual.points.get(i)),
                            "Independent screen offset remains exact with hidden resources");
                check(resources().isEmpty() == ids.contains(ID), "Both pair endings preserve the remaining mechanic");
            }
        }
        width = 640; height = 360;
        set(null, MinecraftClient.class, "instance", client);
        HeartbeatInput.key = new KeyBinding("probe.heartbeat", InputUtil.Type.KEYSYM, 66, "probe");
        for (boolean hidden : new boolean[]{false,true}) {
            options.hudHidden = hidden;
            for (long age : new long[]{10,105,110}) {
                var sample = new HeartbeatStatePayload(1,1,age,110,age==110);
                sync("heartbeat");
                HeartbeatInput.receive(client,sample);
                var baseline = new Context();
                HeartbeatOverlay.render(baseline);
                check(hidden ? baseline.draws.isEmpty() : baseline.draws.size()==5, "Positive rhythm/F1 control");
                sync(ID,"heartbeat");
                var actual = new Context();
                HeartbeatOverlay.render(actual);
                check(actual.draws.equals(baseline.draws) && actual.getMatrices().isEmpty(), "Rhythm survives blind HUD and restores stack");
                check(resources().isEmpty(), "Rhythm feedback does not reveal personal resources");
                sync(ID);
                var ended = new Context();
                HeartbeatOverlay.render(ended);
                check(ended.draws.isEmpty() && resources().isEmpty(), "Ending heartbeat leaves resource filter");
                sync("heartbeat");
                HeartbeatInput.receive(client,sample);
                var resumed = new Context();
                HeartbeatOverlay.render(resumed);
                check(resumed.draws.equals(baseline.draws) && !resources().isEmpty(), "Ending blind HUD retains heartbeat");
            }
        }
        for (int cycle=0;cycle<32;cycle++) {
            options.hudHidden = cycle%2==0;
            ClientDataHolder.reset();
            check(!resources().isEmpty(), "Reset removes stale filter");
            for (int repeat=0;repeat<50;repeat++) {
                sync(ID,ID,"no_sprint");
                check(resources().isEmpty(), "Duplicate sync is idempotent");
            }
            sync("no_sprint");
            check(!resources().isEmpty() && options.hudHidden==(cycle%2==0), "End keeps latest resources and F1");
        }
        options.hudHidden = false;
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
        int predicates = 0;
        for (var method : node.methods) {
            boolean readsHidden = false;
            for (var instruction : method.instructions)
                if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field && field.name.equals("hudHidden"))
                    readsHidden = true;
            if (!readsHidden || !method.desc.equals("(Lnet/minecraft/client/MinecraftClient;)Z")) continue;
            var predicate = InGameHud.class.getDeclaredMethod(method.name, MinecraftClient.class);
            predicate.setAccessible(true);
            for (boolean hidden : new boolean[]{false,true}) for (String id : List.of(ID,"no_sprint")) {
                client.options.hudHidden = hidden;
                sync(id);
                check((boolean)predicate.invoke(null, client) == !hidden, "Actual vanilla layer F1 predicate remains independent");
            }
            predicates++;
        }
        check(predicates == 2, "Both vanilla layer F1 predicates found");
        client.options.hudHidden = false;
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
            pairBoundariesAndRepeatedSync(options);
            System.out.println("BLIND_HUD_CLIENT_PROBE_PASSED checks=" + checks + " renderCalls=" + renderCalls);
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
}
