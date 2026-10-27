package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.AutoJumpChallenge;
import com.cursecycle.network.AutoJumpPayload;
import com.cursecycle.state.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.block.*;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.screen.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

public class AutoJumpGameTest implements FabricGameTest {
    private static final String ID = AutoJumpChallenge.ID;
    private static void near(TestContext c, double expected, double actual, String why) {
        c.assertTrue(Math.abs(expected-actual)<.000001, why+": expected="+expected+", actual="+actual);
    }
    private static void ground(ServerPlayerEntity p) { p.setVelocity(Vec3d.ZERO); p.setOnGround(true); }
    private static List<AutoJumpPayload> packets(ChallengeScenario s, ServerPlayerEntity p) {
        var channel=s.channel(p); channel.runPendingTasks(); channel.flush();
        var result=new ArrayList<AutoJumpPayload>(); Object packet;
        while((packet=channel.readOutbound())!=null)
            if(packet instanceof CustomPayloadS2CPacket custom && custom.payload() instanceof AutoJumpPayload jump) result.add(jump);
        return result;
    }
    private static void jump(TestContext c, ChallengeScenario s, ServerPlayerEntity p, double expected) {
        ground(p); AutoJumpChallenge.tick(p.getServer()); near(c,expected,p.getVelocity().y,"Ordinary auto impulse");
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="auto_jump_physics", tickLimit=40)
    public void ordinaryJumpExclusionsAndComposition(TestContext c) throws Exception {
        try(var s=new ChallengeScenario(c)) {
            var p=s.player(); var server=p.getServer(); var base=c.getAbsolutePos(new BlockPos(3,7,3));
            p.refreshPositionAndAngles(base.getX()+.5,base.getY(),base.getZ()+.5,0,0);
            ground(p); AutoJumpChallenge.tick(server); near(c,0,p.getVelocity().y,"Inactive control");
            var commands=server.getCommandManager();
            commands.executeWithPrefix(server.getCommandSource(),"fdc list");
            commands.executeWithPrefix(server.getCommandSource(),"fdc activate auto_jump");
            commands.executeWithPrefix(server.getCommandSource(),"fdc active");
            p.setSneaking(true); float hunger=p.getHungerManager().getExhaustion();
            int stats=p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.JUMP));
            packets(s,p); jump(c,s,p,.42); c.assertFalse(p.isOnGround(),"Takeoff clears server ground");
            near(c,hunger+.05,p.getHungerManager().getExhaustion(),"Vanilla hunger once");
            c.assertEquals(stats+1,p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.JUMP)),"One vanilla jump stat");
            var sent=packets(s,p); c.assertEquals(1,sent.size(),"One approved motion packet");
            near(c,.42,sent.getFirst().impulse().y,"Packet keeps full impulse");
            p.jump(); AutoJumpChallenge.tick(server); near(c,.42,p.getVelocity().y,"Same-tick manual cannot add impulse");
            near(c,hunger+.05,p.getHungerManager().getExhaustion(),"No double exhaustion");
            p.setSneaking(false); s.activate(ID); p.setSprinting(true); jump(c,s,p,.42);
            near(c,.2,p.getVelocity().z,"Vanilla sprint impulse once"); p.jump(); near(c,.2,p.getVelocity().z,"Sprint not doubled");
            p.setSprinting(false); s.activate(ID); p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST,1200,1));
            jump(c,s,p,.62); p.removeStatusEffect(StatusEffects.JUMP_BOOST);
            s.activate("double_challenge:auto_jump,heavy_body"); jump(c,s,p,.42*.6);
            s.activate("double_challenge:auto_jump,gravity_ten"); jump(c,s,p,.42);
            s.activate(ID); p.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH).setBaseValue(0);
            packets(s,p); jump(c,s,p,0); c.assertEquals(0,packets(s,p).size(),"No packet for unsuccessful zero-strength jump");
            p.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH).setBaseValue(.42);
            jump(c,s,p,0);
            s.activate("double_challenge:auto_jump,no_jumping"); jump(c,s,p,0); c.assertTrue(p.isOnGround(),"Cancelled jump stays grounded");
            s.activate("double_challenge:auto_jump,no_sprint"); p.setSprinting(true); c.assertFalse(p.isSprinting(),"No sprint priority"); jump(c,s,p,.42);
            s.activate("no_sprint"); p.setVelocity(.1,-.3,.2); AutoJumpChallenge.tick(server); near(c,-.3,p.getVelocity().y,"Stop preserves flight");
            for(var mode:List.of(GameMode.CREATIVE,GameMode.SPECTATOR,GameMode.ADVENTURE,GameMode.SURVIVAL)) {
                s.activate(ID); p.changeGameMode(mode); jump(c,s,p,mode==GameMode.CREATIVE||mode==GameMode.SPECTATOR?0:.42);
            }
            s.activate(ID); p.getAbilities().flying=true; jump(c,s,p,0); p.getAbilities().flying=false;
            p.startFallFlying(); jump(c,s,p,0); p.stopFallFlying();
            var boat=new BoatEntity(c.getWorld(),base.getX(),base.getY(),base.getZ());
            p.startRiding(boat,true); jump(c,s,p,0); p.stopRiding(); boat.discard();
            ground(p); p.setOnGround(false); AutoJumpChallenge.tick(server); near(c,0,p.getVelocity().y,"Airborne excluded");
            var previous=c.getWorld().getBlockState(base);
            try {
                for(var block:List.of(Blocks.WATER,Blocks.LAVA,Blocks.LADDER)) {
                    c.getWorld().setBlockState(base,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
                    c.getWorld().setBlockState(base,block.getDefaultState(),Block.NOTIFY_LISTENERS);
                    p.refreshPositionAndAngles(base.getX()+.5,base.getY(),base.getZ()+.5,0,0); p.baseTick();
                    c.assertTrue(p.isTouchingWater()||p.isInLava()||p.isClimbing(),"Real excluded block state "+block);
                    s.activate(ID); jump(c,s,p,0);
                }
            } finally { c.getWorld().setBlockState(base,previous,Block.NOTIFY_LISTENERS); p.baseTick(); p.extinguish(); }
            s.activate(ID); p.currentScreenHandler=GenericContainerScreenHandler.createGeneric9x3(8,p.getInventory());
            jump(c,s,p,.42); p.currentScreenHandler=p.playerScreenHandler;
            s.activate(ID); p.setHealth(0); jump(c,s,p,0);
        }
        c.waitAndRun(5,c::complete);
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="auto_jump_collisions", tickLimit=40)
    public void realLowCeilingAndUnchangedFallDamage(TestContext c) throws Exception {
        try(var s=new ChallengeScenario(c)) {
            var p=s.player(UUID.randomUUID(),true); var base=c.getAbsolutePos(new BlockPos(3,7,3));
            var world=c.getWorld(); var old=new LinkedHashMap<BlockPos,BlockState>();
            for(int y=-1;y<=3;y++) { var pos=base.up(y); old.put(pos,world.getBlockState(pos)); }
            try {
                old.keySet().forEach(pos->world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS));
                world.setBlockState(base.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
                world.setBlockState(base.up(2),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
                p.refreshPositionAndAngles(base.getX()+.5,base.getY(),base.getZ()+.5,0,0);
                var compute=LivingEntity.class.getDeclaredMethod("computeFallDamage",float.class,float.class); compute.setAccessible(true);
                int damage=(int)compute.invoke(p,8F,1F);
                s.activate(ID); jump(c,s,p,.42); p.travel(Vec3d.ZERO);
                c.assertTrue(p.getY()-base.getY()<.201,"Actual ceiling collision limits height");
                for(int i=0;i<20;i++) p.travel(Vec3d.ZERO);
                c.assertTrue(p.isOnGround(),"Ordinary physics lands on support");
                jump(c,s,p,0); c.assertEquals(damage,(int)compute.invoke(p,8F,1F),"Fall damage unchanged");
            } finally { old.forEach((pos,state)->world.setBlockState(pos,state,Block.NOTIFY_LISTENERS)); }
        }
        c.waitAndRun(5,c::complete);
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="auto_jump_clock", tickLimit=55)
    public void exactServerTicksManualJumpAndIndependentPlayers(TestContext c) {
        var s=new ChallengeScenario(c); var p=s.player(); var other=s.player(); var server=p.getServer();
        s.activate(ID); p.setOnGround(false); other.setOnGround(false); packets(s,p); packets(s,other);
        int start=server.getTicks(); long time=c.getWorld().getTimeOfDay();
        for(int i=1;i<=24;i++) {
            int tick=i;
            c.waitAndRun(i,()->{
                try {
                    c.assertEquals(tick,server.getTicks()-start,"Real elapsed ticks");
                    ground(p); ground(other);
                    if(tick==13) p.jump();
                    AutoJumpChallenge.tick(server); AutoJumpChallenge.tick(server);
                    boolean one=tick==1||tick==11||tick==13||tick==23, two=tick==1||tick==11||tick==21;
                    near(c,one?.42:0,p.getVelocity().y,"First player tick="+tick);
                    near(c,two?.42:0,other.getVelocity().y,"Independent player tick="+tick);
                    c.assertEquals(one&&tick!=13?1:0,packets(s,p).size(),"Automatic packet count");
                    c.assertEquals(two?1:0,packets(s,other).size(),"Independent packet count");
                    p.setOnGround(false); other.setOnGround(false);
                    if(tick==5) c.getWorld().setTimeOfDay(time+24000);
                    if(tick==24) { c.getWorld().setTimeOfDay(time); s.close(); c.complete(); }
                } catch(Throwable e) { c.getWorld().setTimeOfDay(time); s.close(); throw e; }
            });
        }
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="auto_jump_lifecycle", tickLimit=40)
    public void activationPersistenceAndPlayerLifecycle(TestContext c) throws Exception {
        var challenge=ChallengeRegistry.get(ID);
        try(var s=new ChallengeScenario(c)) {
            var server=c.getWorld().getServer(); var p=s.player(); s.activate(ID); jump(c,s,p,.42);
            challenge.onStart(server,p); challenge.onPlayerJoin(server,p); jump(c,s,p,0);
            s.state.beginActivation(); jump(c,s,p,.42);
            challenge.setEnabled(false); jump(c,s,p,0); challenge.setEnabled(true); jump(c,s,p,.42);
            s.activate("no_sprint"); s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID); jump(c,s,p,.42);
            s.state.getActiveChallengeIds().add(ID); jump(c,s,p,0);
            s.state.getPersonalActiveChallengeIds(p.getUuid()).clear(); s.activate(ID);
            var late=s.player(); jump(c,s,late,.42); var uuid=late.getUuid();
            server.getPlayerManager().remove(late); late=s.player(uuid); jump(c,s,late,.42);
            late.setHealth(0); c.assertFalse(AutoJumpChallenge.active(late),"Dead excluded");
            late=s.respawn(late,false); jump(c,s,late,.42);
            var base=c.getAbsolutePos(new BlockPos(3,7,3));
            late.teleport(server.getWorld(World.NETHER),base.getX(),80,base.getZ(),0,0);
            c.assertTrue(AutoJumpChallenge.duplicateJump(late),"Same-object dimension transfer keeps interval");
            late.teleport(c.getWorld(),base.getX(),base.getY(),base.getZ(),0,0); jump(c,s,late,0);
            var manager=server.getOverworld().getPersistentStateManager(); manager.save();
            var saved=manager.readNbt(CurseCycleState.STATE_KEY,null,SharedConstants.getGameVersion().getSaveVersion().getId());
            var restored=CurseCycleState.createFromNbt(saved.getCompound("data"),c.getWorld().getRegistryManager());
            manager.set(CurseCycleState.STATE_KEY,restored); AutoJumpChallenge.clear(server); jump(c,s,p,.42);
            c.assertFalse(restored.getChallengeData().contains(ID),"No persisted interval");
            server.getPlayerManager().remove(late); CycleManager.activateChallenge(server,restored,"no_sprint");
            late=s.player(uuid); jump(c,s,late,0); c.assertFalse(AutoJumpChallenge.active(late),"Ended offline");
        } finally { challenge.setEnabled(true); AutoJumpChallenge.clear(c.getWorld().getServer()); }
        c.waitAndRun(5,c::complete);
    }
}
