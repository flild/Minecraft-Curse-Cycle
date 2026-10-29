package validation;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.InvisibleMobs;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.joml.Quaternionf;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import sun.misc.Unsafe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Real transformed dispatcher, counting renderer entry and terminal fire/shadow/F3+B calls. */
public final class InvisibleMobsProbe implements PreLaunchEntrypoint {
    private static final String ID = "invisible_mobs";
    public static int fires, shadows, hitboxes;
    private static int renders, checks;
    private static boolean alive = true;
    private static GameMode mode = GameMode.SURVIVAL;
    private static Unsafe unsafe;
    private static Client client;
    private static EntityRenderDispatcher dispatcher;
    private static Renderer renderer;
    private static VertexConsumerProvider received;
    private static final VertexConsumerProvider NORMAL = layer -> null;
    private static final VertexConsumerProvider OUTLINE = layer -> null;

    public static class Client extends MinecraftClient {
        public Client(RunArgs args) { super(args); }
        @Override public boolean hasReducedDebugInfo() { return false; }
    }
    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
    }
    public static class Manager extends ClientPlayerInteractionManager {
        public Manager() { super(null, null); }
        @Override public GameMode getCurrentGameMode() { return mode; }
    }
    public static class Dispatcher extends EntityRenderDispatcher {
        public Dispatcher() { super(null, null, null, null, null, null, null); }
        @Override public double getSquaredDistanceToCamera(double x, double y, double z) { return 0; }
    }
    public static class Mob extends Entity {
        EntityType<?> type;
        boolean naturalInvisible, burning;
        public Mob() { super(EntityType.ZOMBIE, null); }
        @Override public EntityType<?> getType() { return type; }
        @Override public boolean isInvisible() { return naturalInvisible; }
        @Override public boolean doesRenderOnFire() { return burning; }
        @Override protected void initDataTracker(DataTracker.Builder builder) {}
        @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
        @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
    }
    public static class Renderer extends EntityRenderer<Entity> {
        public Renderer() { super(null); }
        @Override public Vec3d getPositionOffset(Entity entity, float delta) { return Vec3d.ZERO; }
        @Override public float getShadowRadius(Entity entity) { return 1; }
        @Override public Identifier getTexture(Entity entity) { return Identifier.ofVanilla("probe"); }
        @Override public void render(Entity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
            renders++;
            received = vertices;
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
    private static Mob mob(EntityType<?> type) throws Exception {
        var mob = (Mob) unsafe.allocateInstance(Mob.class);
        mob.type = type;
        mob.burning = true;
        set(mob, Entity.class, "pos", Vec3d.ZERO);
        return mob;
    }
    private static void render(Mob mob, boolean outline, boolean hidden) throws Exception {
        set(dispatcher, EntityRenderDispatcher.class, "renderers", Map.of(mob.type, renderer));
        renders = shadows = fires = hitboxes = 0;
        received = null;
        var matrices = new MatrixStack();
        var before = new org.joml.Matrix4f(matrices.peek().getPositionMatrix());
        var provider = outline ? OUTLINE : NORMAL;
        dispatcher.render(mob, 1, 2, 3, 0, .5f, matrices, provider, 15728880);
        check(renders == (hidden ? 0 : 1), "Complete renderer gate: " + Registries.ENTITY_TYPE.getId(mob.type));
        check(shadows == (hidden || mob.naturalInvisible ? 0 : 1), "Shadow gate independent of fire/debug");
        check(fires == (mob.burning ? 1 : 0), "Fire preserved");
        check(hitboxes == (mob.naturalInvisible ? 0 : 1), "F3+B preserves vanilla visibility");
        check(hidden ? received == null : received == provider, "Normal and glowing providers cannot receive hidden model/features/name vertices");
        check(matrices.isEmpty() && before.equals(matrices.peek().getPositionMatrix()), "Dispatcher restores matrix stack");
    }
    private static void audit() throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/client/render/entity/EntityRenderDispatcher.class"))).accept(node, 0);
        int gates = 0;
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && (call.name.contains("cursecycle$renderMob"))) {
                check(method.name.equals("render"), "Only dispatcher.render contains production gates");
                gates++;
            }
        }
        check(gates == 2, "Exactly model and shadow call sites transformed");
        // This verifies the renderer boundary contains every required vanilla layer;
        // the runtime fixture above measures whether that entire boundary executes.
        try (var stream = InvisibleMobsProbe.class.getClassLoader().getResourceAsStream("net/minecraft/client/render/entity/LivingEntityRenderer.class")) {
            var living = new ClassNode();
            new ClassReader(stream).accept(living, 0);
            for (String owner : List.of("entity/model/EntityModel", "entity/feature/FeatureRenderer", "entity/EntityRenderer")) {
                boolean found = false;
                for (var method : living.methods) if (method.name.equals("render")) for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/render/" + owner) && call.name.equals("render")) found = true;
                }
                check(found, "Vanilla living renderer owns " + owner);
            }
        }
    }
    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion();
            net.minecraft.Bootstrap.initialize();
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
            client = (Client) unsafe.allocateInstance(Client.class);
            set(null, MinecraftClient.class, "instance", client);
            client.player = (Player) unsafe.allocateInstance(Player.class);
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class);
            client.interactionManager = (Manager) unsafe.allocateInstance(Manager.class);
            var options = (GameOptions) unsafe.allocateInstance(GameOptions.class);
            set(options, GameOptions.class, "entityShadows", SimpleOption.ofBoolean("probe", true));
            set(client, MinecraftClient.class, "options", options);
            dispatcher = (Dispatcher) unsafe.allocateInstance(Dispatcher.class);
            set(dispatcher, EntityRenderDispatcher.class, "gameOptions", options);
            set(dispatcher, EntityRenderDispatcher.class, "rotation", new Quaternionf());
            dispatcher.setRenderShadows(true);
            dispatcher.setRenderHitboxes(true);
            renderer = (Renderer) unsafe.allocateInstance(Renderer.class);
            set(renderer, EntityRenderer.class, "shadowOpacity", 1f);
            audit();
            check(!InvisibleMobs.isTarget(null), "Null type");
            for (var type : Registries.ENTITY_TYPE) {
                boolean expected = type.getSpawnGroup() == SpawnGroup.MONSTER && type != EntityType.ENDER_DRAGON && type != EntityType.WITHER;
                check(InvisibleMobs.isTarget(type) == expected, "Every registered type follows exact category and boss exceptions");
                if (type == EntityType.PLAYER) continue; // Dispatcher uses a separate player-skin renderer map.
                var mob = mob(type);
                for (var current : GameMode.values()) {
                    mode = current;
                    sync("no_sprint");
                    render(mob, false, false);
                    sync(ID, ID, "no_sprint", "blind_hud");
                    boolean hidden = expected && mode.isSurvivalLike();
                    render(mob, false, hidden);
                    render(mob, true, hidden);
                }
            }
            mode = GameMode.SURVIVAL;
            var mob = mob(EntityType.ZOMBIE);
            sync(ID);
            render(mob, true, true);
            mob.naturalInvisible = true;
            render(mob, true, true);
            sync("no_sprint");
            render(mob, true, false);
            check(mob.naturalInvisible, "Stop does not clear foreign invisibility");
            mob.naturalInvisible = false;
            sync(ID);
            alive = false;
            render(mob, false, false);
            alive = true;
            client.player = (Player) unsafe.allocateInstance(Player.class);
            render(mob, false, true);
            set(client.player, Entity.class, "removalReason", Entity.RemovalReason.DISCARDED);
            render(mob, false, false);
            client.player = (Player) unsafe.allocateInstance(Player.class);
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class);
            render(mob, false, true);
            var world = client.world;
            client.world = null;
            render(mob, false, false);
            client.world = world;
            var player = client.player;
            client.player = null;
            render(mob, false, false);
            client.player = player;
            var manager = client.interactionManager;
            client.interactionManager = null;
            render(mob, false, false);
            client.interactionManager = manager;
            ClientDataHolder.reset();
            render(mob, false, false);
            sync("no_sprint");
            render(mob, false, false);
            check(!options.hudHidden && options.getEntityShadows().getValue() && dispatcher.shouldRenderHitboxes(), "User options unchanged");
            System.out.println("INVISIBLE_MOBS_CLIENT_PROBE_PASSED checks=" + checks);
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
}
