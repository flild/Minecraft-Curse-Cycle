package com.cursecycle.client;

import com.cursecycle.CurseCycleMod;
import com.cursecycle.challenge.impl.BlackHoleChallenge;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Downloaded CC0 accretion mesh, opaque core, world-space field and moving block fragments. */
public final class BlackHoleRenderer {
    private static VertexBuffer disk, core;
    private static ShaderProgram diskShader;
    private static net.minecraft.client.world.ClientWorld groundWorld;
    private static final Map<Integer, GroundRing> GROUND = new HashMap<>();
    private record GroundRing(int sampled, Vec3d center, Vec3d[] points) {}
    private static float[][] vertices = new float[0][];
    private static int[][] colors = new int[0][];
    private BlackHoleRenderer() {}
    public static void initialize() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(Identifier.of("cursecycle", "black_hole_disk"),
                VertexFormats.POSITION_COLOR, shader -> diskShader = shader));
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return Identifier.of("cursecycle", "black_hole_model"); }
            @Override public void reload(ResourceManager manager) {
                if (disk != null) { disk.close(); disk = null; }
                if (core != null) { core.close(); core = null; }
                try (var input = new DataInputStream(manager.getResource(Identifier.of("cursecycle", "models/black_hole.bin"))
                        .orElseThrow().getInputStream())) {
                    if (input.readInt() != 0x42484D31) throw new IOException("Invalid black hole mesh");
                    int count = input.readInt();
                    if (count < 1 || count > 16) throw new IOException("Invalid mesh count");
                    vertices = new float[count][]; colors = new int[count][];
                    for (int layer = 0; layer < count; layer++) {
                        int size = input.readInt();
                        if (size < 3 || size > 100_000 || size % 3 != 0) throw new IOException("Invalid mesh size");
                        colors[layer] = new int[]{input.readInt(), input.readInt(), input.readInt()};
                        vertices[layer] = new float[size * 3];
                        for (int i = 0; i < size * 3; i++) vertices[layer][i] = input.readFloat();
                    }
                } catch (IOException | RuntimeException failure) {
                    vertices = new float[0][];
                    CurseCycleMod.LOGGER.error("Could not load black hole model", failure);
                }
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(BlackHoleRenderer::fragments);
        WorldRenderEvents.LAST.register(BlackHoleRenderer::render);
    }
    private static void buffers() {
        if (disk != null || vertices.length == 0) return;
        try (var allocator = new BufferAllocator(1024 * 1024)) {
            var builder = new BufferBuilder(allocator, VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            for (int layer = 0; layer < vertices.length; layer++) {
                var data = vertices[layer]; var c = colors[layer];
                for (int v = 0; v < data.length; v += 3)
                    builder.vertex(data[v], data[v + 1], data[v + 2]).color(c[0], c[1], c[2], 255);
            }
            disk = new VertexBuffer(VertexBuffer.Usage.STATIC); disk.bind(); disk.upload(builder.end());
            builder = new BufferBuilder(allocator, VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            for (int lat = 0; lat < 24; lat++) for (int lon = 0; lon < 48; lon++) {
                var a = sphere(lat, lon); var b = sphere(lat + 1, lon);
                var c = sphere(lat + 1, lon + 1); var d = sphere(lat, lon + 1);
                for (var point : new Vec3d[]{a, b, c, a, c, d})
                    builder.vertex((float) point.x, (float) point.y, (float) point.z).color(0, 0, 0, 255);
            }
            core = new VertexBuffer(VertexBuffer.Usage.STATIC); core.bind(); core.upload(builder.end());
            VertexBuffer.unbind();
        }
    }
    private static Vec3d sphere(int lat, int lon) {
        double a = Math.PI * lat / 24, b = Math.PI * 2 * lon / 48;
        return new Vec3d(Math.sin(a) * Math.cos(b), Math.cos(a), Math.sin(a) * Math.sin(b));
    }
    private static void render(WorldRenderContext context) {
        if (groundWorld != context.world()) { GROUND.clear(); groundWorld = context.world(); }
        if (BlackHoleVisuals.HOLES.isEmpty()) { GROUND.clear(); return; }
        buffers(); if (disk == null || core == null || diskShader == null) return;
        GROUND.keySet().retainAll(BlackHoleVisuals.HOLES.keySet());
        float delta = context.tickCounter().getTickDelta(false), time = BlackHoleVisuals.time(delta);
        var camera = context.camera().getPos(); var matrices = context.matrixStack();
        if (matrices == null) return;
        RenderSystem.enableDepthTest(); RenderSystem.disableCull(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        try {
            // Opaque cores write depth so the far half of each disk is genuinely occluded.
            RenderSystem.depthMask(true);
            for (var hole : BlackHoleVisuals.HOLES.values()) {
                var pos = hole.position(delta);
                if (pos.squaredDistanceTo(camera) > 128 * 128) continue;
                float formation = 1 - hole.warning(delta) / BlackHoleChallenge.WARNING;
                float size = (float) BlackHoleChallenge.CORE_RADIUS * (.25F + .75F * formation);
                var matrix = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(matrices.peek().getPositionMatrix()).translate((float) (pos.x - camera.x),
                        (float) (pos.y - camera.y), (float) (pos.z - camera.z)).scale(size);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                core.bind(); core.draw(matrix, context.projectionMatrix(), GameRenderer.getPositionColorProgram());
            }
            RenderSystem.depthMask(false);
            var diskTime = diskShader.getUniform("DiskTime");
            if (diskTime != null) diskTime.set(time / 20);
            for (var hole : BlackHoleVisuals.HOLES.values()) {
                var pos = hole.position(delta);
                if (pos.squaredDistanceTo(camera) > 128 * 128) continue;
                float formation = 1 - hole.warning(delta) / BlackHoleChallenge.WARNING;
                var matrix = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(matrices.peek().getPositionMatrix()).translate((float) (pos.x - camera.x),
                        (float) (pos.y - camera.y), (float) (pos.z - camera.z))
                        .rotateY(time * .014F + hole.id).scale(6 * (.25F + .75F * formation));
                RenderSystem.setShaderColor(1, 1, 1, .45F + .55F * formation);
                disk.bind(); disk.draw(matrix, context.projectionMatrix(), diskShader);
            }
            VertexBuffer.unbind(); RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            var builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (var hole : BlackHoleVisuals.HOLES.values()) {
                var pos = hole.position(delta).subtract(camera);
                if (pos.lengthSquared() > 128 * 128) continue;
                boolean warning = hole.warning(delta) > 0;
                int r = warning ? 255 : 153, g = warning ? 180 : 126, b = warning ? 85 : 248;
                int alpha = warning ? (int) (100 + 45 * Math.sin(time * .3)) : 90;
                // Three great circles + two latitude arcs show a sphere, not a misleading flat cylinder.
                for (int plane = 0; plane < 3; plane++) for (int i = 0; i < 128; i++) {
                    if ((i / 8) % 2 != 0) continue;
                    double a = i * Math.PI / 64, end = (i + 1) * Math.PI / 64;
                    line(builder, matrices.peek().getPositionMatrix(), pos.add(circle(plane, a, 24)),
                            pos.add(circle(plane, end, 24)), .045, r, g, b, alpha);
                }
                for (int hemisphere : new int[]{-1, 1}) for (int i = 0; i < 96; i++) {
                    if ((i / 6) % 2 != 0) continue;
                    double a = i * Math.PI / 48, end = (i + 1) * Math.PI / 48;
                    double radius = Math.sqrt(24 * 24 - 12 * 12);
                    line(builder, matrices.peek().getPositionMatrix(), pos.add(Math.cos(a) * radius, hemisphere * 12, Math.sin(a) * radius),
                            pos.add(Math.cos(end) * radius, hemisphere * 12, Math.sin(end) * radius), .035, r, g, b, alpha / 2);
                }
                var ground = ground(context, hole.id, hole.position(delta), (int) time);
                for (int i = 0; i < ground.length; i++) {
                    var a = ground[i]; var next = ground[(i + 1) % ground.length];
                    if (a == null || next == null || a.distanceTo(next) > 5 || (i / 5) % 2 != 0) continue;
                    line(builder, matrices.peek().getPositionMatrix(), a.subtract(camera), next.subtract(camera), .12,
                            r, g, b, warning ? 190 : 165);
                }
                // Dust ribbons spiral from the field edge towards the accretion disk.
                if (!warning) for (int stream = 0; stream < 12; stream++) for (int step = 0; step < 18; step++) {
                    double phase = (time * .008 + stream / 12.0 + step / 90.0) % 1;
                    var a = flow(stream, phase); var bpos = flow(stream, Math.min(.9999, phase + .009));
                    line(builder, matrices.peek().getPositionMatrix(), pos.add(a), pos.add(bpos), .025,
                            205, 156, 255, (int) (85 * (1 - phase)));
                }
            }
            var built = builder.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            VertexBuffer.unbind(); RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend();
        }
    }
    /** Cache terrain raycasts; points remain on the actual sphere, including in caves. */
    private static Vec3d[] ground(WorldRenderContext context, int id, Vec3d center, int tick) {
        var old = GROUND.get(id);
        if (old != null && tick - old.sampled < 10 && old.center.squaredDistanceTo(center) < 4) return old.points;
        var points = new Vec3d[96];
        for (int i = 0; i < points.length; i++) {
            double angle = i * Math.PI * 2 / points.length, radius = BlackHoleChallenge.RADIUS;
            for (int attempt = 0; attempt < 3; attempt++) {
                var at = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                var hit = context.world().raycast(new RaycastContext(at.add(0, 6, 0), at.add(0, -24, 0),
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, MinecraftClient.getInstance().player));
                if (hit.getType() == HitResult.Type.MISS) break;
                double y = hit.getPos().y + .04, dy = y - center.y;
                if (Math.abs(dy) >= BlackHoleChallenge.RADIUS) break;
                radius = Math.sqrt(BlackHoleChallenge.RADIUS * BlackHoleChallenge.RADIUS - dy * dy);
                points[i] = new Vec3d(center.x + Math.cos(angle) * radius, y, center.z + Math.sin(angle) * radius);
            }
        }
        GROUND.put(id, new GroundRing(tick, center, points)); return points;
    }
    private static Vec3d circle(int plane, double angle, double radius) {
        double a = Math.cos(angle) * radius, b = Math.sin(angle) * radius;
        return switch (plane) { case 0 -> new Vec3d(a, 0, b); case 1 -> new Vec3d(a, b, 0); default -> new Vec3d(0, a, b); };
    }
    private static Vec3d flow(int stream, double phase) {
        double radius = 24 - phase * 22, angle = stream * Math.PI / 6 + phase * Math.PI * 3;
        double elevation = Math.sin(stream * 2.4) * .65 * (1 - phase);
        return new Vec3d(Math.cos(angle) * radius * Math.cos(elevation), radius * Math.sin(elevation),
                Math.sin(angle) * radius * Math.cos(elevation));
    }
    private static void line(BufferBuilder builder, Matrix4f matrix, Vec3d a, Vec3d b, double width, int r, int g, int blue, int alpha) {
        var direction = b.subtract(a);
        // Ribbon faces the camera; custom geometry avoids platform-dependent OpenGL line widths.
        var side = direction.crossProduct(a.add(b).multiply(.5)).normalize().multiply(width);
        if (side.lengthSquared() < 1e-12) side = new Vec3d(width, 0, 0);
        for (var p : new Vec3d[]{a.add(side), a.subtract(side), b.subtract(side), b.add(side)})
            builder.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(r, g, blue, alpha);
    }
    private static void fragments(WorldRenderContext context) {
        if (BlackHoleVisuals.FRAGMENTS.isEmpty() || context.consumers() == null || context.matrixStack() == null) return;
        var client = MinecraftClient.getInstance(); float delta = context.tickCounter().getTickDelta(false);
        var camera = context.camera().getPos(); MatrixStack matrices = context.matrixStack();
        for (var fragment : BlackHoleVisuals.FRAGMENTS) {
            var hole = BlackHoleVisuals.HOLES.get(fragment.sample().holeId());
            if (hole == null) continue;
            float age = fragment.age(delta), progress = Math.min(1, age / 40);
            var start = Vec3d.ofCenter(fragment.sample().pos()); var end = hole.position(delta);
            double bend = Math.sin(progress * Math.PI);
            var offset = start.subtract(end);
            var pos = start.lerp(end, progress * progress).add(-offset.z * .22 * bend, 2 * bend, offset.x * .22 * bend);
            float size = .9F * (1 - progress * progress);
            matrices.push();
            matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(age * .09F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(age * .04F)); matrices.scale(size, size, size);
            matrices.translate(-.5, -.5, -.5);
            client.getBlockRenderManager().renderBlockAsEntity(Block.getStateFromRawId(fragment.sample().blockState()),
                    matrices, context.consumers(), LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }
}
