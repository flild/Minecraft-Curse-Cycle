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
    private static List<com.cursecycle.network.CurseCycleSyncPayload> syncs(ChallengeScenario s, ServerPlayerEntity p) {
        var channel = s.channel(p); channel.runPendingTasks(); channel.flush();
        var found = new ArrayList<com.cursecycle.network.CurseCycleSyncPayload>(); Object packet;
        while ((packet = channel.readOutbound()) != null)
            if (packet instanceof net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket custom
                    && custom.payload() instanceof com.cursecycle.network.CurseCycleSyncPayload sync) found.add(sync);
        return found;
    }
    private static void disconnect(ServerPlayerEntity p) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                .onPlayDisconnect(p.networkHandler, p.getServer());
        p.getServer().getPlayerManager().remove(p);
    }
    private static CurseCycleState reload(TestContext c) throws Exception {
        var manager = c.getWorld().getServer().getOverworld().getPersistentStateManager();
        CurseCycleState.getServerState(c.getWorld().getServer()).markDirty(); manager.save();
        var disk = manager.readNbt(CurseCycleState.STATE_KEY, null, SharedConstants.getGameVersion().getSaveVersion().getId());
        var loaded = CurseCycleState.createFromNbt(disk.getCompound("data"), c.getWorld().getRegistryManager());
        manager.set(CurseCycleState.STATE_KEY, loaded); return loaded;
    }
    private static void foreign(TestContext c, ServerPlayerEntity p, net.minecraft.item.ItemStack cargo) {
        c.assertTrue(net.minecraft.item.ItemStack.areEqual(cargo, p.getInventory().getStack(8)), "Foreign named items");
        near(c, 7, p.experienceLevel, "Foreign XP"); near(c, .625, p.experienceProgress, "Foreign XP progress");
        near(c, 17, p.getHealth(), "Foreign HP");
        c.assertTrue(p.hasStatusEffect(StatusEffects.FIRE_RESISTANCE), "Foreign effect");
        near(c, .04, p.getAttributeValue(EntityAttributes.GENERIC_GRAVITY), "Foreign gravity attribute");
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gravity_ten_disk")
    public void scopedSyncRepeatedActivationAndDiskKeepForeignData(TestContext c) throws Exception {
        var server = c.getWorld().getServer(); var challenge = ChallengeRegistry.get(ID);
        try (var s = new ChallengeScenario(c)) {
            var p = s.player(UUID.randomUUID(), true); var other = s.player(UUID.randomUUID(), true);
            var base = c.getAbsolutePos(new BlockPos(3, 12, 3)); var uuid = p.getUuid();
            s.state.getPersonalActiveChallengeIds(uuid).add(ID); s.state.markDirty();
            syncs(s, p); syncs(s, other);
            com.cursecycle.network.NetworkHandler.syncToAll(server, 1, 20, s.state.getActiveChallengeIds(), "", List.of());
            var own = syncs(s, p); var unrelated = syncs(s, other);
            c.assertEquals(1, own.size(), "One actual S2C for owner");
            c.assertTrue(own.getFirst().activeChallengeIds().contains(ID), "PERSONAL owner receives gravity");
            c.assertFalse(unrelated.getFirst().activeChallengeIds().contains(ID), "Other recipient has no gravity");
            var buf = new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), p.getRegistryManager());
            try {
                com.cursecycle.network.CurseCycleSyncPayload.CODEC.encode(buf, own.getFirst());
                c.assertEquals(own.getFirst().activeChallengeIds(), com.cursecycle.network.CurseCycleSyncPayload.CODEC.decode(buf).activeChallengeIds(), "Sync codec");
                c.assertEquals(0, buf.readableBytes(), "Codec consumes bytes");
            } finally { buf.release(); }
            near(c, -.24*DRAG, step(p, base, Vec3d.ZERO), "PERSONAL physics");
            near(c, -.08*DRAG, step(other, base.add(4,0,0), Vec3d.ZERO), "Other player unaffected");
            challenge.setEnabled(false);
            com.cursecycle.network.NetworkHandler.syncToAll(server, 1, 20, s.state.getActiveChallengeIds(), "", List.of());
            c.assertFalse(syncs(s, p).getFirst().activeChallengeIds().contains(ID), "Disabled absent from S2C");
            near(c, -.08*DRAG, step(p, base, Vec3d.ZERO), "Disabled physics"); challenge.setEnabled(true);
            s.state.getPersonalActiveChallengeIds(uuid).clear();
            var cargo = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 17);
            cargo.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("foreign-gravity-review"));
            p.getInventory().clear(); p.getInventory().setStack(8, cargo.copy()); p.experienceLevel=7; p.experienceProgress=.625F;
            p.setHealth(17); p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 1200, 0));
            p.getAttributeInstance(EntityAttributes.GENERIC_GRAVITY).setBaseValue(.04);
            for (int cycle=0; cycle<32; cycle++) {
                long serial=s.state.getActivationSerial(); s.activate("double_challenge:gravity_ten,gravity_ten");
                near(c, serial+1, s.state.getActivationSerial(), "One serial per activation");
                c.assertEquals(List.of(ID), s.state.getActiveChallengeIds(), "Duplicate ID deduplicated");
                challenge.onStart(server,p); challenge.onPlayerJoin(server,p);
                near(c, -.12*DRAG, step(p,base,Vec3d.ZERO), "Repeated start multiplies once");
                s.activate("double_challenge:gravity_ten,no_sprint"); p.setSprinting(true); c.assertFalse(p.isSprinting(), "Both active");
                p.fallDistance=12; p.setVelocity(.2,-.7,.3); s.activate("no_sprint");
                near(c,12,p.fallDistance,"End retains fallDistance"); near(c,-.7,p.getVelocity().y,"End retains velocity");
                near(c, -.04*DRAG, step(p,base,Vec3d.ZERO), "End retains external gravity");
                p.setSprinting(true); c.assertFalse(p.isSprinting(), "End retains no_sprint"); foreign(c,p,cargo);
            }
            s.activate("double_challenge:gravity_ten,no_sprint");
            s.state.getChallengeData().putString("foreign-gravity", "preserve"); s.state.markDirty();
            disconnect(other); disconnect(p); var loaded=reload(c);
            c.assertFalse(loaded.getChallengeData().contains(ID), "No own persistent physics state");
            c.assertEquals("preserve", loaded.getChallengeData().getString("foreign-gravity"), "Foreign world data");
            p=s.player(uuid,true); foreign(c,p,cargo);
            near(c,-.12*DRAG,step(p,base,Vec3d.ZERO),"Active playerdata and state reload");
            p.teleport(server.getWorld(World.NETHER),base.getX(),90,base.getZ(),0,0);
            near(c,-.12*DRAG,step(p,new BlockPos(base.getX(),90,base.getZ()),Vec3d.ZERO),"Nether physics");
            p.teleport(server.getWorld(World.END),base.getX(),90,base.getZ(),0,0);
            near(c,-.12*DRAG,step(p,new BlockPos(base.getX(),90,base.getZ()),Vec3d.ZERO),"End physics");
            p.teleport(c.getWorld(),base.getX(),base.getY(),base.getZ(),0,0);
            disconnect(p); CycleManager.activateChallenge(server,loaded,"no_sprint"); reload(c);
            p=s.player(uuid,true); foreign(c,p,cargo);
            near(c,-.04*DRAG,step(p,base,Vec3d.ZERO),"Offline end survives disk"); disconnect(p);
            loaded=reload(c); CycleManager.activateChallenge(server,loaded,ID); reload(c);
            CycleManager.activateChallenge(server,CurseCycleState.getServerState(server),"no_sprint"); reload(c);
            p=s.player(uuid,true); foreign(c,p,cargo);
            near(c,-.04*DRAG,step(p,base,Vec3d.ZERO),"Empty server active/end has no replay");
            p.setSprinting(true); c.assertFalse(p.isSprinting(),"Disk end keeps companion");
        } finally { challenge.setEnabled(true); }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gravity_ten_pairs")
    public void automaticAndHeavyJumpsAndFallModifiersCompose(TestContext c) throws Exception {
        var server=c.getWorld().getServer();
        try(var s=new ChallengeScenario(c)) {
            var p=s.player(UUID.randomUUID(),true); var base=c.getAbsolutePos(new BlockPos(3,12,3));
            s.activate("double_challenge:gravity_ten,auto_jump"); reset(p,base,Vec3d.ZERO); p.setOnGround(true);
            com.cursecycle.challenge.impl.AutoJumpChallenge.tick(server);
            near(c,.42,p.getVelocity().y,"Automatic ordinary impulse unchanged");
            near(c,(.42-.24)*DRAG,step(p,base,p.getVelocity()),"Auto jump then gravity once");
            s.activate("auto_jump"); reset(p,base,Vec3d.ZERO); p.setOnGround(true);
            com.cursecycle.challenge.impl.AutoJumpChallenge.tick(server); near(c,.42,p.getVelocity().y,"Ending gravity keeps auto jump");
            near(c,(.42-.08)*DRAG,step(p,base,p.getVelocity()),"Ending gravity restores next acceleration");
            s.activate("double_challenge:gravity_ten,auto_jump"); s.activate(ID);
            reset(p,base,Vec3d.ZERO); p.setOnGround(true); com.cursecycle.challenge.impl.AutoJumpChallenge.tick(server);
            near(c,0,p.getVelocity().y,"Ending auto removes automatic jump");
            near(c,-.24*DRAG,step(p,base,Vec3d.ZERO),"Ending auto retains gravity");
            s.activate("double_challenge:gravity_ten,heavy_body"); s.activate("heavy_body");
            reset(p,base,Vec3d.ZERO); p.setOnGround(true); p.jump(); near(c,.42*.6,p.getVelocity().y,"Ending gravity retains heavy jump");
            near(c,(.42*.6-.08)*DRAG,step(p,base,p.getVelocity()),"Heavy only travel");
            s.activate("double_challenge:gravity_ten,heavy_body"); s.activate(ID);
            reset(p,base,Vec3d.ZERO); p.setOnGround(true); p.jump(); near(c,.42,p.getVelocity().y,"Ending heavy restores impulse");
            near(c,(.42-.24)*DRAG,step(p,base,p.getVelocity()),"Ending heavy retains gravity");
            var compute=LivingEntity.class.getDeclaredMethod("computeFallDamage",float.class,float.class); compute.setAccessible(true);
            for(double safe: new double[]{3,5}) for(double attribute: new double[]{.5,1,1.5}) {
                p.getAttributeInstance(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE).setBaseValue(safe);
                p.getAttributeInstance(EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER).setBaseValue(attribute);
                for(float distance:new float[]{2,4.1F,7,12}) for(float block:new float[]{0,.2F,.5F,1}) {
                    s.activate("test_easy"); int ordinary=(int)compute.invoke(p,distance,block);
                    s.activate(ID); near(c,ordinary>0?ordinary*3:ordinary,(int)compute.invoke(p,distance,block),"Vanilla rounded safe/attribute/block result");
                }
            }
            p.getAttributeInstance(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE).setBaseValue(3);
            p.getAttributeInstance(EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER).setBaseValue(1);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST,1200,1));
            near(c,3,(int)compute.invoke(p,6F,1F),"Jump Boost safe distance before triple");
            p.removeStatusEffect(StatusEffects.JUMP_BOOST);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING,1200,0));
            // Slow Falling prevents accumulation in travel; computeFallDamage itself accepts the supplied distance.
            s.activate("test_easy"); int slowControl=(int)compute.invoke(p,12F,1F);
            s.activate(ID); near(c,slowControl*3,(int)compute.invoke(p,12F,1F),"Slow Falling retains vanilla supplied-distance calculation");
        } finally { com.cursecycle.challenge.impl.AutoJumpChallenge.clear(server); com.cursecycle.challenge.impl.HeavyBodyChallenge.clear(server); }
        c.complete();
    }
}
