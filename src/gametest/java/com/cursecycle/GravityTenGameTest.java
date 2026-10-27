package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.GravityTenChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

public class GravityTenGameTest implements FabricGameTest {
    private static final String ID = GravityTenChallenge.ID;
    private static final double DRAG = .9800000190734863;
    private static void near(TestContext c, double expected, double actual, String why) {
        c.assertTrue(Math.abs(expected - actual) < .000001, why + ": expected="+expected+", actual="+actual);
    }
    private static void reset(ServerPlayerEntity p, BlockPos base, Vec3d velocity) {
        p.refreshPositionAndAngles(base.getX()+.5, base.getY()+.5, base.getZ()+.5, 0, 0);
        p.setOnGround(false); p.setVelocity(Vec3d.ZERO); p.baseTick(); p.setVelocity(velocity);
    }
    private static double step(ServerPlayerEntity p, BlockPos base, Vec3d velocity) {
        reset(p, base, velocity); p.travel(Vec3d.ZERO); return p.getVelocity().y;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gravity_ten_physics", tickLimit = 40)
    public void realTravelBranchesAndFallDamage(TestContext c) throws Exception {
        try (var s = new ChallengeScenario(c)) {
            var p = s.player(UUID.randomUUID(), true); var base = c.getAbsolutePos(new BlockPos(3, 5, 3));
            var world = c.getWorld(); var blocks = new LinkedHashMap<BlockPos, BlockState>();
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) for(int y=-2;y<=3;y++) {
                var pos=base.add(x,y,z); blocks.put(pos,world.getBlockState(pos));
                world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
            }
            try {
                near(c,-.08*DRAG,step(p,base,Vec3d.ZERO),"Control air");
                var commands=world.getServer().getCommandManager();
                commands.executeWithPrefix(world.getServer().getCommandSource(),"fdc list");
                commands.executeWithPrefix(world.getServer().getCommandSource(),"fdc activate gravity_ten");
                commands.executeWithPrefix(world.getServer().getCommandSource(),"fdc active");
                c.assertTrue(s.state.getActiveChallengeIds().contains(ID),"Command activation");
                near(c,-.24*DRAG,step(p,base,Vec3d.ZERO),"Triple air acceleration");
                near(c,(-.7-.24)*DRAG,step(p,base,new Vec3d(.2,-.7,.3)),"Velocity and knockback not multiplied");
                near(c,.2*.91,p.getVelocity().x,"Horizontal drag preserved");
                reset(p,base,Vec3d.ZERO); p.setOnGround(true); p.jump(); double jump=p.getVelocity().y;
                near(c,.42,jump,"Jump impulse unchanged");
                near(c,(jump-.24)*DRAG,step(p,base,new Vec3d(0,jump,0)),"Jump decelerates sooner");
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST,1200,1));
                reset(p,base,Vec3d.ZERO); p.setOnGround(true); p.jump(); near(c,.62,p.getVelocity().y,"Jump Boost intact");
                p.removeStatusEffect(StatusEffects.JUMP_BOOST);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING,1200,0));
                near(c,-.03*DRAG,step(p,base,Vec3d.ZERO),"Slow Falling multiplied after min");
                near(c,(.2-.24)*DRAG,step(p,base,new Vec3d(0,.2,0)),"Slow Falling ascent uses normal gravity");
                p.removeStatusEffect(StatusEffects.SLOW_FALLING);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION,1200,1));
                double levitation=step(p,base,Vec3d.ZERO); s.activate("test_easy");
                near(c,levitation,step(p,base,Vec3d.ZERO),"Levitation replaces gravity"); p.removeStatusEffect(StatusEffects.LEVITATION);
                p.getAttributeInstance(EntityAttributes.GENERIC_GRAVITY).setBaseValue(.04); s.activate(ID);
                near(c,-.12*DRAG,step(p,base,Vec3d.ZERO),"External gravity attribute respected");
                p.getAttributeInstance(EntityAttributes.GENERIC_GRAVITY).setBaseValue(.08);
                p.setNoGravity(true); near(c,0,step(p,base,Vec3d.ZERO),"No gravity stays zero"); p.setNoGravity(false);
                for (var fluid: List.of(Blocks.WATER,Blocks.LAVA)) {
                    // Clear the previous pool first: placing lava into water creates solid blocks immediately.
                    for (var pos: blocks.keySet()) world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
                    for (var pos: blocks.keySet()) world.setBlockState(pos,fluid.getDefaultState(),Block.NOTIFY_LISTENERS);
                    s.activate("test_easy"); double control=step(p,base,Vec3d.ZERO);
                    c.assertTrue(fluid==Blocks.WATER ? p.isTouchingWater() : p.isInLava(),"Real fluid branch");
                    s.activate(ID); near(c,control*3,step(p,base,Vec3d.ZERO),fluid+" gravity");
                    if(fluid==Blocks.WATER) {
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING,1200,0));
                        near(c,-.003,step(p,base,Vec3d.ZERO),"Water keeps vanilla tiny-speed snap");
                        near(c,-.1*.800000011920929-.03/16,step(p,base,new Vec3d(0,-.1,0)),"Water Slow Falling");
                        p.removeStatusEffect(StatusEffects.SLOW_FALLING);
                        p.setSprinting(true); near(c,0,step(p,base,Vec3d.ZERO),"Water sprint has no gravity term"); p.setSprinting(false);
                    }
                }
                // A shallow lava layer uses BOTH g/16 and g/4 in this version.
                for (var pos:blocks.keySet()) world.setBlockState(pos,pos.getY()==base.getY() ? Blocks.LAVA.getDefaultState() : Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
                p.refreshPositionAndAngles(base.getX()+.5,base.getY()+.85,base.getZ()+.5,0,0); p.setVelocity(Vec3d.ZERO); p.baseTick();
                c.assertTrue(p.isInLava() && p.getFluidHeight(net.minecraft.registry.tag.FluidTags.LAVA)<=p.getSwimHeight(),"Shallow lava branch");
                p.travel(Vec3d.ZERO); near(c,-.24/16-.24/4,p.getVelocity().y,"Both shallow lava terms scale once");
                for(var pos:blocks.keySet()) world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
                reset(p,base,Vec3d.ZERO); p.startFallFlying(); c.assertTrue(p.isFallFlying(),"Elytra branch");
                // Pitch zero: g * (-1 + .75), then vanilla descent lift (+10%) and drag.
                p.travel(Vec3d.ZERO); near(c,-.24*.25*.9*DRAG,p.getVelocity().y,"Elytra gravity"); p.stopFallFlying();
                reset(p,base,Vec3d.ZERO); p.fallDistance=12; p.setVelocity(.1,-.7,.2); s.activate("no_sprint");
                near(c,12,p.fallDistance,"Stop keeps fall distance"); near(c,-.7,p.getVelocity().y,"Stop keeps velocity");
                near(c,(-.7-.08)*DRAG,step(p,base,new Vec3d(0,-.7,0)),"Next step restores ordinary gravity");
                // Test rounded vanilla damage, safe distance, and real health loss via handleFallDamage.
                var compute=LivingEntity.class.getDeclaredMethod("computeFallDamage",float.class,float.class); compute.setAccessible(true);
                int control=(int)compute.invoke(p,4.1F,.5F); s.activate(ID);
                near(c,control*3,(int)compute.invoke(p,4.1F,.5F),"Triple rounded fall damage");
                near(c,-1,(int)compute.invoke(p,2F,1F),"Nonpositive vanilla fall result unchanged");
                p.setNoGravity(true); p.setVelocity(Vec3d.ZERO); p.extinguish();
                for(int i=0;i<65;i++) p.tick();
                p.setNoGravity(false);
                p.extinguish(); p.setHealth(20); p.timeUntilRegen=0; p.handleFallDamage(5,1,p.getDamageSources().fall());
                near(c,14,p.getHealth(),"Actual fall damage x3");
                p.timeUntilRegen=0; p.damage(p.getDamageSources().generic(),1); near(c,13,p.getHealth(),"Other damage unchanged");
                var boat=new BoatEntity(world,base.getX(),base.getY(),base.getZ()); p.startRiding(boat,true);
                c.assertFalse(GravityTenChallenge.active(p),"Passenger excluded"); p.stopRiding(); boat.discard();
                var pig=EntityType.PIG.create(world); pig.refreshPositionAndAngles(base.getX(),base.getY(),base.getZ(),0,0);
                pig.setVelocity(Vec3d.ZERO); pig.travel(Vec3d.ZERO); near(c,-.08*DRAG,pig.getVelocity().y,"Mobs unchanged"); pig.discard();
            } finally { blocks.forEach((pos,state)->world.setBlockState(pos,state,Block.NOTIFY_LISTENERS)); }
        }
        c.waitAndRun(5,c::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gravity_ten_lifecycle", tickLimit = 40)
    public void activationModesPersistenceAndLifecycle(TestContext c) throws Exception {
        var challenge=ChallengeRegistry.get(ID);
        try(var s=new ChallengeScenario(c)) {
            var server=c.getWorld().getServer(); var p=s.player(UUID.randomUUID(),true);
            var base=c.getAbsolutePos(new BlockPos(3,7,3));
            s.activate(ID); challenge.onStart(server,p); challenge.onPlayerJoin(server,p);
            near(c,-.24*DRAG,step(p,base,Vec3d.ZERO),"Repeated lifecycle idempotent");
            p.changeGameMode(GameMode.CREATIVE); near(c,-.08*DRAG,step(p,base,Vec3d.ZERO),"Creative excluded");
            p.changeGameMode(GameMode.SPECTATOR); c.assertFalse(GravityTenChallenge.active(p),"Spectator excluded");
            p.changeGameMode(GameMode.ADVENTURE); near(c,-.24*DRAG,step(p,base,Vec3d.ZERO),"Adventure"); p.changeGameMode(GameMode.SURVIVAL);
            s.activate("double_challenge:gravity_ten,heavy_body"); reset(p,base,Vec3d.ZERO); p.setOnGround(true); p.jump();
            near(c,.42*.6,p.getVelocity().y,"Heavy body owns only jump impulse");
            near(c,(.42*.6-.24)*DRAG,step(p,base,p.getVelocity()),"Heavy body and gravity compose");
            s.activate("double_challenge:gravity_ten,no_jumping"); reset(p,base,Vec3d.ZERO); p.jump(); near(c,0,p.getVelocity().y,"No jumping still cancels");
            s.activate("double_challenge:gravity_ten,no_sprint"); p.setSprinting(true); c.assertFalse(p.isSprinting(),"No sprint preserved");
            s.activate("no_sprint"); near(c,-.08*DRAG,step(p,base,Vec3d.ZERO),"Pair cleanup");
            s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID);
            near(c,-.24*DRAG,step(p,base,Vec3d.ZERO),"Personal"); s.state.getActiveChallengeIds().add(ID);
            near(c,-.24*DRAG,step(p,base,Vec3d.ZERO),"Duplicate scope once");
            challenge.setEnabled(false); near(c,-.08*DRAG,step(p,base,Vec3d.ZERO),"Disabled"); challenge.setEnabled(true);
            s.state.getPersonalActiveChallengeIds(p.getUuid()).clear(); s.activate(ID);
            var late=s.player(); c.assertTrue(GravityTenChallenge.active(late),"Late join"); UUID uuid=late.getUuid();
            server.getPlayerManager().remove(late); late=s.player(uuid); c.assertTrue(GravityTenChallenge.active(late),"Reconnect");
            late.setHealth(0); c.assertFalse(GravityTenChallenge.active(late),"Death"); late=s.respawn(late,false);
            c.assertTrue(GravityTenChallenge.active(late),"Respawn");
            late.teleport(server.getWorld(World.NETHER),base.getX(),80,base.getZ(),0,0); c.assertTrue(GravityTenChallenge.active(late),"Nether");
            late.teleport(c.getWorld(),base.getX(),base.getY(),base.getZ(),0,0);
            var manager=server.getOverworld().getPersistentStateManager(); manager.save();
            var saved=manager.readNbt(CurseCycleState.STATE_KEY,null,SharedConstants.getGameVersion().getSaveVersion().getId());
            var restored=CurseCycleState.createFromNbt(saved.getCompound("data"),c.getWorld().getRegistryManager()); manager.set(CurseCycleState.STATE_KEY,restored);
            near(c,-.24*DRAG,step(p,base,Vec3d.ZERO),"Real disk restoration");
            c.assertFalse(restored.getChallengeData().contains(ID),"No custom stale state");
            server.getPlayerManager().remove(late); CycleManager.activateChallenge(server,restored,"test_easy");
            late=s.player(uuid); c.assertFalse(GravityTenChallenge.active(late),"Ended offline");
        } finally { challenge.setEnabled(true); }
        c.waitAndRun(5,c::complete);
    }
}
