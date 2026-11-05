package validation;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.InvertedMouseInput;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.tutorial.TutorialManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import sun.misc.Unsafe;
import java.lang.reflect.Method;
import java.util.List;

/** Runs transformed Mouse.updateMouse and its real production gate without a window or live world. */
public final class InputProbe implements PreLaunchEntrypoint {
    static boolean alive = true, scoping, riding, water, lava, climbing, elytra;
    static final net.minecraft.entity.player.PlayerAbilities ABILITIES = new net.minecraft.entity.player.PlayerAbilities();
    static GameMode mode = GameMode.SURVIVAL;
    static double yaw, pitch;
    static int calls, checks;
    static Unsafe unsafe;
    static Method update;
    static MinecraftClient client;
    static GameOptions options;
    static ClientPlayerEntity player;
    static ClientWorld world;
    static ClientPlayerInteractionManager manager;

    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isUsingSpyglass() { return scoping; }
        @Override public boolean hasVehicle() { return riding; }
        @Override public boolean isTouchingWater() { return water; }
        @Override public boolean isInLava() { return lava; }
        @Override public boolean isClimbing() { return climbing; }
        @Override public boolean isFallFlying() { return elytra; }
        @Override public net.minecraft.entity.player.PlayerAbilities getAbilities() { return ABILITIES; }
        @Override public void changeLookDirection(double x, double y) { yaw=x; pitch=y; calls++; }
    }
    public static class Manager extends ClientPlayerInteractionManager {
        public Manager() { super(null, null); }
        @Override public GameMode getCurrentGameMode() { return mode; }
    }
    static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        var f=owner.getDeclaredField(name); f.setAccessible(true); f.set(target,value);
    }
    static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    static void sync(String... ids) { ClientDataHolder.updateState(1,20,List.of(ids),"",List.of()); }
    static Mouse mouse(double sensitivity, boolean invertY, boolean smooth) throws Exception {
        set(options,GameOptions.class,"mouseSensitivity",new SimpleOption<Double>("probe.sensitivity",
                SimpleOption.emptyTooltip(),(text,v)->Text.empty(),SimpleOption.DoubleSliderCallbacks.INSTANCE,
                sensitivity,v->{}));
        set(options,GameOptions.class,"invertYMouse",SimpleOption.ofBoolean("probe.invertY",invertY));
        options.smoothCameraEnabled=smooth;
        return new Mouse(client);
    }
    static double[] sample(Mouse mouse, double x, double y) throws Exception {
        set(mouse,Mouse.class,"cursorDeltaX",x); set(mouse,Mouse.class,"cursorDeltaY",y);
        int before=calls; update.invoke(mouse,.05);
        check(calls==before+1,"Exactly one look invocation");
        return new double[]{yaw,pitch};
    }
    static void same(double expected,double actual,String why) {
        check(Math.abs(expected-actual)<1e-10,why+": expected="+expected+" actual="+actual);
    }

    public static class RealLookPlayer extends ClientPlayerEntity {
        public RealLookPlayer() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isUsingSpyglass() { return scoping; }
    }
    static void auditMouse() throws Exception {
        var node=new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                ".mixin.out/class/net/minecraft/client/Mouse.class"))).accept(node,0);
        int injected=0, look=0;
        for(var method:node.methods) for(var insn:method.instructions) {
            if(!(insn instanceof org.objectweb.asm.tree.MethodInsnNode call)) continue;
            if(call.name.contains("cursecycle$invertMouseLook")) {
                check(method.name.equals("updateMouse"),"Only mouse look intercepted, no GUI/button/scroll"); injected++;
            }
            if(call.name.equals("changeLookDirection")) { check(method.name.equals("updateMouse"),"Look caller"); look++; }
        }
        check(injected==1 && look==1,"Exactly one transformed invocation site");
    }
    static void extraChecks() throws Exception {
        alive=true; mode=GameMode.SURVIVAL; scoping=false;
        client.player=(RealLookPlayer)unsafe.allocateInstance(RealLookPlayer.class);
        // No override of Entity.changeLookDirection: actual yaw/pitch accumulation and pitch clamping.
        for(float start:new float[]{-89,0,89}) for(boolean active:new boolean[]{false,true}) {
            client.player.setYaw(31); client.player.setPitch(start);
            set(client.player,net.minecraft.entity.Entity.class,"prevYaw",31f);
            set(client.player,net.minecraft.entity.Entity.class,"prevPitch",start);
            Mouse m=mouse(.5,false,false); sync(active?"inverted_mouse":"no_sprint");
            set(m,Mouse.class,"cursorDeltaX",20d); set(m,Mouse.class,"cursorDeltaY",20d); update.invoke(m,.05);
            double sign=active?-1:1;
            check(Math.abs(31+sign*3-client.player.getYaw())<1e-5,"Actual Entity yaw float tolerance");
            check(Math.abs(Math.max(-90,Math.min(90,start+sign*3))-client.player.getPitch())<1e-5,"Actual Entity pitch clamp float tolerance");
            float y=client.player.getYaw(),v=client.player.getPitch(); sync("no_sprint");
            same(y,client.player.getYaw(),"End retains angle"); same(v,client.player.getPitch(),"End retains pitch");
            client.player.setYaw(73); client.player.setPitch(-12); sync("inverted_mouse");
            same(73,client.player.getYaw(),"Absolute yaw unchanged"); same(-12,client.player.getPitch(),"Absolute pitch unchanged");
        }
        client.player=(Player)unsafe.allocateInstance(Player.class);
        Mouse m=mouse(.5,false,false); sync("no_sprint");
        double controlLook=sample(m,10,10)[0];
        var names=new String[]{"forwardKey","backKey","leftKey","rightKey","jumpKey","sneakKey","sprintKey"};
        var keys=new net.minecraft.client.option.KeyBinding[names.length];
        for(int i=0;i<names.length;i++) { keys[i]=new net.minecraft.client.option.KeyBinding("probe."+names[i],300+i,
                net.minecraft.client.option.KeyBinding.MOVEMENT_CATEGORY); set(options,GameOptions.class,names[i],keys[i]); }
        var input=new net.minecraft.client.input.KeyboardInput(options); client.player.input=input;
        for(boolean mouseActive:new boolean[]{true,false}) for(boolean keyboardActive:new boolean[]{true,false}) {
            sync(mouseActive?"inverted_mouse":"no_sprint",keyboardActive?"inverted_keyboard":"no_sprint");
            for(int mask=0;mask<128;mask++) {
                for(int i=0;i<keys.length;i++) keys[i].setPressed((mask&(1<<i))!=0);
                input.tick(false,.3f);
                float f=(((mask&1)!=0?1:0)-((mask&2)!=0?1:0))*(keyboardActive?-1:1);
                float side=(((mask&4)!=0?1:0)-((mask&8)!=0?1:0))*(keyboardActive?-1:1);
                check(input.movementForward==f&&input.movementSideways==side,"Keyboard and mouse independent, both endings");
                var angle=sample(m,10,10); same(mouseActive?-controlLook:controlLook,angle[0],"Mouse independent of keyboard");
                same(mouseActive?-controlLook:controlLook,angle[1],"Pitch independent of keyboard");
                check(input.jumping==((mask&16)!=0)&&input.sneaking==((mask&32)!=0),"Jump/sneak unchanged");
            }
        }
        var renderer=(net.minecraft.client.render.GameRenderer)unsafe.allocateInstance(net.minecraft.client.render.GameRenderer.class);
        set(renderer,net.minecraft.client.render.GameRenderer.class,"client",client);
        var roll=java.util.Arrays.stream(net.minecraft.client.render.GameRenderer.class.getDeclaredMethods())
                .filter(x->x.getName().contains("cursecycle$rollProjection")).findFirst().orElseThrow(); roll.setAccessible(true);
        for(boolean mouseActive:new boolean[]{true,false}) for(boolean cameraActive:new boolean[]{true,false}) {
            sync(mouseActive?"inverted_mouse":"no_sprint",cameraActive?"upside_down_camera":"no_sprint");
            var projection=new org.joml.Matrix4f().perspective(1.2f,1.6f,.05f,128f);
            var expected=new org.joml.Matrix4f(projection);
            if(cameraActive) expected.rotateZ((float)Math.PI);
            var actual=(org.joml.Matrix4f)roll.invoke(renderer,projection);
            check(expected.equals(actual,1e-6f),"Real camera injected handler; both endings");
            var angle=sample(m,10,10); same(mouseActive?-controlLook:controlLook,angle[0],"Roll does not reinvert yaw");
            same(mouseActive?-controlLook:controlLook,angle[1],"Roll does not reinvert pitch");
        }
        for(String forced:new String[]{"forced_third_person_front","forced_third_person_rpg"}) {
            sync("inverted_mouse",forced); options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            var expected=forced.endsWith("front")?net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT:net.minecraft.client.option.Perspective.THIRD_PERSON_BACK;
            check(options.getPerspective()==expected,"Actual perspective setter preserves forced camera");
            var angle=sample(m,10,10); same(-controlLook,angle[0],"Forced perspective mouse inversion");
            sync(forced); angle=sample(m,10,10); same(controlLook,angle[0],"End inversion retains perspective");
            check(options.getPerspective()==expected,"Companion perspective retained");
        }
        for(int environment=0;environment<32;environment++) {
            riding=(environment&1)!=0; water=(environment&2)!=0; lava=(environment&4)!=0;
            climbing=(environment&8)!=0; elytra=(environment&16)!=0;
            sync("inverted_mouse"); var angle=sample(m,10,10); same(-controlLook,angle[0],"Mouse remains active in all environments");
        }
        riding=water=lava=climbing=elytra=false;
        for(int cycle=0;cycle<32;cycle++) {
            ClientDataHolder.reset(); check(!InvertedMouseInput.shouldInvert(client),"Reset clears own effect");
            for(int n=0;n<50;n++) sync("inverted_mouse","inverted_mouse");
            var angle=sample(m,10,10); same(-controlLook,angle[0],"Repeated sync/duplicates invert once");
            mouse(.1,true,true); check(options.getInvertYMouse().getValue(),"Live option change");
            sync("no_sprint"); check(options.getInvertYMouse().getValue()&&options.getMouseSensitivity().getValue()==.1,"End retains latest settings");
            m=mouse(.5,false,false);
        }
    }
    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion(); net.minecraft.Bootstrap.initialize();
            var f=Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); unsafe=(Unsafe)f.get(null);
            client=(MinecraftClient)unsafe.allocateInstance(MinecraftClient.class);
            options=(GameOptions)unsafe.allocateInstance(GameOptions.class);
            player=(Player)unsafe.allocateInstance(Player.class);
            world=(ClientWorld)unsafe.allocateInstance(ClientWorld.class);
            manager=(Manager)unsafe.allocateInstance(Manager.class);
            set(client,MinecraftClient.class,"options",options);
            set(null,MinecraftClient.class,"instance",client);
            client.player=player; client.world=world; client.interactionManager=manager;
            set(client,MinecraftClient.class,"tutorialManager",new TutorialManager(client,options));
            update=Mouse.class.getDeclaredMethod("updateMouse",double.class); update.setAccessible(true);
            auditMouse();
            int comparisons=0;
            for (double sensitivity : new double[]{0,.1,.5,1}) {
                for (boolean invertY : new boolean[]{false,true}) {
                    for (boolean smooth : new boolean[]{false,true}) {
                        for (boolean scope : new boolean[]{false,true}) {
                            scoping=scope;
                            for (var perspective : Perspective.values()) {
                                set(options,GameOptions.class,"perspective",perspective);
                                Mouse control=mouse(sensitivity,invertY,smooth);
                                Mouse inverted=mouse(sensitivity,invertY,smooth);
                                for (int frame=0;frame<4;frame++) {
                                    double x=new double[]{13,-8,0,1.5}[frame];
                                    double y=new double[]{-9,6,2.25,0}[frame];
                                    sync("no_sprint"); var before=sample(control,x,y);
                                    sync("inverted_mouse","inverted_mouse","inverted_keyboard","no_sprint");
                                    var after=sample(inverted,x,y);
                                    same(-before[0],after[0],"Yaw after sensitivity/smoothing");
                                    same(-before[1],after[1],"Pitch after current invertY");
                                    check(options.getMouseSensitivity().getValue()==sensitivity
                                            && options.getInvertYMouse().getValue()==invertY
                                            && options.getPerspective()==perspective,"Options unchanged");
                                    comparisons++;
                                }
                            }
                        }
                    }
                }
            }
            scoping=false; Mouse m=mouse(.5,false,false);
            for (var gm : GameMode.values()) {
                mode=gm; sync("inverted_mouse");
                check(InvertedMouseInput.shouldInvert(client)==(gm==GameMode.SURVIVAL||gm==GameMode.ADVENTURE),"Mode "+gm);
                var active=sample(m,10,10);
                sync("no_sprint"); var control=sample(m,10,10);
                double sign=(gm==GameMode.SURVIVAL||gm==GameMode.ADVENTURE)?-1:1;
                same(sign*control[0],active[0],"Mode yaw"); same(sign*control[1],active[1],"Mode pitch");
            }
            mode=GameMode.SURVIVAL; sync("inverted_mouse");
            alive=false; check(!InvertedMouseInput.shouldInvert(client),"Death"); alive=true;
            set(player,net.minecraft.entity.Entity.class,"removalReason",net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            check(!InvertedMouseInput.shouldInvert(client),"Removed object");
            client.player=(Player)unsafe.allocateInstance(Player.class);
            check(InvertedMouseInput.shouldInvert(client),"New player object");
            client.world=null; check(!InvertedMouseInput.shouldInvert(client),"No world"); client.world=world;
            client.interactionManager=null; check(!InvertedMouseInput.shouldInvert(client),"No mode sync"); client.interactionManager=manager;
            client.player=null; check(!InvertedMouseInput.shouldInvert(client),"No player");
            client.player=(Player)unsafe.allocateInstance(Player.class);
            ClientDataHolder.reset(); check(!InvertedMouseInput.shouldInvert(client),"Disconnect/lost sync");
            sync("no_sprint"); check(!InvertedMouseInput.shouldInvert(client),"Ended offline");
            sync("inverted_mouse"); check(InvertedMouseInput.shouldInvert(client),"Fresh connection");
            // A direct/absolute rotation path does not acquire a second inversion.
            client.player.changeLookDirection(7,9); same(7,yaw,"Non-mouse yaw"); same(9,pitch,"Non-mouse pitch");
            extraChecks();
            System.out.println("INVERTED_MOUSE_REVIEW_CLIENT_PASSED checks="+checks+" pairs="+comparisons+": "+comparisons+" paired transformed Mouse.updateMouse samples; real gate, sensitivity, invertY, smooth/spyglass, perspectives, duplicate IDs, modes, lifecycle/reset. Stub client/player accessors, no live world/window/network.");
            System.exit(0);
        } catch (Throwable t) { t.printStackTrace(); System.exit(1); }
    }
}
