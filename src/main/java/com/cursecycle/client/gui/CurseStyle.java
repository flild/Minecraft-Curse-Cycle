package com.cursecycle.client.gui;

import com.cursecycle.challenge.Challenge;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.ChallengeUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/** Shared materials and vanilla item symbols for the wheel, journal and HUD. */
public final class CurseStyle {
    public static final int TEXT = 0xFFE8DFD0;
    public static final int MUTED = 0xFFAAA2B4;
    public static final int BRONZE = 0xFFAF8954;
    public static final int EDGE = 0xFF514635;
    public static final int PURPLE = 0xFFBB8DE5;
    public static final int PANEL = 0xF016151D;
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private CurseStyle() {}

    public static int color(Challenge challenge) {
        if (challenge == null) return PURPLE;
        return switch (challenge.getDifficulty()) {
            case EASY -> 0xFF90B998;
            case MEDIUM -> 0xFFD8B675;
            case HARD -> 0xFFDE857E;
        };
    }

    public static String name(String id) {
        Challenge c = ChallengeRegistry.get(ChallengeUtils.getBaseChallengeId(id));
        return c == null ? id : c.getName();
    }

    public static void icon(DrawContext context, String id, int x, int y) {
        String base = ChallengeUtils.getBaseChallengeId(id);
        context.drawItem(ICONS.computeIfAbsent(base, key -> new ItemStack(item(key))), x, y);
    }

    private static Item item(String id) {
        return switch (id) {
            case "no_sprint" -> Items.LEATHER_BOOTS;
            case "no_jumping", "heavy_body", "gravity_ten" -> Items.ANVIL;
            case "gills", "floor_is_water" -> Items.PUFFERFISH;
            case "the_floor_is_lava" -> Items.MAGMA_BLOCK;
            case "slippery_hands" -> Items.ICE;
            case "infinite_diet", "one_time_menu" -> Items.APPLE;
            case "all_debuffs", "random_potion" -> Items.POTION;
            case "no_healing", "one_heart", "heartbeat" -> Items.GLISTERING_MELON_SLICE;
            case "damage_slot_drop", "inventory_lockdown", "locked_containers" -> Items.CHEST;
            case "damage_teleport" -> Items.ENDER_PEARL;
            case "bitter_experience", "crafting_tax" -> Items.EXPERIENCE_BOTTLE;
            case "attack_recoil" -> Items.IRON_SWORD;
            case "armor_disabled", "netherite_mobs" -> Items.NETHERITE_CHESTPLATE;
            case "no_block_breaking" -> Items.IRON_PICKAXE;
            case "no_block_placing", "fragile_floor" -> Items.CRACKED_STONE_BRICKS;
            case "sunlight_burn" -> Items.BLAZE_POWDER;
            case "invisible_mobs", "blind_hud" -> Items.ENDER_EYE;
            case "tiny_fov", "offset_crosshair" -> Items.SPYGLASS;
            case "silent_world" -> Items.SCULK_SENSOR;
            case "endless_raid" -> Items.OMINOUS_BOTTLE;
            case "double_challenge" -> Items.ECHO_SHARD;
            case "hotbar_shuffle", "inverted_keyboard", "inverted_mouse", "blocked_direction",
                    "movement_memory", "upside_down_camera", "forced_third_person_front",
                    "forced_third_person_rpg" -> Items.COMPASS;
            case "auto_jump", "adhd_no_standing_still" -> Items.RABBIT_FOOT;
            default -> Items.AMETHYST_SHARD;
        };
    }

    public static void panel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x + 3, y + 3, x + width + 3, y + height + 3, 0x45000000);
        context.fill(x, y, x + width, y + height, PANEL);
        context.drawBorder(x, y, width, height, EDGE);
        context.fill(x + 1, y + 1, x + width - 1, y + 2, BRONZE);
    }

    public static void backdrop(DrawContext context, int width, int height) {
        context.fill(0, 0, width, height, 0xC00B0911);
    }

    public static String trim(TextRenderer renderer, String value, int width) {
        if (width <= 0) return "";
        return renderer.getWidth(value) <= width ? value
                : renderer.trimToWidth(value, Math.max(0, width - renderer.getWidth("…"))) + "…";
    }

    public static int wrapped(DrawContext context, TextRenderer renderer, Text text,
                              int x, int y, int width, int color) {
        for (OrderedText line : renderer.wrapLines(text, width)) {
            context.drawTextWithShadow(renderer, line, x, y, color);
            y += 11;
        }
        return y;
    }

    public static void arc(DrawContext context, float x, float y, float inner, float outer,
                           double start, double end, int innerColor, int outerColor) {
        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP,
                VertexFormats.POSITION_COLOR);
        int steps = Math.max(1, (int) Math.ceil(Math.abs(end - start) / 4));
        for (int i = 0; i <= steps; i++) {
            double radians = Math.toRadians(start + (end - start) * i / steps);
            float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
            buffer.vertex(matrix, x + cos * outer, y + sin * outer, 0).color(outerColor);
            buffer.vertex(matrix, x + cos * inner, y + sin * inner, 0).color(innerColor);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    public static void disk(DrawContext context, float x, float y, float radius, int color) {
        arc(context, x, y, 0, radius, 0, 360, color, color);
    }

    public static void diamond(DrawContext context, float x, float y, float size, int color) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(45));
        int r = Math.max(1, Math.round(size * .7071f));
        context.fill(-r, -r, r, r, color);
        context.getMatrices().pop();
    }
}
