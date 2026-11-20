package com.cursecycle.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.cursecycle.challenge.impl.MeteorShowerChallenge;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Emissive fissures, faceted tumbling meteors, animated CC0 fire and bounded eruption plumes. */
public final class MeteorShowerRenderer {
    private static final Identifier FIRE = Identifier.of("cursecycle", "textures/effect/meteor_fire.png");
    private static ShaderProgram plumeShader;
    private MeteorShowerRenderer() {}
    public static void initialize() {
        CoreShaderRegistrationCallback.EVENT.register(c -> c.register(Identifier.of("cursecycle", "meteor_plume"),
                VertexFormats.POSITION_TEXTURE_COLOR, program -> plumeShader = program));
        WorldRenderEvents.LAST.register(MeteorShowerRenderer::render);
    }
    private static void draw(BufferBuilder b) { var built = b.endNullable(); if (built != null) BufferRenderer.drawWithGlobalProgram(built); }
    private static void render(WorldRenderContext context) {
        if (MeteorShowerVisuals.STRIKES.isEmpty() || context.matrixStack() == null) return;
        float delta = context.tickCounter().getTickDelta(false), time = MeteorShowerVisuals.time(delta);
        var camera = context.camera().getPos(); var matrix = context.matrixStack().peek().getPositionMatrix();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            var b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (var strike : MeteorShowerVisuals.STRIKES.values()) {
                var s = strike.sample; if (s.target().squaredDistanceTo(camera) > 96 * 96) continue;
                var origin = s.target().subtract(camera).add(0, .035, 0);
                float age = strike.age(delta), impact = strike.impactAge(delta);
                if (impact < 0) {
                    float charge = Math.min(1, Math.max(0, age / MeteorShowerChallenge.WARNING));
                    int alpha = (int) (120 + 100 * charge * (.5 + .5 * Math.sin(time * .5)));
                    // The outer ring is the vanilla explosion reach, the inner ring the sustained jet.
                    ring(b, matrix, origin, 5, .035, 255, 112, 40, 75, true);
                    ring(b, matrix, origin, 1.8, .08, 255, 90, 20, alpha, false);
                    ring(b, matrix, origin, 1.8 * (1 - charge), .06, 255, 223, 120, 170, false);
                    for (int crack = 0; crack < 7; crack++) {
                        double a = crack * Math.PI * 2 / 7 + s.id();
                        Vec3d last = origin;
                        for (int step = 1; step <= 4; step++) {
                            double twist = a + Math.sin(crack * 17 + step * 3) * .23;
                            var next = origin.add(Math.cos(twist) * step * .42, .002 * step, Math.sin(twist) * step * .42);
                            line(b, matrix, last, next, .035 + .045 * charge, 255, (int) (85 + 125 * charge), 35, alpha); last = next;
                        }
                    }
                    if (age >= 60 && !s.geyser()) rock(b, matrix, strike.position(delta).subtract(camera), time, s.id());
                } else {
                    float life = Math.max(0, 1 - impact / (s.geyser() ? 30 : 20));
                    var hit = strike.position(delta).subtract(camera);
                    ring(b, matrix, hit.add(0, .05, 0), .6 + impact * .25, .12 * life, 255, 175, 70, (int) (190 * life), false);
                    // Ballistic hot shards are visual only: they cannot duplicate/re-place world blocks.
                    for (int shard = 0; shard < 12; shard++) {
                        double a = shard * Math.PI * 2 / 12 + s.id(), t = impact / 20;
                        var p = hit.add(Math.cos(a) * t * 5, Math.max(0, 4 * t - 3 * t * t), Math.sin(a) * t * 5);
                        line(b, matrix, p, p.add(-Math.cos(a) * .3, -.12, -Math.sin(a) * .3), .08 * life, 255, 190, 85, (int) (230 * life));
                    }
                }
            }
            draw(b);
            if (plumeShader != null) {
                RenderSystem.setShader(() -> plumeShader);
                var clock = plumeShader.getUniform("EffectTime"); if (clock != null) clock.set(time / 20);
                b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                for (var strike : MeteorShowerVisuals.STRIKES.values()) {
                    float impact = strike.impactAge(delta); var s = strike.sample;
                    if (!s.geyser() || impact < 0 || impact >= 30 || s.target().squaredDistanceTo(camera) > 96 * 96) continue;
                    float fade = Math.min(1, (30 - impact) / 7), grow = Math.min(1, (impact + 1) / 4);
                    plume(b, matrix, s.target().subtract(camera), s.height() * grow, time, s.id(), fade);
                }
                draw(b);
            }
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram); RenderSystem.setShaderTexture(0, FIRE);
            b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            var rotation = context.camera().getRotation();
            var right = new Vector3f(1, 0, 0).rotate(rotation); var up = new Vector3f(0, 1, 0).rotate(rotation);
            for (var strike : MeteorShowerVisuals.STRIKES.values()) {
                var s = strike.sample; if (s.target().squaredDistanceTo(camera) > 96 * 96) continue;
                float age = strike.age(delta), impact = strike.impactAge(delta);
                var target = s.target().subtract(camera);
                if (impact < 0 && age < 60) {
                    // Small rising wisps announce that this is a vent, before the ground bursts.
                    if (s.geyser()) for (int wisp = 0; wisp < 3; wisp++) {
                        float phase = (time * .035F + wisp / 3F) % 1;
                        sprite(b, matrix, target.add(Math.sin(wisp * 4 + s.id()) * .5, .2 + phase * .9, Math.cos(wisp * 4 + s.id()) * .5),
                                right, up, .45F + phase * .25F, .5F, 8 + (int) (phase * 7), (int) (95 * (1 - phase)));
                    }
                } else if (impact < 0 && !s.geyser()) {
                    var pos = strike.position(delta).subtract(camera);
                    for (int trail = 9; trail >= 0; trail--) sprite(b, matrix, pos.add(Math.sin(trail * .6 + s.id()) * .12,
                            trail * .65, Math.cos(trail * .6 + s.id()) * .12), right, up,
                            1F - trail * .055F, 2F, 2 + (int) (time * .6 + trail) % 5, 170 - trail * 12);
                } else if (impact >= 0) {
                    var pos = strike.position(delta).subtract(camera);
                    if (s.geyser() && impact < 30) {
                        float fade = Math.min(1, (30 - impact) / 7), grow = Math.min(1, (impact + 1) / 4);
                        double height = s.height() * grow;
                        // Texture wisps add irregular smoke and fire around the volumetric shader jet.
                        for (int layer = 0; layer < 5; layer++) {
                            float h = layer / 5F;
                            double y = Math.min(height - .1, .4 + h * height);
                            if (y <= 0) continue;
                            double swirl = time * .18 + layer * 1.6 + s.id();
                            sprite(b, matrix, pos.add(Math.cos(swirl) * h * .25, y, Math.sin(swirl) * h * .25),
                                    right, up, (.9F - h * .45F) * fade, (float) Math.max(.01, Math.min(2.3, Math.min(y, height - y))),
                                    3 + (int) (time * .7 + layer * 2 + s.id()) % 5, (int) (115 * fade));
                        }
                    }
                    if (impact < 20) sprite(b, matrix, pos.add(0, 1, 0), right, up, 2.6F, 2.6F,
                            Math.min(15, (int) (impact * .8)), (int) (255 * Math.min(1, (20 - impact) / 4)));
                }
            }
            draw(b);
        } finally {
            RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend(); RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }
    private static void plume(BufferBuilder b, Matrix4f m, Vec3d p, double height, float time, int id, float fade) {
        for (int ring = 0; ring < 14; ring++) for (int side = 0; side < 20; side++) {
            for (int corner = 0; corner < 4; corner++) {
                float h = (ring + (corner == 1 || corner == 2 ? 1 : 0)) / 14F;
                float u = (side + (corner >= 2 ? 1 : 0)) / 20F;
                double a = u * Math.PI * 2, swirl = h * 7 - time * .17 + id;
                double radius = (.85 - h * .62) * (1 + .16 * Math.sin(a * 5 + swirl * 2));
                double x = p.x + Math.cos(a) * radius + Math.sin(swirl) * h * .3;
                double z = p.z + Math.sin(a) * radius + Math.cos(swirl * 1.3) * h * .3;
                b.vertex(m, (float) x, (float) (p.y + .04 + h * Math.max(0, height - .04)), (float) z)
                        .texture(u, h).color(255, Math.floorMod(id * 17, 255), 0, (int) (220 * fade));
            }
        }
    }
    private static void sprite(BufferBuilder b, Matrix4f m, Vec3d p, Vector3f right, Vector3f up,
                               float width, float height, int frame, int alpha) {
        frame = Math.floorMod(frame, 16); float u = (frame % 4) * .25F, v = (frame / 4) * .25F;
        for (int i = 0; i < 4; i++) {
            float x = i == 0 || i == 3 ? -width : width, y = i < 2 ? -height : height;
            b.vertex(m, (float) p.x + right.x * x + up.x * y, (float) p.y + right.y * x + up.y * y,
                    (float) p.z + right.z * x + up.z * y).texture(u + (x > 0 ? .249F : .001F), v + (y > 0 ? .001F : .249F)).color(255, 255, 255, Math.max(0, alpha));
        }
    }
    private static void ring(BufferBuilder b, Matrix4f m, Vec3d p, double radius, double width, int r, int g, int blue, int alpha, boolean dashed) {
        for (int i = 0; i < 80; i++) {
            if (dashed && i % 10 >= 6) continue;
            double a = i * Math.PI / 40, next = (i + 1) * Math.PI / 40;
            line(b, m, p.add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    p.add(Math.cos(next) * radius, 0, Math.sin(next) * radius), width, r, g, blue, alpha);
        }
    }
    private static void line(BufferBuilder b, Matrix4f m, Vec3d a, Vec3d end, double width, int r, int g, int blue, int alpha) {
        var side = end.subtract(a).crossProduct(a.add(end).multiply(.5)).normalize().multiply(width);
        for (var p : new Vec3d[]{a.add(side), end.add(side), end.subtract(side), a.subtract(side)})
            b.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, blue, Math.max(0, alpha));
    }
    private static Vec3d stone(int lat, int lon, float time, int id) {
        double a = lat * Math.PI / 8, angle = lon * Math.PI / 6 + time * .07 + id;
        double radius = .6 + .12 * Math.sin(lat * 13 + lon * 17 + id);
        return new Vec3d(Math.sin(a) * Math.cos(angle) * radius, Math.cos(a) * radius, Math.sin(a) * Math.sin(angle) * radius);
    }
    private static void rock(BufferBuilder b, Matrix4f m, Vec3d p, float time, int id) {
        for (int lat = 0; lat < 8; lat++) for (int lon = 0; lon < 12; lon++) {
            int shade = 40 + Math.floorMod(lat * 31 + lon * 17, 50);
            for (var v : new Vec3d[]{stone(lat, lon, time, id), stone(lat + 1, lon, time, id),
                    stone(lat + 1, lon + 1, time, id), stone(lat, lon + 1, time, id)})
                b.vertex(m, (float) (p.x + v.x), (float) (p.y + v.y), (float) (p.z + v.z)).color(shade + 50, shade / 2, shade / 4, 255);
            if ((lat + lon) % 3 == 0) line(b, m, p.add(stone(lat, lon, time, id).multiply(1.02)),
                    p.add(stone(lat + 1, lon, time, id).multiply(1.02)), .025, 255, 190, 70, 255);
        }
    }
}
