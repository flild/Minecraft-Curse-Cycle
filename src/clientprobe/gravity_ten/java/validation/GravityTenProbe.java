package validation;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.challenge.impl.GravityTenChallenge;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.effect.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.block.*;
import net.minecraft.fluid.*;
import net.minecraft.util.math.*;
import java.util.*;
import sun.misc.Unsafe;

public class GravityTenProbe implements PreLaunchEntrypoint {
    static FakeWorld world;
    static boolean alive=true, creative, spectator, riding, slow, levitation, water, lava, elytra;
    static Vec3d velocity=Vec3d.ZERO;
    static PlayerAbilities abilities=new PlayerAbilities();
    static double gravity=.08, probeFluidHeight=1;
    static boolean noGravity, sprint;
    static int checks, steps;
    public static class FakeWorld extends ClientWorld {
        public FakeWorld() { super(null,null,null,null,0,0,null,null,false,0); }
        @Override public BlockState getBlockState(BlockPos pos) { return Blocks.AIR.getDefaultState(); }
        @Override public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.getDefaultState(); }
        @Override public boolean isChunkLoaded(int x,int z) { return true; }
    }
    public static class Player extends ClientPlayerEntity {
        public Player() { super(null,null,null,null,null,false,false); }
        @Override public World getWorld() { return world; }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isCreative() { return creative; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public boolean hasVehicle() { return riding; }
        @Override public boolean isLogicalSideForUpdatingMovement() { return true; }
        @Override public boolean hasNoGravity() { return noGravity; }
        @Override public boolean hasNoDrag() { return false; }
        @Override public boolean isSwimming() { return false; }
        @Override public boolean isTouchingWater() { return water; }
        @Override public boolean isInLava() { return lava; }
        @Override public boolean isFallFlying() { return elytra; }
        @Override public boolean isClimbing() { return false; }
        @Override public boolean isSprinting() { return sprint; }
        @Override public boolean isOnGround() { return false; }
        @Override public boolean shouldSwimInFluids() { return true; }
        @Override public double getFluidHeight(TagKey<Fluid> tag) { return probeFluidHeight; }
        @Override public double getSwimHeight() { return .4; }
        @Override public PlayerAbilities getAbilities() { return abilities; }
        @Override public Vec3d getVelocity() { return velocity; }
        @Override public void setVelocity(Vec3d v) { velocity=v; }
        @Override public void setVelocity(double x,double y,double z) { velocity=new Vec3d(x,y,z); }
        @Override public void updateVelocity(float speed,Vec3d input) {}
        @Override public void move(MovementType type,Vec3d v) {}
        @Override public void updateLimbs(boolean flutter) {}
        @Override public Vec3d applyMovementInput(Vec3d input,float slipperiness) { return velocity; }
        @Override public Vec3d getRotationVector() { return new Vec3d(0,0,1); }
        @Override public BlockPos getBlockPos() { return BlockPos.ORIGIN; }
        @Override public BlockPos getVelocityAffectingPos() { return BlockPos.ORIGIN; }
        @Override public double getAttributeValue(RegistryEntry<EntityAttribute> a) {
            return a==EntityAttributes.GENERIC_GRAVITY ? gravity : a==EntityAttributes.GENERIC_SAFE_FALL_DISTANCE ? 3 : a==EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER ? 1 : 0;
        }
        @Override public boolean hasStatusEffect(RegistryEntry<StatusEffect> effect) {
            return effect==StatusEffects.SLOW_FALLING ? slow : effect==StatusEffects.LEVITATION && levitation;
        }
        @Override public StatusEffectInstance getStatusEffect(RegistryEntry<StatusEffect> effect) { return new StatusEffectInstance(effect,1200,0); }
    }
    static void near(double a,double b) { checks++; if(!Double.isFinite(b)||Math.abs(a-b)>.000001) throw new AssertionError(a+" != "+b); }
    static double step(Player p,double y) { steps++; velocity=new Vec3d(.2,y,.3); p.travel(Vec3d.ZERO); return velocity.y; }
    static void active(boolean value) {
        ClientDataHolder.updateState(1,20,value?List.of("gravity_ten"):List.of(),"",List.of());
    }
    static void matrix(Player p) throws Exception {
        final double drag=.9800000190734863;
        for(boolean enabled:new boolean[]{false,true}) for(double g:new double[]{0,.005,.01,.04,.08,.12})
            for(double y:new double[]{-.7,-.1,0,.2,.42}) for(boolean sf:new boolean[]{false,true}) {
                active(enabled); gravity=g; slow=sf;
                double effective=(sf&&y<=0?Math.min(g,.01):g)*(enabled?3:1);
                near((y-effective)*drag,step(p,y));
                near(.2*.91,velocity.x); near(.3*.91,velocity.z);
                levitation=true; near((y+(.05-y)*.2)*drag,step(p,y)); levitation=false;
                water=true;
                // Small descending fluid velocities snap to -0.003 in vanilla; test an independent non-boundary velocity.
                double waterG=(sf?Math.min(g,.01):g)*(enabled?3:1);
                near(-.1*.800000011920929-waterG/16,step(p,-.1));
                sprint=true; near(-.1*.800000011920929,step(p,-.1)); sprint=false; water=false;
                lava=true; probeFluidHeight=1;
                near(y*.5-effective/4,step(p,y));
                probeFluidHeight=.1; near(-.1*.800000011920929-waterG/16-waterG/4,step(p,-.1));
                lava=false; probeFluidHeight=1;
            }
        gravity=.08; slow=false;
        active(true); noGravity=true; near(.2*drag,step(p,.2)); noGravity=false;
        var damage=LivingEntity.class.getDeclaredMethod("computeFallDamage",float.class,float.class); damage.setAccessible(true);
        for(float distance:new float[]{0,2,3,3.1F,4.1F,6,12}) for(float multiplier:new float[]{0,.2F,.5F,1}) {
            active(false); int control=(int)damage.invoke(p,distance,multiplier);
            active(true); near(control>0?control*3:control,(int)damage.invoke(p,distance,multiplier));
        }
        for(int cycle=0;cycle<32;cycle++) {
            ClientDataHolder.reset(); near(-.08*drag,step(p,0));
            for(int repeat=0;repeat<50;repeat++) {
                ClientDataHolder.updateState(1,20,List.of("gravity_ten","gravity_ten"),"",List.of());
                near(-.24*drag,step(p,0));
            }
            velocity=new Vec3d(.2,-.7,.3); p.fallDistance=12; active(false);
            near(-.7,velocity.y); near(12,p.fallDistance);
            near((-.7-.08)*drag,step(p,-.7));
        }
        active(true);
        var removed=Entity.class.getDeclaredField("removalReason"); removed.setAccessible(true);
        for(int mode=0;mode<5;mode++) {
            alive=mode!=0; creative=mode==1; spectator=mode==2; riding=mode==3;
            removed.set(p,mode==4?Entity.RemovalReason.DISCARDED:null);
            near(-.08*drag,step(p,0));
        }
        removed.set(p,null); alive=true; creative=spectator=riding=false;
        ClientDataHolder.reset();
    }
    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion(); net.minecraft.Bootstrap.initialize();
            var f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); var u=(Unsafe)f.get(null);
            world=(FakeWorld)u.allocateInstance(FakeWorld.class);
            f=World.class.getDeclaredField("isClient"); f.setAccessible(true); f.set(world,true);
            var p=(Player)u.allocateInstance(Player.class);
            f=net.minecraft.entity.player.PlayerEntity.class.getDeclaredField("abilities"); f.setAccessible(true); f.set(p,abilities);
            f=Entity.class.getDeclaredField("pos"); f.setAccessible(true); f.set(p,new Vec3d(0,80,0));
            f=Entity.class.getDeclaredField("type"); f.setAccessible(true); f.set(p,EntityType.PLAYER);
            ClientDataHolder.reset(); near(-.08*.9800000190734863,step(p,0));
            ClientDataHolder.updateState(1,20,List.of("gravity_ten"),"",List.of());
            near(-.24*.9800000190734863,step(p,0));
            near((.42-.24)*.9800000190734863,step(p,.42));
            slow=true; near(-.03*.9800000190734863,step(p,0)); slow=false;
            levitation=true; near(.01*.9800000190734863,step(p,0)); levitation=false;
            water=true; near(-.24/16,step(p,0));
            slow=true; near(-.003,step(p,0)); slow=false; water=false;
            lava=true; near(-.24/4,step(p,0)); lava=false;
            elytra=true; near(-.24*.25*.9*.9800000190734863,step(p,0)); elytra=false;
            for(int i=0;i<4;i++) {
                alive=i!=0; creative=i==1; spectator=i==2; riding=i==3;
                if(GravityTenChallenge.active(p)) throw new AssertionError("Exclusion "+i);
                near(-.08*.9800000190734863,step(p,0));
            }
            alive=true; creative=spectator=riding=false;
            var damage=LivingEntity.class.getDeclaredMethod("computeFallDamage",float.class,float.class); damage.setAccessible(true);
            near(9,(int)damage.invoke(p,6F,1F));
            ClientDataHolder.reset(); near(-.08*.9800000190734863,step(p,0)); near(3,(int)damage.invoke(p,6F,1F));
            matrix(p);
            System.out.println("GRAVITY_TEN_REVIEW_CLIENT_PASSED checks="+checks+" travel="+steps+": real CLIENT travel and fall-damage transforms; air, impulse, slow falling, levitation, water, shallow/deep lava, elytra, exclusions and reset; stub world/collisions/player accessors");
            System.exit(0);
        } catch(Throwable t) { t.printStackTrace(); System.exit(1); }
    }
}
