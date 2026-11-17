package validation;

import com.cursecycle.client.ClientDataHolder;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.Items;
import net.minecraft.world.GameMode;
import sun.misc.Unsafe;

import java.util.List;

/** Runs the transformed HungerManager in Fabric CLIENT without opening a game window. */
public final class InfiniteDietProbe implements PreLaunchEntrypoint {
    private static GameMode mode = GameMode.SURVIVAL;
    private static HungerManager hunger;
    private static int checks;

    public static class Player extends ClientPlayerEntity {
        public Player() { super(null, null, null, null, null, false, false); }
        @Override public boolean isCreative() { return mode == GameMode.CREATIVE; }
        @Override public boolean isSpectator() { return mode == GameMode.SPECTATOR; }
        @Override public HungerManager getHungerManager() { return hunger; }
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static void sync(String... ids) {
        ClientDataHolder.updateState(1, 20, List.of(ids), "", List.of());
    }

    private static void nutrition(int food, float saturation) {
        hunger.setFoodLevel(food);
        hunger.setSaturationLevel(saturation);
    }

    private static void assertNutrition(int food, float saturation, String message) {
        check(hunger.getFoodLevel() == food, message + " (food)");
        check(Math.abs(hunger.getSaturationLevel() - saturation) < 0.0001F, message + " (saturation)");
    }

    @Override public void onPreLaunch() {
        try {
            net.minecraft.SharedConstants.createGameVersion();
            net.minecraft.Bootstrap.initialize();
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var unsafe = (Unsafe) field.get(null);
            var client = (MinecraftClient) unsafe.allocateInstance(MinecraftClient.class);
            field = MinecraftClient.class.getDeclaredField("instance");
            field.setAccessible(true);
            field.set(null, client);
            hunger = new HungerManager();
            client.player = (Player) unsafe.allocateInstance(Player.class);

            ClientDataHolder.reset();
            nutrition(10, 2);
            hunger.eat(Items.COOKED_BEEF.getComponents().get(DataComponentTypes.FOOD));
            assertNutrition(18, 14.8F, "Unsynced food uses vanilla prediction");

            for (var currentMode : List.of(GameMode.SURVIVAL, GameMode.ADVENTURE)) {
                mode = currentMode;
                for (var ids : List.of(List.of("infinite_diet"), List.of("infinite_diet", "no_sprint"))) {
                    sync(ids.toArray(String[]::new));
                    nutrition(10, 2);
                    for (var food : List.of(Items.COOKED_BEEF, Items.GOLDEN_APPLE, Items.PUFFERFISH, Items.HONEY_BOTTLE)) {
                        hunger.eat(food.getComponents().get(DataComponentTypes.FOOD));
                        assertNutrition(10, 2, currentMode + " food cannot briefly raise HUD nutrition");
                    }
                    hunger.add(2, 0.1F); // Cake prediction uses this overload.
                    assertNutrition(10, 2, "Cake cannot briefly raise HUD nutrition");

                    // Same setters used by vanilla health packets: respawn/recovery must still work.
                    nutrition(20, 5);
                    assertNutrition(20, 5, "Server increases remain authoritative during diet");
                    nutrition(7, 0);
                    assertNutrition(7, 0, "Server decreases remain authoritative during diet");
                    hunger.add(8, 0.8F);
                    assertNutrition(7, 0, "Prediction stays blocked after a server update");
                }
            }

            for (var currentMode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
                mode = currentMode;
                nutrition(10, 2);
                hunger.add(2, 0.5F);
                assertNutrition(12, 4, currentMode + " remains exempt");
            }
            mode = GameMode.SURVIVAL;
            sync("infinite_diet");
            var otherHunger = new HungerManager();
            otherHunger.setFoodLevel(10);
            otherHunger.setSaturationLevel(2);
            otherHunger.add(2, 0.5F);
            check(otherHunger.getFoodLevel() == 12 && otherHunger.getSaturationLevel() == 4,
                    "Other hunger managers, including integrated-server players, remain independent");
            sync("no_sprint");
            nutrition(10, 2);
            hunger.add(2, 0.5F);
            assertNutrition(12, 4, "Ending diet immediately releases prediction");
            sync("infinite_diet");
            ClientDataHolder.reset();
            nutrition(10, 2);
            hunger.add(2, 0.5F);
            assertNutrition(12, 4, "Disconnect clears prediction restriction");
            client.player = null;
            sync("infinite_diet");
            hunger.add(2, 0.5F);
            assertNutrition(14, 6, "No player is safe during transitions");

            System.out.println("INFINITE_DIET_CLIENT_PROBE_PASSED checks=" + checks);
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }
}
