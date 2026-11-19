package com.cursecycle;

import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.GiantPlayerChallenge;
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

public class GiantPlayerGameTest implements FabricGameTest {
    private static final String ID = GiantPlayerChallenge.ID;
    private static final Identifier FOREIGN = Identifier.of("test", "giant_foreign");
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
            for (int x=-2;x<=10;x++) for(int z=-2;z<=2;z++) for(int y=-1;y<=4;y++) {
                var pos=base.add(x,y,z); original.put(pos,c.getWorld().getBlockState(pos));
                c.getWorld().setBlockState(pos,(y==-1?Blocks.STONE:Blocks.AIR).getDefaultState(),Block.NOTIFY_LISTENERS);
            }
        }
        void ceiling(int height) {
            for(int x=-1;x<=4;x++) for(int z=-1;z<=1;z++) c.getWorld().setBlockState(base.add(x,height,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
        }
        public void close() { original.forEach((p,b)->c.getWorld().setBlockState(p,b,Block.NOTIFY_LISTENERS)); }
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="giant_geometry")
    public void realDoorAndOneBlockCorridorRejectGiantAndRestore(TestContext c) throws Exception {
        try(var room=new Room(c); var s=new ChallengeScenario(c)) {
            var p=s.player(UUID.randomUUID(),true);position(p,room.base);s.activate(ID);
            size(c,p,3.6);near(c,1.2,p.getWidth(),"Double width");
            for(int y=0;y<4;y++)for(int z=-2;z<=2;z++)if(z!=0)c.getWorld().setBlockState(room.base.add(3,y,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            // Open door in a one-block opening: normal players pass, giants cannot even crawl through.
            c.getWorld().setBlockState(room.base.add(3,0,0),Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.OPEN,true).with(DoorBlock.FACING,Direction.EAST),Block.NOTIFY_LISTENERS);
            c.getWorld().setBlockState(room.base.add(3,1,0),Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.OPEN,true).with(DoorBlock.FACING,Direction.EAST).with(DoorBlock.HALF,net.minecraft.block.enums.DoubleBlockHalf.UPPER),Block.NOTIFY_LISTENERS);
            double start=p.getX();p.move(MovementType.SELF,new Vec3d(5,0,0));
            c.assertTrue(p.getX()<start+3,"Giant blocked by open ordinary door");
            position(p,room.base);p.setPose(EntityPose.SWIMMING);p.calculateDimensions();p.move(MovementType.SELF,new Vec3d(5,0,0));
            c.assertTrue(p.getX()<start+3,"Crawling does not bypass giant width");
            s.activate("test_easy");position(p,room.base);p.move(MovementType.SELF,new Vec3d(5,0,0));near(c,start+5,p.getX(),"Normal body passes same open door");
            c.getWorld().setBlockState(room.base.add(3,0,0),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
            c.getWorld().setBlockState(room.base.add(3,1,0),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
            position(p,room.base);s.activate(ID);p.move(MovementType.SELF,new Vec3d(5,0,0));c.assertTrue(p.getX()<start+3,"Doorless one-block corridor also blocks giant");
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="giant_step")
    public void realStepUpAndUnchangedJumpHealthSpeed(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player(UUID.randomUUID(),true);position(p,room.base);
            double hp=p.getMaxHealth(),speed=p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),attack=p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            for(int x=2;x<=6;x++)for(int z=-2;z<=2;z++)c.getWorld().setBlockState(room.base.add(x,0,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            p.setOnGround(true);p.move(MovementType.SELF,new Vec3d(3,0,0));near(c,room.base.getY(),p.getY(),"Normal .6 step cannot climb full block");
            position(p,room.base);s.activate(ID);near(c,1.2,p.getStepHeight(),"Double step");p.setOnGround(true);p.move(MovementType.SELF,new Vec3d(3,0,0));near(c,room.base.getY()+1,p.getY(),"Actual collision step climbs one block");
            p.setVelocity(Vec3d.ZERO);p.setOnGround(true);p.jump();near(c,.42,p.getVelocity().y,"No higher jump");near(c,hp,p.getMaxHealth(),"Health unchanged");near(c,speed,p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),"Speed unchanged");near(c,attack,p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE),"Attack unchanged");
            s.activate("test_easy");near(c,.6,p.getStepHeight(),"Step restored");
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="giant_reach")
    public void actualServerPacketsUseIncreasedReachAndRejectBeyondIt(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();position(p,room.base);s.activate(ID);
            var field=p.networkHandler.getClass().getDeclaredField("requestedTeleportId");field.setAccessible(true);p.networkHandler.onTeleportConfirm(new TeleportConfirmC2SPacket(field.getInt(p.networkHandler)));
            near(c,6.75,p.getBlockInteractionRange(),"Block reach");near(c,4.5,p.getEntityInteractionRange(),"Entity reach");
            var pig=EntityType.PIG.create(c.getWorld());pig.setAiDisabled(true);pig.refreshPositionAndAngles(p.getX()+5,p.getY(),p.getZ(),0,0);c.getWorld().spawnEntity(pig);
            try {
                float hp=pig.getHealth();p.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.attack(pig,false));c.assertTrue(pig.getHealth()<hp,"Giant distant C2S attack accepted");
                pig.setHealth(hp);pig.timeUntilRegen=0;s.activate("test_easy");p.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.attack(pig,false));near(c,hp,pig.getHealth(),"Same packet rejected at normal reach");
                s.activate(ID);pig.setPosition(p.getPos().add(7,0,0));p.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.attack(pig,false));near(c,hp,pig.getHealth(),"Beyond giant range plus vanilla tolerance rejected");
            }finally{pig.discard();}
            var target=room.base.add(7,2,0);c.getWorld().setBlockState(target,Blocks.LEVER.getDefaultState(),Block.NOTIFY_LISTENERS);
            p.networkHandler.onPlayerInteractBlock(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(target),Direction.UP,target,false),0));c.assertTrue(c.getWorld().getBlockState(target).get(LeverBlock.POWERED),"Giant distant block use accepted");
            c.getWorld().setBlockState(target,Blocks.LEVER.getDefaultState(),Block.NOTIFY_LISTENERS);s.activate("test_easy");p.networkHandler.onPlayerInteractBlock(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(target),Direction.UP,target,false),1));c.assertFalse(c.getWorld().getBlockState(target).get(LeverBlock.POWERED),"Same block packet rejected at normal reach");
            s.activate(ID);var far=room.base.add(9,2,0);c.getWorld().setBlockState(far,Blocks.LEVER.getDefaultState(),Block.NOTIFY_LISTENERS);p.networkHandler.onPlayerInteractBlock(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(far),Direction.UP,far,false),2));c.assertFalse(c.getWorld().getBlockState(far).get(LeverBlock.POWERED),"Beyond giant block reach rejected");
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="giant_foreign")
    public void foreignAttributesIdempotenceAndTemporaryNbt(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();position(p,room.base);
            var attributes=List.of(EntityAttributes.GENERIC_SCALE,EntityAttributes.GENERIC_STEP_HEIGHT,EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE,EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
            var values=new ArrayList<Double>();
            for(var key:attributes) {var a=p.getAttributeInstance(key);a.setBaseValue(a.getBaseValue()*.9);a.addPersistentModifier(new EntityAttributeModifier(FOREIGN,.2,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));values.add(a.getValue());}
            s.activate(ID);
            for(int i=0;i<20;i++)p.playerTick();
            for(int i=0;i<attributes.size();i++) near(c,values.get(i)*(i==0?GiantPlayerChallenge.SCALE:i==1?2:1.5),p.getAttributeValue(attributes.get(i)),"Foreign modifiers compose once "+i);
            var nbt=new NbtCompound();p.writeNbt(nbt);c.assertFalse(nbt.toString().contains("giant_player/"),"Own temporary modifiers never saved");c.assertTrue(nbt.toString().contains("giant_foreign"),"Foreign persistent modifiers saved");
            s.activate("test_easy"); for(int i=0;i<attributes.size();i++)near(c,values.get(i),p.getAttributeValue(attributes.get(i)),"Foreign values restored "+i);
            var stateNbt=s.state.writeNbt(new NbtCompound(),c.getWorld().getRegistryManager());c.assertFalse(stateNbt.getCompound("challengeData").contains(ID),"No saved challenge size/cache");
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="giant_lifecycle")
    public void personalGlobalModesDisableAndReset(TestContext c) throws Exception {
        var rule=ChallengeRegistry.get(ID);
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();var other=s.player();position(p,room.base);position(other,room.base.add(5,0,0));
            s.state.getPersonalActiveChallengeIds(p.getUuid()).add(ID); p.playerTick();other.playerTick();size(c,p,3.6);size(c,other,1.8);
            s.activate(ID);p.playerTick();other.playerTick();size(c,p,3.6);size(c,other,3.6);
            for(var mode:List.of(GameMode.CREATIVE,GameMode.SPECTATOR,GameMode.ADVENTURE,GameMode.SURVIVAL)) {
                p.changeGameMode(mode);position(p,room.base);p.playerTick();size(c,p,mode==GameMode.CREATIVE||mode==GameMode.SPECTATOR?1.8:3.6);
            }
            position(p,room.base);rule.setEnabled(false);p.playerTick();size(c,p,1.8);position(p,room.base);rule.setEnabled(true);p.playerTick();size(c,p,3.6);
            s.state.getPersonalActiveChallengeIds(p.getUuid()).clear();s.activate("test_easy");size(c,p,1.8);
            s.activate(ID);c.getWorld().getServer().getCommandManager().executeWithPrefix(c.getWorld().getServer().getCommandSource(),"fdc reset");p.playerTick();size(c,p,1.8);
        } finally {rule.setEnabled(true);}
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="giant_growth")
    public void safeGrowthWaitsInCorridorThenResizesAndCrawlsUnderRoof(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();position(p,room.base);
            for(int x=-1;x<=2;x++)for(int y=0;y<=3;y++)for(int z:new int[]{-1,1})c.getWorld().setBlockState(room.base.add(x,y,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            var initial=p.getPos();s.activate(ID);p.playerTick();size(c,p,1.8);near(c,4.5,p.getBlockInteractionRange(),"Pending giant grants no reach");near(c,.6,p.getStepHeight(),"Pending giant grants no step");c.assertTrue(p.getPos().equals(initial),"No relocation through walls");
            position(p,room.base.add(5,0,0));p.playerTick();size(c,p,3.6);c.assertTrue(c.getWorld().isSpaceEmpty(p,p.getBoundingBox().contract(1e-7)),"Safe delayed growth");
            s.activate("test_easy");position(p,room.base.add(5,0,0));
            for(int x=4;x<=10;x++)for(int z=-1;z<=1;z++)c.getWorld().setBlockState(room.base.add(x,2,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            s.activate(ID);near(c,2,p.getScale(),"Grows safely in crawl pose below roof");c.assertTrue(p.getPose()==EntityPose.SWIMMING,"Safe giant crawl");c.assertTrue(c.getWorld().isSpaceEmpty(p,p.getBoundingBox().contract(1e-7)),"No overlap at start");
            s.activate("test_easy");p.playerTick();size(c,p,1.8);
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="giant_pair")
    public void tinyPriorityInBothOrdersTransitionsAndBoat(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();position(p,room.base);
            for(var pair:List.of("double_challenge:tiny_player,giant_player","double_challenge:giant_player,tiny_player")) {
                s.activate(pair);p.playerTick();size(c,p,1);near(c,.42*.7,p.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH),"Tiny jump wins");near(c,2.7,p.getBlockInteractionRange(),"Tiny reach wins");near(c,.6,p.getStepHeight(),"No giant step while tiny");c.assertFalse(p.getAttributeInstance(EntityAttributes.GENERIC_SCALE).hasModifier(GiantPlayerChallenge.SCALE_MODIFIER),"No giant scale in pair");
                ChallengeRegistry.get(ID).onStop(p.getServer(),p);size(c,p,1);near(c,2.7,p.getBlockInteractionRange(),"Stopping suppressed giant preserves active tiny");
                s.activate(ID);size(c,p,3.6);near(c,.42,p.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH),"No lingering tiny jump");
            }
            var boat=new BoatEntity(c.getWorld(),p.getX(),p.getY(),p.getZ());var dimensions=boat.getDimensions(boat.getPose());
            try {p.startRiding(boat,true);p.playerTick();near(c,2,p.getScale(),"Rider remains giant");near(c,dimensions.width(),boat.getWidth(),"Boat unchanged");}
            finally{p.stopRiding();boat.discard();}
            s.activate("test_easy");position(p,room.base);size(c,p,1.8);near(c,.6,p.getStepHeight(),"All size effects restored");
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="giant_reload")
    public void deathRespawnDisconnectDimensionAndStateNbt(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();position(p,room.base);var uuid=p.getUuid();s.activate(ID);
            p.setHealth(0);p.playerTick();near(c,1,p.getScale(),"Dead entity releases size");p=s.respawn(p,false);size(c,p,3.6);
            p.teleport(p.getServer().getWorld(World.NETHER),0,100,0,0,0);p.playerTick();size(c,p,3.6);p.teleport(c.getWorld(),room.base.getX()+.5,room.base.getY(),room.base.getZ()+.5,0,0);
            var manager=p.getServer().getOverworld().getPersistentStateManager();var restored=CurseCycleState.createFromNbt(s.state.writeNbt(new NbtCompound(),c.getWorld().getRegistryManager()),c.getWorld().getRegistryManager());manager.set(CurseCycleState.STATE_KEY,restored);p.playerTick();size(c,p,3.6);
            restored.beginActivation();restored.getActiveChallengeIds().clear();restored.getActiveChallengeIds().add(ID);p.playerTick();size(c,p,3.6);
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler,p.getServer());p.getServer().getPlayerManager().remove(p);
            p=s.player(uuid);p.playerTick();size(c,p,3.6);
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler,p.getServer());p.getServer().getPlayerManager().remove(p);CycleManager.activateChallenge(c.getWorld().getServer(),restored,"test_easy");p=s.player(uuid);p.playerTick();size(c,p,1.8);
            manager.set(CurseCycleState.STATE_KEY,s.state);
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="giant_sync")
    public void vanillaTrackedPacketContainsAllFourModifiers(TestContext c) throws Exception {
        try(var room=new Room(c);var s=new ChallengeScenario(c)) {
            var p=s.player();position(p,room.base);s.activate(ID);
            var packet=new EntityAttributesS2CPacket(p.getId(),p.getAttributes().getAttributesToSend());
            var buf=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),p.getRegistryManager());
            try {
                EntityAttributesS2CPacket.CODEC.encode(buf,packet);var decoded=EntityAttributesS2CPacket.CODEC.decode(buf);
                for(var id:List.of(GiantPlayerChallenge.SCALE_MODIFIER,GiantPlayerChallenge.STEP_MODIFIER,GiantPlayerChallenge.BLOCK_MODIFIER,GiantPlayerChallenge.ENTITY_MODIFIER)) c.assertTrue(decoded.getEntries().stream().anyMatch(e->e.attribute().value().isTracked()&&e.modifiers().stream().anyMatch(m->m.id().equals(id))),"Tracked wire modifier "+id);
            } finally {buf.release();}
        }
        c.complete();
    }
}
