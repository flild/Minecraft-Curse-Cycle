package validation;

import com.cursecycle.client.ClientDataHolder;
import com.cursecycle.client.SilentWorld;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.SubtitlesHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.sound.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;
import net.minecraft.world.GameMode;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import sun.misc.Unsafe;

import javax.sound.sampled.AudioFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real transformed listener/system + real OpenAL Soft null device, no production gate replacement. */
public final class SilentWorldProbe implements PreLaunchEntrypoint {
    private static final String ID = "silent_world";
    private static Unsafe unsafe;
    private static MinecraftClient client;
    private static SoundSystem system;
    private static SoundListener listener;
    private static GameOptions options;
    private static int checks;
    private static boolean alive = true;
    private static GameMode mode = GameMode.SURVIVAL;
    private static final List<StaticSound> buffers = new ArrayList<>();

    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isAlive() { return alive; }
    }
    public static class Manager extends ClientPlayerInteractionManager {
        public Manager() { super(null, null); }
        @Override public GameMode getCurrentGameMode() { return mode; }
    }
    public static class Samples extends SoundLoader {
        public Samples() { super(id -> Optional.empty()); }
        @Override public CompletableFuture<StaticSound> loadStatic(Identifier id) {
            // A short sine wave: source time advances even when listener gain is zero.
            int samples = 4410;
            var pcm = MemoryUtil.memAlloc(samples * 2);
            for (int i = 0; i < samples; i++) pcm.putShort((short) (Math.sin(i * 2 * Math.PI * 440 / 44100) * 8000));
            pcm.flip();
            var buffer = new StaticSound(pcm, new AudioFormat(44100, 16, 1, true, false));
            buffers.add(buffer);
            return CompletableFuture.completedFuture(buffer);
        }
    }
    private static final class Tone extends AbstractSoundInstance {
        private final WeightedSoundSet set;
        Tone(SoundCategory category, boolean loop, String label) {
            super(Identifier.of("probe", label), category, SoundInstance.createRandom());
            sound = new Sound(id, ConstantFloatProvider.create(1), ConstantFloatProvider.create(1), 1,
                    Sound.RegistrationType.FILE, false, false, 16);
            set = new WeightedSoundSet(id, "subtitles.probe." + label);
            volume = pitch = 1;
            repeat = loop;
            attenuationType = SoundInstance.AttenuationType.NONE;
            relative = true;
        }
        @Override public WeightedSoundSet getSoundSet(SoundManager manager) { return set; }
    }
    private static Object get(Object target, Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
    private static void sync(String... ids) { ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of()); }
    private static void gain(float expected, String why) {
        check(Math.abs(AL10.alGetListenerf(AL10.AL_GAIN) - expected) < .00001F, why);
        check(AL10.alGetError() == AL10.AL_NO_ERROR, "OpenAL call succeeds");
    }
    private static void refresh(float expected) {
        system.tick(true);
        gain(expected, "Paused tick reconciles current output");
        check(Math.abs(listener.getVolume() - options.getSoundVolume(SoundCategory.MASTER)) < .00001,
                "Logical listener volume retains current user MASTER");
    }
    private static int pointer(Tone tone) throws Exception {
        var sources = (Map<?, ?>) get(system, SoundSystem.class, "sources");
        var manager = sources.get(tone);
        check(manager != null, "Real SoundSystem.play created a source");
        var source = get(manager, Channel.SourceManager.class, "source");
        return (int) get(source, Source.class, "pointer");
    }
    private static void level(SoundCategory category, double volume) {
        options.getSoundVolumeOption(category).setValue(volume);
        system.updateSoundVolume(category, (float) volume);
    }
    private static void audit() throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/client/sound/SoundListener.class"))).accept(node, 0);
        int gates = 0;
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.contains("cursecycle$muteOutput")) {
                check(method.name.equals("setVolume"), "Only final setVolume output is filtered");
                gates++;
            }
        }
        check(gates == 1, "Exactly one final listener gain filter");
        node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/client/sound/SoundSystem.class"))).accept(node, 0);
        var sites = new HashSet<String>();
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.contains("cursecycle$refreshOutput"))
                sites.add(method.name + method.desc);
        }
        check(sites.equals(Set.of("tick(Z)V", "play(Lnet/minecraft/client/sound/SoundInstance;)V",
                "updateListenerPosition(Lnet/minecraft/client/render/Camera;)V")), "Tick/play/frame refresh sites transformed");
    }
    private static void prepare() throws Exception {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
        client = (MinecraftClient) unsafe.allocateInstance(MinecraftClient.class);
        set(null, MinecraftClient.class, "instance", client);
        client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class);
        client.player = (Player) unsafe.allocateInstance(Player.class);
        client.interactionManager = (Manager) unsafe.allocateInstance(Manager.class);
        options = (GameOptions) unsafe.allocateInstance(GameOptions.class);
        set(client, MinecraftClient.class, "options", options);
        var levels = new EnumMap<SoundCategory, SimpleOption<Double>>(SoundCategory.class);
        for (var category : SoundCategory.values()) levels.put(category, new SimpleOption<>("probe.volume",
                SimpleOption.emptyTooltip(), (text, value) -> Text.literal(value.toString()),
                SimpleOption.DoubleSliderCallbacks.INSTANCE, .8, value -> {}));
        set(options, GameOptions.class, "soundVolumeLevels", levels);
        var device = unsafe.allocateInstance(SimpleOption.class);
        set(device, SimpleOption.class, "value", "");
        set(options, GameOptions.class, "soundDevice", device);
        set(options, GameOptions.class, "directionalAudio", SimpleOption.ofBoolean("probe.directional", false));
        set(options, GameOptions.class, "showSubtitles", SimpleOption.ofBoolean("probe.subtitles", true));
        var manager = new SoundManager(options);
        system = (SoundSystem) get(manager, SoundManager.class, "soundSystem");
        listener = (SoundListener) get(system, SoundSystem.class, "listener");
        set(system, SoundSystem.class, "soundLoader", new Samples());
        var engine = (SoundEngine) get(system, SoundSystem.class, "soundEngine");
        // Deterministic source setup on this OpenAL context, not the asynchronous game executor.
        set(system, SoundSystem.class, "channel", new Channel(engine, Runnable::run));
        var start = SoundSystem.class.getDeclaredMethod("start");
        start.setAccessible(true);
        start.invoke(system);
        check((boolean) get(system, SoundSystem.class, "started"), "Real OpenAL Soft null device started");
    }
    private static void audio() throws Exception {
        ClientDataHolder.reset();
        refresh(.8F);
        var loop = new Tone(SoundCategory.MUSIC, true, "already_playing_music");
        system.play(loop);
        int looping = pointer(loop);
        check(AL10.alGetSourcei(looping, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING, "Positive baseline source plays");
        var subtitles = new SubtitlesHud(client);
        system.registerListener(subtitles);
        sync(ID, "no_sprint");
        // A frame boundary must mute an existing source without a new play or unpaused tick.
        system.updateListenerPosition(new net.minecraft.client.render.Camera());
        gain(0, "Existing music becomes silent at frame boundary");
        check(AL10.alGetSourcei(looping, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING, "Existing loop is not stopped/paused");
        for (var category : SoundCategory.values()) {
            var tone = new Tone(category, false, category.getName());
            system.play(tone);
            gain(0, "All categories including UI/MASTER/VOICE are silent: " + category);
            int source = pointer(tone);
            check(AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING, "Muted sounds still run on their original timeline");
        }
        check(((List<?>) get(subtitles, SubtitlesHud.class, "entries")).size() == SoundCategory.values().length,
                "Real SubtitlesHud receives every muted category via real SoundSystem.play");
        check(options.getShowSubtitles().getValue(), "Subtitle preference untouched");
        // Change each real SimpleOption and use the same sound-system update as the slider callback.
        for (var category : SoundCategory.values()) {
            level(category, .35);
            gain(0, "Slider cannot bypass mute: " + category);
            check(Math.abs(options.getSoundVolume(category) - .35F) < .00001, "New user category value is retained");
        }
        for (double master : new double[]{0, 1, .6, .25}) {
            level(SoundCategory.MASTER, master);
            refresh(0);
        }
        var shortTone = new Tone(SoundCategory.PLAYERS, false, "expires_during_mute");
        system.play(shortTone);
        int shortSource = pointer(shortTone);
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (AL10.alGetSourcei(shortSource, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING && System.nanoTime() < deadline)
            Thread.sleep(10);
        check(AL10.alGetSourcei(shortSource, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED, "Short muted sound finishes rather than being queued");
        sync("no_sprint");
        refresh(.25F);
        check(AL10.alGetSourcei(looping, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING, "Current loop survives activation and ending");
        check(!AL10.alIsSource(shortSource) || AL10.alGetSourcei(shortSource, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED,
                "Ending does not replay the expired sound (Channel may already have released it)");
        check(Math.abs(AL10.alGetSourcef(looping, AL10.AL_GAIN) - .35F) < .00001, "Loop uses latest MUSIC user level");
        int notifications = ((List<?>) get(subtitles, SubtitlesHud.class, "entries")).size();
        for (int i = 0; i < 25; i++) system.tick(false);
        check(!((Map<?, ?>) get(system, SoundSystem.class, "sources")).containsKey(shortTone), "Vanilla cleans up expired sources");
        check(((List<?>) get(subtitles, SubtitlesHud.class, "entries")).size() == notifications, "No catch-up subtitle or sound playback");
    }
    private static void lifecycle() throws Exception {
        sync(ID, ID, "no_sprint");
        for (var value : GameMode.values()) {
            mode = value;
            refresh(value == GameMode.SURVIVAL || value == GameMode.ADVENTURE ? 0 : .25F);
        }
        mode = GameMode.SURVIVAL;
        alive = false; refresh(.25F);
        alive = true;
        client.player = (Player) unsafe.allocateInstance(Player.class); refresh(0);
        set(client.player, Entity.class, "removalReason", Entity.RemovalReason.DISCARDED); refresh(.25F);
        client.player = (Player) unsafe.allocateInstance(Player.class); refresh(0);
        for (int i = 0; i < 3; i++) {
            client.world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class); refresh(0);
        }
        var world = client.world;
        client.world = null; refresh(.25F);
        client.world = world; refresh(0);
        var player = client.player;
        client.player = null; refresh(.25F);
        client.player = player; refresh(0);
        var manager = client.interactionManager;
        client.interactionManager = null; refresh(.25F);
        client.interactionManager = manager; refresh(0);
        ClientDataHolder.reset(); refresh(.25F);
        sync("no_sprint"); refresh(.25F);
        sync(ID); refresh(0);
        // The play hook must reconcile immediately even before the next tick/frame.
        sync("no_sprint");
        system.play(new Tone(SoundCategory.MASTER, false, "menu_restores_gain")); gain(.25F, "Menu/play restores gain immediately");
        sync(ID);
        system.play(new Tone(SoundCategory.MASTER, false, "play_mutes_gain")); gain(0, "Play immediately applies newly synced mute");
        // Reinitialization of the listener/device cannot escape the final output filter.
        listener.init(); listener.setVolume(.25F); gain(0, "Listener reinitialization remains muted");
        ClientDataHolder.reset();
        system.updateListenerPosition(new net.minecraft.client.render.Camera()); gain(.25F, "Unsynced menu restores current gain");
        check(!SilentWorld.shouldMute(null), "No client cannot mute");
    }
    @Override public void onPreLaunch() {
        try {
            prepare();
            audio();
            lifecycle();
            audit();
            system.stopAll();
            for (var buffer : buffers) buffer.close();
            system.stop();
            System.out.println("SILENT_WORLD_CLIENT_PROBE_PASSED checks=" + checks);
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
}
