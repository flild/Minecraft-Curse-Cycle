package validation;

import com.cursecycle.challenge.impl.GiantPlayerChallenge;
import com.cursecycle.client.ClientDataHolder;
import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.player.*;
import net.minecraft.fluid.*;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.*;
import sun.misc.Unsafe;
import java.util.*;

/** Real CLIENT packet, dimensions, camera, renderer scale and jump; synthetic world and no GPU. */
public class GiantPlayerProbe implements PreLaunchEntrypoint {
    static WorldStub world;
    static Entity packetTarget;
    static int checks;
    static Unsafe unsafe;
    static final Identifier FOREIGN=Identifier.of("probe","foreign_scale");
    public static class Client extends MinecraftClient {
        public Client(){super(null);}
        @Override public boolean isOnThread(){return true;}
    }
    public static class WorldStub extends ClientWorld {
        public WorldStub(){super(null,null,null,null,0,0,null,null,false,0);}
        @Override public BlockState getBlockState(BlockPos p){return Blocks.AIR.getDefaultState();}
        @Override public FluidState getFluidState(BlockPos p){return Fluids.EMPTY.getDefaultState();}
        @Override public boolean isChunkLoaded(int x,int z){return true;}
        @Override public Entity getEntityById(int id){return packetTarget;}
        @Override public BlockHitResult raycast(RaycastContext c){return BlockHitResult.createMissed(c.getEnd(),Direction.UP,BlockPos.ofFloored(c.getEnd()));}
    }
    public static class Player extends ClientPlayerEntity {
        public Player(){super(null,null,null,null,null,false,false);}
        @Override public World getWorld(){return world;}
        @Override public boolean isAlive(){return true;}
        @Override public boolean isCreative(){return false;}
        @Override public boolean isSpectator(){return false;}
        @Override public boolean hasVehicle(){return false;}
        @Override public boolean isSprinting(){return false;}
        @Override public boolean isOnGround(){return true;}
        @Override public boolean hasStatusEffect(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> e){return false;}
        @Override public Text getName(){return Text.literal("probe");}
        @Override public boolean hasCustomName(){return false;}
        @Override public boolean isSleeping(){return false;}
    }
    public static class Other extends OtherClientPlayerEntity {
        public Other(){super(null,new GameProfile(UUID.randomUUID(),"probe"));}
        @Override public World getWorld(){return world;}
        @Override public boolean isAlive(){return true;}
        @Override public boolean hasVehicle(){return false;}
        @Override public Text getName(){return Text.literal("remote");}
        @Override public boolean hasCustomName(){return false;}
        @Override public boolean isSleeping(){return false;}
    }
    public static class Model extends EntityModel<LivingEntity> {
        @Override public void setAngles(LivingEntity e,float a,float b,float c,float d,float f){}
        @Override public void render(MatrixStack m,VertexConsumer v,int l,int o,int c){}
    }
    private static final class ScaleReached extends RuntimeException {}
    public static class Renderer extends LivingEntityRenderer<LivingEntity,Model> {
        public Renderer(){super(null,null,0);}
        @Override public Identifier getTexture(LivingEntity e){return Identifier.ofVanilla("probe");}
        @Override protected float getHandSwingProgress(LivingEntity e,float delta){return 0;}
        // This virtual call is immediately AFTER the real vanilla MatrixStack.scale(getScale()) in render.
        @Override protected float getAnimationProgress(LivingEntity e,float delta){throw new ScaleReached();}
    }
    static void set(Class<?> type,Object o,String name,Object v)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(o,v);}
    static void near(double e,double a){checks++;if(!Double.isFinite(a)||Math.abs(e-a)>.00001)throw new AssertionError("expected="+e+", actual="+a);}
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void tracked(net.minecraft.entity.data.DataTracker.Builder builder,String name,Object value)throws Exception{
        var field=Entity.class.getDeclaredField(name);field.setAccessible(true);
        builder.add((net.minecraft.entity.data.TrackedData)field.get(null),value);
    }
    static PlayerEntity player(boolean other)throws Exception{
        var p=(PlayerEntity)unsafe.allocateInstance(other?Other.class:Player.class);
        set(Entity.class,p,"world",world);set(Entity.class,p,"pos",new Vec3d(0,80,0));set(Entity.class,p,"type",EntityType.PLAYER);
        set(Entity.class,p,"dimensions",EntityType.PLAYER.getDimensions());
        set(Entity.class,p,"supportingBlockPos",Optional.empty());
        set(Entity.class,p,"velocity",Vec3d.ZERO);
        set(PlayerEntity.class,p,"abilities",new PlayerAbilities());
        set(PlayerEntity.class,p,"inventory",new PlayerInventory(p));
        set(LivingEntity.class,p,"attributes",new AttributeContainer(PlayerEntity.createPlayerAttributes().build()));
        set(Entity.class,p,"random",net.minecraft.util.math.random.Random.create(1));
        var builder=new net.minecraft.entity.data.DataTracker.Builder(p);
        tracked(builder,"FLAGS",(byte)0);tracked(builder,"AIR",300);tracked(builder,"NAME_VISIBLE",false);
        tracked(builder,"CUSTOM_NAME",Optional.empty());tracked(builder,"SILENT",false);tracked(builder,"NO_GRAVITY",false);
        tracked(builder,"POSE",EntityPose.STANDING);tracked(builder,"FROZEN_TICKS",0);
        var init=PlayerEntity.class.getDeclaredMethod("initDataTracker",net.minecraft.entity.data.DataTracker.Builder.class);
        init.setAccessible(true);init.invoke(p,builder);set(Entity.class,p,"dataTracker",builder.build());
        p.setPose(EntityPose.STANDING);p.calculateDimensions();return p;
    }
    static void packet(ClientPlayNetworkHandler h,PlayerEntity p,boolean enabled,double base,double foreign){
        packetTarget=p;
        var source=new AttributeContainer(PlayerEntity.createPlayerAttributes().build());
        for(var key:List.of(EntityAttributes.GENERIC_SCALE,EntityAttributes.GENERIC_STEP_HEIGHT,EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE,EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE)) source.getCustomInstance(key);
        source.getCustomInstance(EntityAttributes.GENERIC_SCALE).setBaseValue(base);
        if(foreign!=0)source.getCustomInstance(EntityAttributes.GENERIC_SCALE).addTemporaryModifier(new EntityAttributeModifier(FOREIGN,foreign,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if(enabled){
            source.getCustomInstance(EntityAttributes.GENERIC_SCALE).addTemporaryModifier(new EntityAttributeModifier(GiantPlayerChallenge.SCALE_MODIFIER,GiantPlayerChallenge.SCALE-1,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            source.getCustomInstance(EntityAttributes.GENERIC_STEP_HEIGHT).addTemporaryModifier(new EntityAttributeModifier(GiantPlayerChallenge.STEP_MODIFIER,1,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            source.getCustomInstance(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE).addTemporaryModifier(new EntityAttributeModifier(GiantPlayerChallenge.BLOCK_MODIFIER,.5,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            source.getCustomInstance(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE).addTemporaryModifier(new EntityAttributeModifier(GiantPlayerChallenge.ENTITY_MODIFIER,.5,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        h.onEntityAttributes(new EntityAttributesS2CPacket(1,source.getAttributesToSend()));
        // Vanilla LivingEntity.tick ends with this same method when prevScale != getScale.
        p.calculateDimensions();
    }
    @Override public void onPreLaunch(){try{
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
        var client=(Client)unsafe.allocateInstance(Client.class);set(MinecraftClient.class,null,"instance",client);
        world=(WorldStub)unsafe.allocateInstance(WorldStub.class);set(World.class,world,"isClient",true);
        var h=(ClientPlayNetworkHandler)unsafe.allocateInstance(ClientPlayNetworkHandler.class);set(ClientCommonNetworkHandler.class,h,"client",client);set(ClientPlayNetworkHandler.class,h,"world",world);
        if(Arrays.stream(PlayerEntity.class.getDeclaredMethods()).noneMatch(m->m.getName().contains("cursecycle$tinyAttributes")))throw new AssertionError("CLIENT common Mixin not transformed");
        var renderer=(Renderer)unsafe.allocateInstance(Renderer.class);set(LivingEntityRenderer.class,renderer,"model",new Model());
        for(boolean other:new boolean[]{false,true}) {
            var p=player(other);
            for(double base:new double[]{.8,1,1.2}) for(double foreign:new double[]{0,.2}) for(boolean active:new boolean[]{true,false,true,false}) {
                // Observer/local challenge IDs intentionally disagree with target attributes.
                ClientDataHolder.updateState(1,20,active?List.of():List.of("giant_player"),"",List.of());
                packet(h,p,active,base,foreign);double scale=base*(1+foreign)*(active?GiantPlayerChallenge.SCALE:1);
                near(active?1.2:.6,p.getStepHeight());near(scale,p.getScale());near(active?6.75:4.5,p.getBlockInteractionRange());near(active?4.5:3,p.getEntityInteractionRange());
                GiantPlayerChallenge.reconcile(p);near(scale,p.getScale());
                for(var pose:List.of(EntityPose.STANDING,EntityPose.CROUCHING,EntityPose.SWIMMING,EntityPose.FALL_FLYING)) {
                    p.setPose(pose);
                    near(p.getBaseDimensions(pose).height()*scale,p.getHeight());near(p.getHeight(),p.getBoundingBox().getLengthY());near(p.getBaseDimensions(pose).eyeHeight()*scale,p.getStandingEyeHeight());
                    var camera=new Camera();camera.update(world,p,false,false,1);
                    for(int i=0;i<24;i++)camera.updateEyeHeight();camera.update(world,p,false,false,1);
                    near(p.getY()+p.getStandingEyeHeight(),camera.getPos().y);
                    camera.update(world,p,true,false,1);near(4*scale,camera.getPos().subtract(new Vec3d(p.getX(),p.getY()+p.getStandingEyeHeight(),p.getZ())).length());
                    camera.update(world,p,true,true,1);near(4*scale,camera.getPos().subtract(new Vec3d(p.getX(),p.getY()+p.getStandingEyeHeight(),p.getZ())).length());
                    var matrices=new MatrixStack();try{renderer.render(p,0,1,matrices,null,0);throw new AssertionError("Renderer did not reach scale");}catch(ScaleReached expected){}
                    near(scale,matrices.peek().getPositionMatrix().m00());near(scale,matrices.peek().getPositionMatrix().m11());near(scale,matrices.peek().getPositionMatrix().m22());
                }
                if(!other){p.setVelocity(Vec3d.ZERO);p.jump();near(.42,p.getVelocity().y);}
            }
        }
        System.out.println("GIANT_PLAYER_CLIENT_PASSED checks="+checks+": transformed CLIENT, real attribute handler; local/remote dimensions, model matrix, camera/F5/front, jump and ranges; synthetic world, no GPU");System.exit(0);
    }catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
