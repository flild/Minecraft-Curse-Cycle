package validation;

import com.cursecycle.challenge.impl.LowGravityChallenge;
import com.cursecycle.client.ClientDataHolder;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.fluid.*;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import sun.misc.Unsafe;

import java.util.*;

/** Real CLIENT attribute packet handler, travel, jump and knockback; synthetic world/collisions. */
public class LowGravityProbe implements PreLaunchEntrypoint {
    static WorldStub world;
    static Player player;
    static Entity packetTarget;
    static AttributeContainer attributes;
    static Vec3d velocity = Vec3d.ZERO;
    static PlayerAbilities abilities = new PlayerAbilities();
    static boolean creative, spectator, riding, slow, water, lava, levitation, noGravity, ground;
    static int checks;
    static final double DRAG = .9800000190734863;

    public static class Client extends MinecraftClient {
        public Client() { super(null); }
        @Override public boolean isOnThread() { return true; }
    }
    public static class WorldStub extends ClientWorld {
        public WorldStub() { super(null, null, null, null, 0, 0, null, null, false, 0); }
        @Override public BlockState getBlockState(BlockPos pos) { return Blocks.AIR.getDefaultState(); }
        @Override public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.getDefaultState(); }
        @Override public boolean isChunkLoaded(int x, int z) { return true; }
        @Override public Entity getEntityById(int id) { return packetTarget; }
    }
    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public World getWorld() { return world; }
        @Override public boolean isAlive() { return true; }
        @Override public boolean isCreative() { return creative; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public boolean hasVehicle() { return riding; }
        @Override public boolean isLogicalSideForUpdatingMovement() { return true; }
        @Override public boolean hasNoGravity() { return noGravity; }
        @Override public boolean hasNoDrag() { return false; }
        @Override public boolean isSwimming() { return false; }
        @Override public boolean isTouchingWater() { return water; }
        @Override public boolean isInLava() { return lava; }
        @Override public boolean isFallFlying() { return false; }
        @Override public boolean isClimbing() { return false; }
        @Override public boolean isSprinting() { return false; }
        @Override public boolean isOnGround() { return ground; }
        @Override public boolean shouldSwimInFluids() { return true; }
        @Override public double getFluidHeight(TagKey<Fluid> tag) { return 1; }
        @Override public double getSwimHeight() { return .4; }
        @Override public PlayerAbilities getAbilities() { return abilities; }
        @Override public Vec3d getVelocity() { return velocity; }
        @Override public void setVelocity(Vec3d v) { velocity = v; }
        @Override public void setVelocity(double x, double y, double z) { velocity = new Vec3d(x, y, z); }
        @Override public void updateVelocity(float speed, Vec3d input) {}
        @Override public void move(MovementType type, Vec3d v) {}
        @Override public void updateLimbs(boolean flutter) {}
        @Override public Vec3d applyMovementInput(Vec3d input, float slipperiness) { return velocity; }
        @Override public Vec3d getRotationVector() { return new Vec3d(0, 0, 1); }
        @Override public BlockPos getBlockPos() { return BlockPos.ORIGIN; }
        @Override public BlockPos getVelocityAffectingPos() { return BlockPos.ORIGIN; }
        @Override public AttributeContainer getAttributes() { return attributes; }
        @Override public EntityAttributeInstance getAttributeInstance(RegistryEntry<EntityAttribute> a) { return attributes.getCustomInstance(a); }
        @Override public double getAttributeValue(RegistryEntry<EntityAttribute> a) { return attributes.getValue(a); }
        @Override public boolean hasStatusEffect(RegistryEntry<StatusEffect> effect) {
            return effect == StatusEffects.SLOW_FALLING ? slow : effect == StatusEffects.LEVITATION && levitation;
        }
        @Override public StatusEffectInstance getStatusEffect(RegistryEntry<StatusEffect> effect) { return new StatusEffectInstance(effect, 200, 0); }
    }
    static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        var f = type.getDeclaredField(name); f.setAccessible(true); f.set(object, value);
    }
    static void near(double expected, double actual) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > .00001)
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
    }
    static double step(double y) { ground = false; velocity = new Vec3d(.2, y, .3); player.travel(Vec3d.ZERO); return velocity.y; }
    static void packet(ClientPlayNetworkHandler handler, boolean enabled, double base) {
        var source = new AttributeContainer(LivingEntity.createLivingAttributes().build());
        source.getCustomInstance(EntityAttributes.GENERIC_GRAVITY).setBaseValue(base);
        source.getCustomInstance(EntityAttributes.GENERIC_JUMP_STRENGTH).setBaseValue(.42);
        if (enabled) {
            source.getCustomInstance(EntityAttributes.GENERIC_GRAVITY).addTemporaryModifier(new EntityAttributeModifier(
                    LowGravityChallenge.GRAVITY_MODIFIER, -.75, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            source.getCustomInstance(EntityAttributes.GENERIC_JUMP_STRENGTH).addTemporaryModifier(new EntityAttributeModifier(
                    LowGravityChallenge.JUMP_MODIFIER, .25, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        handler.onEntityAttributes(new EntityAttributesS2CPacket(1, source.getAttributesToSend()));
    }
    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion(); net.minecraft.Bootstrap.initialize();
            var f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); var unsafe = (Unsafe) f.get(null);
            var client = (Client) unsafe.allocateInstance(Client.class);
            set(MinecraftClient.class, null, "instance", client);
            world = (WorldStub) unsafe.allocateInstance(WorldStub.class); set(World.class, world, "isClient", true);
            player = (Player) unsafe.allocateInstance(Player.class);
            packetTarget = player;
            set(net.minecraft.entity.player.PlayerEntity.class, player, "abilities", abilities);
            set(Entity.class, player, "pos", new Vec3d(0, 80, 0)); set(Entity.class, player, "type", EntityType.PLAYER);
            attributes = new AttributeContainer(LivingEntity.createLivingAttributes().build());
            var handler = (ClientPlayNetworkHandler) unsafe.allocateInstance(ClientPlayNetworkHandler.class);
            set(ClientCommonNetworkHandler.class, handler, "client", client);
            set(ClientPlayNetworkHandler.class, handler, "world", world);
            if (Arrays.stream(LivingEntity.class.getDeclaredMethods()).noneMatch(m -> m.getName().contains("cursecycle$knockback")))
                throw new AssertionError("CLIENT mixin not transformed");
            for (boolean enabled : new boolean[]{false, true}) for (double base : new double[]{0, .005, .04, .08}) {
                packet(handler, enabled, base);
                for (boolean pair : new boolean[]{false, true}) {
                    ClientDataHolder.updateState(1, 20, pair ? List.of("gravity_ten", "low_gravity") : List.of("low_gravity"), "", List.of());
                    for (boolean sf : new boolean[]{false, true}) for (double y : new double[]{-.7, -.1, 0, .2, .42}) {
                        slow = sf;
                        double g = base * (enabled ? .25 : 1);
                        if (sf && y <= 0) g = Math.min(g, .01);
                        g *= pair ? 3 : 1;
                        near((y - g) * DRAG, step(y));
                        levitation = true; near((y + (.05 - y) * .2) * DRAG, step(y)); levitation = false;
                        water = true;
                        double fluidG = (sf ? Math.min(base * (enabled ? .25 : 1), .01) : base * (enabled ? .25 : 1)) * (pair ? 3 : 1);
                        near(-.1 * .800000011920929 - fluidG / 16, step(-.1)); water = false;
                        lava = true; near(y * .5 - g / 4, step(y)); lava = false;
                    }
                }
                ClientDataHolder.reset(); slow = false;
                velocity = Vec3d.ZERO; ground = true; player.jump(); near(.42 * (enabled ? 1.25 : 1), velocity.y);
                velocity = Vec3d.ZERO; player.takeKnockback(.2, 1, 0); near(enabled ? -.3 : -.2, velocity.x);
                // The ordinary client tick hook must never invent or remove server-owned modifiers.
                LowGravityChallenge.reconcile(player); near(base * (enabled ? .25 : 1), player.getAttributeValue(EntityAttributes.GENERIC_GRAVITY));
            }
            packet(handler, true, .08);
            for (int mode = 0; mode < 4; mode++) {
                creative = mode == 0; spectator = mode == 1; riding = mode == 2; abilities.flying = mode == 3;
                near(0, LowGravityChallenge.active(player) ? 1 : 0);
            }
            creative = spectator = riding = abilities.flying = false;
            packet(handler, false, .08); near(-.08 * DRAG, step(0));
            velocity = Vec3d.ZERO; ground = true; player.jump(); near(.42, velocity.y);
            var pig = (net.minecraft.entity.passive.PigEntity) unsafe.allocateInstance(net.minecraft.entity.passive.PigEntity.class);
            set(LivingEntity.class, pig, "attributes", new AttributeContainer(LivingEntity.createLivingAttributes().build()));
            packetTarget = pig; ClientDataHolder.reset();
            packet(handler, true, .08);
            near(.02, pig.getAttributeValue(EntityAttributes.GENERIC_GRAVITY));
            near(.42 * 1.25, pig.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH));
            packet(handler, false, .08);
            near(.08, pig.getAttributeValue(EntityAttributes.GENERIC_GRAVITY));
            near(.42, pig.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH));
            System.out.println("LOW_GRAVITY_CLIENT_PASSED checks=" + checks
                    + ": real CLIENT attribute handler, travel/jump/knockback, fluids, Slow Falling, levitation, gravity_ten and removal; synthetic world/collisions");
            System.exit(0);
        } catch (Throwable t) { t.printStackTrace(); System.exit(1); }
    }
}
