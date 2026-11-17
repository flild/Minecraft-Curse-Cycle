package validation;

import com.cursecycle.client.ClientDataHolder;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.joml.Matrix4f;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.List;

/** Calls transformed vanilla drawSlot and captures draws without an OpenGL window. */
public final class InventoryLockdownProbe implements PreLaunchEntrypoint {
    private static GameMode mode = GameMode.SURVIVAL;
    private static int checks, slots;

    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isCreative() { return mode == GameMode.CREATIVE; }
        @Override public boolean isSpectator() { return mode == GameMode.SPECTATOR; }
        @Override public Text getName() { return Text.literal("Probe"); }
    }

    private static class Screen extends HandledScreen<ScreenHandler> {
        Screen(ScreenHandler handler, PlayerInventory inventory) { super(handler, inventory, Text.empty()); }
        @Override protected void drawBackground(DrawContext context, float delta, int x, int y) {}
        void slot(DrawContext context, Slot slot) { super.drawSlot(context, slot); }
    }

    private record Fill(int x1, int y1, int x2, int y2, int color, float z) {}
    private static class Context extends DrawContext {
        final List<Fill> fills = new ArrayList<>();
        int items;
        Context(MinecraftClient client) { super(client, null); }
        @Override public void drawItem(ItemStack stack, int x, int y, int seed) { items++; }
        @Override public void drawItemWithoutEntity(ItemStack stack, int x, int y, int seed) { items++; }
        @Override public void drawItemInSlot(TextRenderer font, ItemStack stack, int x, int y, String count) {}
        @Override public void fill(int x1, int y1, int x2, int y2, int color) {
            fills.add(new Fill(x1, y1, x2, y2, color, getMatrices().peek().getPositionMatrix().m32()));
        }
    }

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }
    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void sync(String... ids) { ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of()); }

    private static void draw(MinecraftClient client, Screen screen, Slot slot, boolean locked) {
        var context = new Context(client);
        context.getMatrices().translate(31, 47, 0);
        var before = new Matrix4f(context.getMatrices().peek().getPositionMatrix());
        var original = slot.getStack().copy();
        screen.slot(context, slot);
        check(before.equals(context.getMatrices().peek().getPositionMatrix()), "Slot matrix restored");
        check(context.getMatrices().isEmpty(), "No leaked matrix frames");
        check(ItemStack.areEqual(original, slot.getStack()), "Rendering preserves inventory contents");
        check(context.items == 1, "Vanilla item draw preserved");
        check(context.fills.size() == (locked ? 13 : 0), "Only locked slots have one veil and one cross");
        if (locked) {
            var veil = context.fills.getFirst();
            check(veil.x1 == slot.x && veil.y1 == slot.y && veil.x2 == slot.x + 16 && veil.y2 == slot.y + 16,
                    "Veil covers exactly the slot");
            check((veil.color >>> 24) > 0 && (veil.color >>> 24) < 255, "Items remain visible through veil");
            boolean[][] pixels = new boolean[16][16];
            for (int i = 0; i < context.fills.size(); i++) {
                var fill = context.fills.get(i);
                check(fill.z == 300, "Overlay above slot items, below cursor and tooltips");
                if (i == 0) continue;
                check(fill.x1 >= slot.x && fill.y1 >= slot.y && fill.x2 <= slot.x + 16 && fill.y2 <= slot.y + 16,
                        "Cross stays within slot bounds");
                for (int x = fill.x1; x < fill.x2; x++)
                    for (int y = fill.y1; y < fill.y2; y++) pixels[x - slot.x][y - slot.y] = true;
            }
            for (int i = 2; i < 14; i++) check(pixels[i][i] && pixels[i][15 - i], "Both cross diagonals visible");
            check(!pixels[2][7] && !pixels[7][2], "Cross does not obscure whole item");
        }
        slots++;
    }

    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion();
            net.minecraft.Bootstrap.initialize();
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var unsafe = (Unsafe) field.get(null);
            var client = (MinecraftClient) unsafe.allocateInstance(MinecraftClient.class);
            set(null, MinecraftClient.class, "instance", client);
            var player = (Player) unsafe.allocateInstance(Player.class);
            var world = (ClientWorld) unsafe.allocateInstance(ClientWorld.class);
            set(world, net.minecraft.world.World.class, "isClient", true);
            set(player, Entity.class, "world", world);
            client.player = player;
            var inventory = new PlayerInventory(player);
            for (int i = 0; i < inventory.size(); i++) inventory.setStack(i, new ItemStack(Items.STONE, 12));
            var handlers = List.of(new PlayerScreenHandler(inventory, false, player),
                    GenericContainerScreenHandler.createGeneric9x3(1, inventory),
                    GenericContainerScreenHandler.createGeneric9x6(2, inventory));
            for (var currentMode : GameMode.values()) {
                mode = currentMode;
                for (var handler : handlers) {
                    var screen = new Screen(handler, inventory);
                    sync("inventory_lockdown", "inventory_lockdown", "no_sprint");
                    for (var slot : handler.slots) {
                        boolean locked = currentMode != GameMode.CREATIVE && currentMode != GameMode.SPECTATOR
                                && slot.inventory == inventory && slot.getIndex() >= 9 && slot.getIndex() <= 40;
                        draw(client, screen, slot, locked);
                    }
                    sync("no_sprint");
                    for (var slot : handler.slots) draw(client, screen, slot, false);
                }
            }
            mode = GameMode.SURVIVAL;
            var screen = new Screen(handlers.getFirst(), inventory);
            // Generic slots also cover empty armor/offhand without needing a sprite atlas in this fixture.
            for (int index : new int[]{0, 8, 9, 35, 36, 39, 40}) {
                inventory.setStack(index, ItemStack.EMPTY);
                var slot = new Slot(inventory, index, 8, 18);
                sync("inventory_lockdown");
                draw(client, screen, slot, index >= 9);
                ClientDataHolder.reset();
                draw(client, screen, slot, false);
            }
            System.out.println("INVENTORY_LOCKDOWN_CLIENT_PROBE_PASSED checks=" + checks + " slots=" + slots);
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }
}
