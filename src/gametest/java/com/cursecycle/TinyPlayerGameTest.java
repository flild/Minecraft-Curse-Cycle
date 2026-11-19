package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.TinyPlayerChallenge;
import com.cursecycle.state.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

public class TinyPlayerGameTest implements FabricGameTest {
    private static final String ID = TinyPlayerChallenge.ID;
    private static final Identifier FOREIGN = Identifier.of("test", "tiny_foreign");
    private static void near(TestContext c, double expected, double actual, String message) {
        c.assertTrue(Math.abs(expected - actual) < .00001, message + ": " + expected + " != " + actual);
    }
    private static void position(ServerPlayerEntity p, BlockPos b) {
        p.teleport(p.getServerWorld(), b.getX() + .5, b.getY(), b.getZ() + .5, 0, 0);
        p.setPose(EntityPose.STANDING); p.calculateDimensions(); p.setVelocity(Vec3d.ZERO);
    }
    private static void size(TestContext c, ServerPlayerEntity p, double height) {
        near(c, height, p.getHeight(), "Cached height");
        near(c, height, p.getBoundingBox().getLengthY(), "Real collision height");
        near(c, p.getDimensions(p.getPose()).eyeHeight(), p.getStandingEyeHeight(), "Eye follows pose and scale");
    }
    private static final class Room implements AutoCloseable {
        final TestContext c; final BlockPos base; final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        Room(TestContext c) {
            this.c=c; base=c.getAbsolutePos(new BlockPos(2, 8, 2));
            for (int x=-2;x<=7;x++) for(int z=-2;z<=2;z++) for(int y=-1;y<=4;y++) {
                var pos=base.add(x,y,z); original.put(pos,c.getWorld().getBlockState(pos));
                c.getWorld().setBlockState(pos,(y==-1?Blocks.STONE:Blocks.AIR).getDefaultState(),Block.NOTIFY_LISTENERS);
            }
        }
        void ceiling(int height) {
            for(int x=-1;x<=4;x++) for(int z=-1;z<=1;z++) c.getWorld().setBlockState(base.add(x,height,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
        }
        public void close() { original.forEach((p,b)->c.getWorld().setBlockState(p,b,Block.NOTIFY_LISTENERS)); }
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="tiny_geometry")
    public void realOneBlockTunnelStandingCrouchingSwimmingAndBoat(TestContext c) throws Exception {
        try(var room=new Room(c); var s=new ChallengeScenario(c)) {
            var p=s.player(UUID.randomUUID(),true); position(p,room.base); room.ceiling(1);
            c.assertFalse(c.getWorld().isSpaceEmpty(p,p.getBoundingBox()),"Normal standing body cannot fit");
            s.activate(ID); size(c,p,1);
            near(c,1.0/3,p.getWidth(),"Proportional width from actual 1.8 standing height");
            c.assertTrue(c.getWorld().isSpaceEmpty(p,p.getBoundingBox().contract(1e-7)),"Tiny standing body fits exactly one block");
            double start=p.getX(); p.move(MovementType.SELF,new Vec3d(3,0,0));
            near(c,start+3,p.getX(),"Actual collision movement through one-block tunnel");
            for(var pose:List.of(EntityPose.STANDING,EntityPose.CROUCHING,EntityPose.SWIMMING,EntityPose.FALL_FLYING,EntityPose.SLEEPING)) {
                p.setPose(pose); p.calculateDimensions();
                near(c,p.getDimensions(pose).height(),p.getBoundingBox().getLengthY(),"Pose collision "+pose);
                near(c,pose==EntityPose.SLEEPING?.2:p.getBaseDimensions(pose).height()*TinyPlayerChallenge.scaleFactor(p),p.getHeight(),"Vanilla scaled pose "+pose);
            }
            var boat=new BoatEntity(c.getWorld(),p.getX(),p.getY(),p.getZ());
            try { p.startRiding(boat,true); p.playerTick(); near(c,TinyPlayerChallenge.scaleFactor(p),p.getScale(),"Rider keeps small body, vehicle is vanilla"); }
            finally {p.stopRiding();boat.discard();}
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="tiny_jump")
    public void actualJumpAndApexWithPhysicsPairs(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player(UUID.randomUUID(),true); position(p,room.base); p.setOnGround(true); p.jump();
            near(c,.42,p.getVelocity().y,"Control jump");
            s.activate(ID); position(p,room.base); p.setOnGround(true); p.jump(); near(c,.42*.7,p.getVelocity().y,"Real lower impulse");
            double floor=p.getY(), max=floor;
            for(int i=0;i<20;i++){p.travel(Vec3d.ZERO);max=Math.max(max,p.getY());}
            c.assertTrue(max-floor>.4 && max-floor<.7,"Actual jump apex is lower than one block: "+(max-floor));
            for(var pair:List.of("low_gravity","heavy_body","no_jumping","auto_jump")) {
                s.activate("double_challenge:tiny_player,"+pair); position(p,room.base);p.setOnGround(true);
                if(pair.equals("auto_jump")) com.cursecycle.challenge.impl.AutoJumpChallenge.tick(p.getServer()); else p.jump();
                near(c,pair.equals("no_jumping")?0:.42*.7*(pair.equals("low_gravity")?1.25:pair.equals("heavy_body")?.6:1),p.getVelocity().y,"Pair "+pair);
            }
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="tiny_reach")
    public void serverReachAndRealNetworkEntityAttack(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();position(p,room.base); s.activate(ID);
            var teleportField=p.networkHandler.getClass().getDeclaredField("requestedTeleportId");teleportField.setAccessible(true);
            p.networkHandler.onTeleportConfirm(new TeleportConfirmC2SPacket(teleportField.getInt(p.networkHandler)));
            near(c,2.7,p.getBlockInteractionRange(),"Block range");near(c,1.8,p.getEntityInteractionRange(),"Entity range");
            c.assertTrue(p.canInteractWithBlockAt(room.base.add(2,0,0),0),"Close block allowed");
            c.assertFalse(p.canInteractWithBlockAt(room.base.add(4,0,0),0),"Distant block denied by server API");
            var pig=EntityType.PIG.create(c.getWorld()); pig.setAiDisabled(true); pig.refreshPositionAndAngles(p.getX()+3.6,p.getY(),p.getZ(),0,0);
            c.getWorld().spawnEntity(pig);
            try {
                float hp=pig.getHealth(); p.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.attack(pig,false));
                near(c,hp,pig.getHealth(),"Forged far entity attack rejected by actual network handler incl vanilla tolerance");
                s.activate("test_easy");
                p.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.attack(pig,false));
                c.assertTrue(pig.getHealth()<hp,"Same distant entity attack works with normal reach");
                pig.setHealth(hp);pig.timeUntilRegen=0;s.activate(ID);
                pig.setPosition(p.getPos().add(1,0,0));
                p.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.attack(pig,false));
                c.assertTrue(pig.getHealth()<hp,"Near entity attack succeeds through network handler");
            } finally {pig.discard();}
            var target=room.base.add(5,0,0); c.getWorld().setBlockState(target,Blocks.LEVER.getDefaultState(),Block.NOTIFY_LISTENERS);
            p.networkHandler.onPlayerInteractBlock(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(target),Direction.UP,target,false),0));
            c.assertFalse(c.getWorld().getBlockState(target).get(LeverBlock.POWERED),"Forged far block use rejected by network handler");
            s.activate("test_easy");
            p.networkHandler.onPlayerInteractBlock(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(target),Direction.UP,target,false),1));
            c.assertTrue(c.getWorld().getBlockState(target).get(LeverBlock.POWERED),"Same distant packet works with normal reach");
            c.getWorld().setBlockState(target,Blocks.LEVER.getDefaultState(),Block.NOTIFY_LISTENERS);s.activate(ID);
            position(p,room.base.add(4,0,0));
            // Flush the outstanding teleport with its actual protocol id.
            var field=p.networkHandler.getClass().getDeclaredField("requestedTeleportId");field.setAccessible(true);
            p.networkHandler.onTeleportConfirm(new TeleportConfirmC2SPacket(field.getInt(p.networkHandler)));
            p.networkHandler.onPlayerInteractBlock(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(target),Direction.UP,target,false),2));
            c.assertTrue(c.getWorld().getBlockState(target).get(LeverBlock.POWERED),"Near block use accepted by network handler");
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="tiny_foreign")
    public void foreignAttributesIdempotenceAndTemporaryNbt(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();position(p,room.base);
            var attributes=List.of(EntityAttributes.GENERIC_SCALE,EntityAttributes.GENERIC_JUMP_STRENGTH,EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE,EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
            var values=new ArrayList<Double>();
            for(var key:attributes) {var a=p.getAttributeInstance(key);a.setBaseValue(a.getBaseValue()*.9);a.addPersistentModifier(new EntityAttributeModifier(FOREIGN,.2,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));values.add(a.getValue());}
            s.activate(ID);
            for(int i=0;i<20;i++)p.playerTick();
            for(int i=0;i<attributes.size();i++) near(c,values.get(i)*(i==0?TinyPlayerChallenge.scaleFactor(p):i==1?.7:.6),p.getAttributeValue(attributes.get(i)),"Foreign modifiers compose once "+i);
            var nbt=new NbtCompound();p.writeNbt(nbt);c.assertFalse(nbt.toString().contains("tiny_player/"),"Own temporary modifiers never saved");c.assertTrue(nbt.toString().contains("tiny_foreign"),"Foreign persistent modifiers saved");
            s.activate("test_easy"); for(int i=0;i<attributes.size();i++)near(c,values.get(i),p.getAttributeValue(attributes.get(i)),"Foreign values restored "+i);
            var stateNbt=s.state.writeNbt(new NbtCompound(),c.getWorld().getRegistryManager());c.assertFalse(stateNbt.getCompound("challengeData").contains(ID),"No saved challenge size/cache");
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="tiny_lifecycle")
    public void personalGlobalModesDisableAndReset(TestContext c) throws Exception {
        var rule=ChallengeRegistry.get(ID);
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();var other=s.player();position(p,room.base);position(other,room.base.add(5,0,0));
            s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID); p.playerTick();other.playerTick();size(c,p,1);size(c,other,1.8);
            s.activate(ID);p.playerTick();other.playerTick();size(c,p,1);size(c,other,1);
            for(var mode:List.of(GameMode.CREATIVE,GameMode.SPECTATOR,GameMode.ADVENTURE,GameMode.SURVIVAL)) {
                p.changeGameMode(mode);p.playerTick();size(c,p,mode==GameMode.CREATIVE||mode==GameMode.SPECTATOR?1.8:1);
            }
            position(p,room.base);rule.setEnabled(false);p.playerTick();size(c,p,1.8);rule.setEnabled(true);p.playerTick();size(c,p,1);
            s.state.getPersonalActiveChallengeIds(p.getUuid()).clear();s.activate("test_easy");size(c,p,1.8);
            s.activate(ID);c.getWorld().getServer().getCommandManager().executeWithPrefix(c.getWorld().getServer().getCommandSource(),"fdc reset");p.playerTick();size(c,p,1.8);
        } finally {rule.setEnabled(true);}
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="tiny_restore")
    public void safeCrawlOnStopAndStandingAfterLeavingCeiling(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();position(p,room.base);s.activate(ID);room.ceiling(1);s.activate("test_easy");
            near(c,1,p.getScale(),"Own scale removed under ceiling");c.assertTrue(p.getPose()==EntityPose.SWIMMING,"Restored player crawls safely");
            c.assertTrue(c.getWorld().isSpaceEmpty(p,p.getBoundingBox().contract(1e-7)),"No suffocation on restoration");
            p.playerTick();c.assertTrue(p.getPose()==EntityPose.SWIMMING,"Vanilla maintains crawl under roof");
            position(p,room.base.add(6,0,0));p.playerTick();size(c,p,1.8);c.assertTrue(p.getPose()==EntityPose.STANDING,"Stand after exiting");
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="tiny_narrow")
    public void impossibleGrowthDefersOnlyScaleUntilSpaceIsAvailable(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();position(p,room.base);s.activate(ID);
            // A .375-wide gap between a full wall and the centre fence post fits only the tiny body.
            c.getWorld().setBlockState(room.base.west(),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            c.getWorld().setBlockState(room.base,Blocks.OAK_FENCE.getDefaultState(),Block.NOTIFY_LISTENERS);
            p.setPosition(room.base.getX()+.1875,p.getY(),room.base.getZ()+.5);
            c.assertTrue(c.getWorld().isSpaceEmpty(p,p.getBoundingBox().contract(1e-7)),"Tiny body fits fixture slot");
            s.activate("test_easy");p.playerTick();
            c.assertTrue(p.getAttributeInstance(EntityAttributes.GENERIC_SCALE).hasModifier(TinyPlayerChallenge.SCALE_MODIFIER),"Unsafe growth waits");
            near(c,.42,p.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH),"Jump released immediately");near(c,4.5,p.getBlockInteractionRange(),"Reach released immediately");
            position(p,room.base.add(6,0,0));p.playerTick();size(c,p,1.8);c.assertFalse(p.getAttributeInstance(EntityAttributes.GENERIC_SCALE).hasModifier(TinyPlayerChallenge.SCALE_MODIFIER),"Ordinary inactive tick releases pending size");
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="tiny_reload")
    public void deathRespawnDisconnectDimensionAndStateNbt(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();position(p,room.base);var uuid=p.getUuid();s.activate(ID);
            p.setHealth(0);p.playerTick();near(c,1,p.getScale(),"Dead entity releases size");p=s.respawn(p,false);size(c,p,1);
            p.teleport(p.getServer().getWorld(World.NETHER),0,100,0,0,0);p.playerTick();size(c,p,1);p.teleport(c.getWorld(),room.base.getX()+.5,room.base.getY(),room.base.getZ()+.5,0,0);
            var manager=p.getServer().getOverworld().getPersistentStateManager();var restored=CurseCycleState.createFromNbt(s.state.writeNbt(new NbtCompound(),c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());manager.set(CurseCycleState.STATE_KEY,restored);p.playerTick();size(c,p,1);
            restored.beginActivation();restored.getActiveChallengeIds().clear();restored.getActiveChallengeIds().add(ID);p.playerTick();size(c,p,1);
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler,p.getServer());p.getServer().getPlayerManager().remove(p);
            p=s.player(uuid);p.playerTick();size(c,p,1);
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler,p.getServer());p.getServer().getPlayerManager().remove(p);CycleManager.activateChallenge(c.getWorld().getServer(),restored,"test_easy");p=s.player(uuid);p.playerTick();size(c,p,1.8);
            manager.set(CurseCycleState.STATE_KEY,s.state);
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="tiny_sync")
    public void vanillaTrackedPacketContainsAllFourModifiers(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();position(p,room.base);s.activate(ID);
            var packet=new EntityAttributesS2CPacket(p.getId(),p.getAttributes().getAttributesToSend());
            var buf=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),p.getRegistryManager());
            try {
                EntityAttributesS2CPacket.CODEC.encode(buf,packet);var decoded=EntityAttributesS2CPacket.CODEC.decode(buf);
                for(var id:List.of(TinyPlayerChallenge.SCALE_MODIFIER,TinyPlayerChallenge.JUMP_MODIFIER,TinyPlayerChallenge.BLOCK_MODIFIER,TinyPlayerChallenge.ENTITY_MODIFIER)) c.assertTrue(decoded.getEntries().stream().anyMatch(e->e.attribute().value().isTracked()&&e.modifiers().stream().anyMatch(m->m.id().equals(id))),"Tracked wire modifier "+id);
            } finally {buf.release();}
        }
        c.complete();
    }
}
