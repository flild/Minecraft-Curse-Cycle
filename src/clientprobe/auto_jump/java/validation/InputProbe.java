package validation;
import com.cursecycle.client.AutoJumpMotion;
import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.network.AutoJumpPayload;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.world.World;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import io.netty.buffer.Unpooled;
import java.util.List;
import sun.misc.Unsafe;

public class InputProbe implements PreLaunchEntrypoint {
    static boolean alive=true, creative, spectator, ground, riding, water, lava, climbing, elytra;
    static ClientWorld world;
    static PlayerAbilities abilities=new PlayerAbilities();
    static Vec3d velocity=Vec3d.ZERO;
    static int writes, checks;
    public static class Player extends ClientPlayerEntity {
        public Player() { super(null,null,null,null,null,false,false); }
        @Override public int getId() { return 42; }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isCreative() { return creative; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public PlayerAbilities getAbilities() { return abilities; }
        @Override public World getWorld() { return world; }
        @Override public boolean hasVehicle() { return riding; }
        @Override public boolean isTouchingWater() { return water; }
        @Override public boolean isInLava() { return lava; }
        @Override public boolean isClimbing() { return climbing; }
        @Override public boolean isFallFlying() { return elytra; }
        @Override public void setOnGround(boolean value) { ground=value; }
        @Override public boolean isOnGround() { return ground; }
        @Override public Vec3d getVelocity() { return velocity; }
        @Override public void setVelocity(Vec3d v) { velocity=v; writes++; }
    }
    static void check(boolean ok,String why) { checks++; if(!ok) throw new AssertionError(why); }
    static void sync(String... ids) { ClientDataHolder.updateState(1,20,List.of(ids),"",List.of()); }
    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion(); net.minecraft.Bootstrap.initialize();
            var f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); var u=(Unsafe)f.get(null);
            var p=(Player)u.allocateInstance(Player.class);
            world=(ClientWorld)u.allocateInstance(ClientWorld.class);
            f=World.class.getDeclaredField("isClient"); f.setAccessible(true); f.set(world,true);
            var payload=new AutoJumpPayload(42,new Vec3d(.17,.62,.28));
            var buf=new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);
            AutoJumpPayload.CODEC.encode(buf,payload);
            check(payload.equals(AutoJumpPayload.CODEC.decode(buf)),"Payload roundtrip"); buf.release();
            ClientDataHolder.reset(); p.setOnGround(true); AutoJumpMotion.receive(p,payload);
            check(writes==0 && p.isOnGround(),"Unsynced ignored");
            sync("auto_jump"); velocity=new Vec3d(.31,0,.45); AutoJumpMotion.receive(p,payload);
            var expected=new Vec3d(.31+.17,.62,.45+.28);
            check(velocity.equals(expected)&&!p.isOnGround(),"Client horizontal motion plus ordinary sprint impulse");
            AutoJumpMotion.receive(p,payload); check(velocity.equals(expected),"Repeat cannot add impulse");
            // An already ascending manual jump keeps its predicted motion.
            velocity=new Vec3d(.17,.25,.28); p.setOnGround(false); AutoJumpMotion.receive(p,payload);
            check(velocity.equals(new Vec3d(.17,.25,.28))&&!p.isOnGround(),"Delayed approval cannot restart manual ascent");
            // Ground is false before the next ordinary input-driven jump decision.
            check(!p.isOnGround(),"Automatic-first order suppresses stale grounded input");
            int before=writes;
            AutoJumpMotion.receive(p,new AutoJumpPayload(43,Vec3d.ZERO)); AutoJumpMotion.receive(null,payload);
            check(writes==before,"Old player entity ignored");
            for(int i=0;i<3;i++) {
                alive=i!=0; creative=i==1; spectator=i==2; AutoJumpMotion.receive(p,payload);
                check(writes==before,"Excluded player "+i);
            }
            alive=true; creative=spectator=false; sync("no_sprint"); AutoJumpMotion.receive(p,payload);
            check(writes==before,"Ended activation ignored");
            sync("auto_jump"); velocity=Vec3d.ZERO; p.setOnGround(true);
            for(int i=0;i<6;i++) {
                riding=i==0; water=i==1; lava=i==2; climbing=i==3; elytra=i==4; abilities.flying=i==5;
                AutoJumpMotion.receive(p,payload); check(writes==before,"Delayed state exclusion "+i);
            }
            riding=water=lava=climbing=elytra=abilities.flying=false;
            sync("auto_jump","no_jumping"); AutoJumpMotion.receive(p,payload); check(writes==before,"No jumping wins");
            sync("auto_jump","no_sprint"); AutoJumpMotion.receive(p,payload); check(writes==before+1,"Effective double IDs");
            ClientDataHolder.reset(); AutoJumpMotion.receive(p,payload); check(writes==before+1,"Disconnect reset");
            // Load the actual common transformations in CLIENT, without a probe Mixin or fake gate.
            check(java.util.Arrays.stream(net.minecraft.entity.LivingEntity.class.getDeclaredMethods())
                    .anyMatch(m -> m.getName().contains("successfulJump")), "Common success hook transformed in CLIENT");
            check(java.util.Arrays.stream(net.minecraft.entity.player.PlayerEntity.class.getDeclaredMethods())
                    .anyMatch(m -> m.getName().contains("onPlayerJump")), "Player jump hook transformed in CLIENT");
            var removal=net.minecraft.entity.Entity.class.getDeclaredField("removalReason"); removal.setAccessible(true);
            for(int cycle=0;cycle<32;cycle++) {
                ClientDataHolder.reset(); sync("auto_jump");
                for(double x:new double[]{-.7,0,.31}) for(double y:new double[]{-.3,0,.25})
                    for(boolean grounded:new boolean[]{false,true}) {
                        velocity=new Vec3d(x,y,.45); ground=grounded; var original=velocity; int start=writes;
                        AutoJumpMotion.receive(p,payload);
                        boolean ignored=!grounded&&y>0;
                        check(writes==start+(ignored?0:1), "Receiver write count");
                        check(velocity.equals(ignored?original:new Vec3d(x+.17,.62,.45+.28)), "Signed motion preserved");
                        check(!ground, "Accepted motion clears ground");
                        for(int duplicate=0;duplicate<50;duplicate++) AutoJumpMotion.receive(p,payload);
                        check(writes==start+(ignored?0:1), "Repeated ascending approvals do not stack");
                    }
                ground=true; velocity=Vec3d.ZERO; int start=writes;
                removal.set(p,net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                AutoJumpMotion.receive(p,payload); check(writes==start&&ground,"Removed object ignored"); removal.set(p,null);
                sync("auto_jump","no_sprint"); AutoJumpMotion.receive(p,payload); check(writes==start+1,"Companion keeps auto motion");
                ground=true; velocity=new Vec3d(.2,-.7,.3); var flight=velocity; sync("no_sprint");
                AutoJumpMotion.receive(p,payload); check(velocity.equals(flight)&&ground,"Ending auto preserves unrelated motion");
                sync("auto_jump","no_sprint"); sync("auto_jump"); ground=true; velocity=Vec3d.ZERO;
                AutoJumpMotion.receive(p,payload); check(velocity.y==.62&&!ground,"Ending companion keeps auto");
                ClientDataHolder.reset(); ground=true; velocity=flight; AutoJumpMotion.receive(p,payload);
                check(velocity.equals(flight)&&ground,"Reset rejects late payload");
            }
            System.out.println("AUTO_JUMP_REVIEW_CLIENT_PASSED checks="+checks+": real CLIENT payload codec and receiver, full motion, takeoff, both manual orderings, duplicate, entity/mode/death/sync guards; stub player accessors, no live world or network");
            System.exit(0);
        } catch(Throwable t) { t.printStackTrace(); System.exit(1); }
    }
}
