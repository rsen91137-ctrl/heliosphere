package com.heliosphere.core.client.fx;

import com.heliosphere.core.env.Environment;
import com.heliosphere.core.fx.sim.FxAtlas;
import com.heliosphere.core.fx.sim.MushroomCloud;
import com.heliosphere.core.fx.sim.SpriteSink;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The shared effects renderer. An {@link Effect} is a pure function of its age that hands sprites to a
 * {@link SpriteSink}; the engine sorts every sprite of every live effect back to front and draws them in a
 * single pass at the very end of the world render, after the vanilla clouds and weather, so smoke is never
 * painted over by something behind it.
 *
 * <p>One blend mode serves everything: premultiplied alpha. A sprite's alpha says how much of the
 * background it hides and its colour is simply added, so dark smoke, glowing fire and pure light all live
 * in one sorted list.
 */
public final class FxEngine {
    private FxEngine() {}

    /** One effect in the world. */
    public interface Effect {
        /** Emits this frame's sprites. Returns false once the effect is over. */
        boolean emit(SpriteSink sink, float age, MushroomCloud.Env env);
    }

    private record Live(Effect effect, double born, Object level) {}

    private static final List<Live> LIVE = new ArrayList<>();
    private static final int MAX_LIVE = 700;

    private static double clock;
    private static long lastNanos;

    // whole-screen flash (detonations): colour and a level that decays
    private static float flashLevel, flashDecay = 1f;
    private static int flashRgb = 0xFFFFFF;
    // persistent tint (radiation sickness and the like), set every tick by whoever needs it
    private static float tintLevel;
    private static int tintRgb;

    /** Seconds on the effect clock (stops while the game is paused). */
    public static double clock() { return clock; }

    /** Starts an effect now. Call on the client thread. */
    public static void add(Effect e) { add(e, 0); }

    /** Starts an effect that is already {@code age} seconds old (for viewers who arrive late). */
    public static void add(Effect e, float age) {
        if (LIVE.size() >= MAX_LIVE) LIVE.remove(0);
        LIVE.add(new Live(e, clock - age, Minecraft.getInstance().level));
    }

    /** A whole-screen flash that fades over roughly {@code seconds}. The brightest request wins. */
    public static void flash(float level, int rgb, float seconds) {
        if (level > flashLevel) { flashLevel = Math.min(1, level); flashRgb = rgb; flashDecay = Math.max(0.05f, seconds); }
    }

    /** A screen tint that lasts while it keeps being requested. */
    public static void tint(float level, int rgb) {
        if (level > tintLevel) { tintLevel = Math.min(0.8f, level); tintRgb = rgb; }
    }

    // ---- GPU objects --------------------------------------------------------------------------------------------

    private static final Identifier ATLAS_ID = Identifier.fromNamespaceAndPath("heliosphere", "fx_atlas");
    private static RenderType type;
    private static MultiBufferSource.BufferSource buffers;

    private static void init() {
        float[] px = FxAtlas.pixels();
        NativeImage img = new NativeImage(FxAtlas.SIZE, FxAtlas.SIZE, false);
        for (int y = 0; y < FxAtlas.SIZE; y++) {
            for (int x = 0; x < FxAtlas.SIZE; x++) {
                int i = (y * FxAtlas.SIZE + x) * 4;
                int r = (int) (px[i] * 255f + 0.5f), g = (int) (px[i + 1] * 255f + 0.5f), b = (int) (px[i + 2] * 255f + 0.5f), a = (int) (px[i + 3] * 255f + 0.5f);
                img.setPixelABGR(x, y, a << 24 | b << 16 | g << 8 | r);
            }
        }
        Minecraft.getInstance().getTextureManager().register(ATLAS_ID, new DynamicTexture(() -> "heliosphere fx atlas", img));
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("heliosphere", "pipeline/fx"))
                .withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color")
                .withSampler("Sampler0")
                .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
                .withDepthWrite(false).withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build());
        type = RenderType.create("heliosphere_fx", RenderSetup.builder(pipeline)
                .withTexture("Sampler0", ATLAS_ID, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                .bufferSize(1 << 20)
                .createRenderSetup());
        buffers = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 20));
    }

    // ---- frame --------------------------------------------------------------------------------------------------

    private static final Batch BATCH = new Batch();
    private static final MushroomCloud.Env ENV = new MushroomCloud.Env();

    /** Called once per frame at the end of the world render (see LevelRendererFxMixin). */
    public static void render(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.25f, (now - lastNanos) * 1e-9f);
        lastNanos = now;
        if (!mc.isPaused()) {
            clock += dt;
            flashLevel = Math.max(0, flashLevel - dt / flashDecay * Math.max(0.15f, flashLevel));
            tintLevel = Math.max(0, tintLevel - dt * 0.6f);
        }
        ClientLevel level = mc.level;
        if (level == null) { LIVE.clear(); return; }
        if (LIVE.isEmpty()) return;
        if (type == null) init();

        ENV.daylight = (float) Math.max(0, Math.min(1, Environment.daylight(level, camera.blockPosition())));
        ParticleStatus status = mc.options.particles().get();
        ENV.detail = status == ParticleStatus.ALL ? 1f : status == ParticleStatus.DECREASED ? 0.65f : 0.4f;

        BATCH.proxy = Math.max(256f, Math.min(1500f, mc.options.getEffectiveRenderDistance() * 16f * 2f));
        BATCH.begin(camera);
        for (Iterator<Live> it = LIVE.iterator(); it.hasNext(); ) {
            Live l = it.next();
            if (l.level != level) { it.remove(); continue; }
            float age = (float) (clock - l.born);
            if (age < 0) continue;                  // scheduled for later (a bullet still in flight)
            boolean alive;
            try {
                alive = l.effect.emit(BATCH, age, ENV);
            } catch (RuntimeException e) {
                alive = false;                      // a broken effect must never take the frame down with it
            }
            if (!alive) it.remove();
        }
        if (BATCH.count == 0) return;
        BATCH.draw(buffers.getBuffer(type));
        buffers.endBatch();
    }

    /** Draws the screen flash and tint. Called from the HUD pass. */
    public static void hud(GuiGraphics g) {
        if (tintLevel > 0.004f) g.fill(0, 0, g.guiWidth(), g.guiHeight(), (int) (tintLevel * 255f) << 24 | tintRgb);
        if (flashLevel > 0.004f) g.fill(0, 0, g.guiWidth(), g.guiHeight(), (int) (flashLevel * 255f) << 24 | flashRgb);
    }

    /** Collects a frame's sprites as camera-relative quads, then sorts and writes them. */
    private static final class Batch implements SpriteSink {
        static final int STRIDE = 12 + 2 + 4;       // 4 corners xyz, tile u0/v0, premultiplied rgba
        float[] data = new float[4096 * STRIDE];
        float[] depth = new float[4096];
        int[] order = new int[4096];
        int count;
        double cx, cy, cz;
        float rx, ry, rz, ux, uy, uz, fx, fy, fz;
        /**
         * Sprites further than this are drawn scaled towards the camera by proxy / distance. Scaling about the
         * eye leaves the picture unchanged but keeps a cloud kilometres away inside the far clip plane.
         */
        float proxy = 600f;

        /** Pulls the quad just written at offset o towards the camera if its centre (mx, my, mz) is too far. */
        private void pull(int o, float mx, float my, float mz) {
            float l = (float) Math.sqrt(mx * mx + my * my + mz * mz);
            if (l <= proxy) return;
            float k = proxy / l;
            for (int i = 0; i < 12; i++) data[o + i] *= k;
            depth[o / STRIDE] *= k;
        }

        void begin(Camera camera) {
            count = 0;
            Vec3 p = camera.position();
            cx = p.x; cy = p.y; cz = p.z;
            Vector3fc l = camera.leftVector(), u = camera.upVector(), f = camera.forwardVector();
            rx = -l.x(); ry = -l.y(); rz = -l.z();
            ux = u.x(); uy = u.y(); uz = u.z();
            fx = f.x(); fy = f.y(); fz = f.z();
        }

        private int slot(float d) {
            if (count == depth.length) {
                int n = count * 2;
                data = java.util.Arrays.copyOf(data, n * STRIDE);
                depth = java.util.Arrays.copyOf(depth, n);
                order = java.util.Arrays.copyOf(order, n);
            }
            depth[count] = d;
            return count++ * STRIDE;
        }

        private void colour(int o, int tile, float r, float g, float b, float a, float er, float eg, float eb) {
            data[o + 12] = FxAtlas.u0(tile); data[o + 13] = FxAtlas.v0(tile);
            data[o + 14] = Math.min(1, r * a + er); data[o + 15] = Math.min(1, g * a + eg); data[o + 16] = Math.min(1, b * a + eb);
            data[o + 17] = Math.max(a, 1 / 255f);   // exactly zero alpha would be discarded by the shader
        }

        @Override
        public void billboard(double x, double y, double z, float size, float roll, int tile, float r, float g, float b, float a, float er, float eg, float eb) {
            float px = (float) (x - cx), py = (float) (y - cy), pz = (float) (z - cz);
            float d = px * fx + py * fy + pz * fz;
            float h = size * 0.5f;
            if (d < -h || (a <= 0 && er + eg + eb <= 0.004f)) return;
            if (h < d * 0.0006f) return;            // under a pixel
            float c = (float) Math.cos(roll) * h, s = (float) Math.sin(roll) * h;
            float ax = rx * c + ux * s, ay = ry * c + uy * s, az = rz * c + uz * s;       // rotated "right"
            float bx = -rx * s + ux * c, by = -ry * s + uy * c, bz = -rz * s + uz * c;    // rotated "up"
            int o = slot(d);
            data[o] = px - ax + bx; data[o + 1] = py - ay + by; data[o + 2] = pz - az + bz;        // top-left
            data[o + 3] = px - ax - bx; data[o + 4] = py - ay - by; data[o + 5] = pz - az - bz;    // bottom-left
            data[o + 6] = px + ax - bx; data[o + 7] = py + ay - by; data[o + 8] = pz + az - bz;    // bottom-right
            data[o + 9] = px + ax + bx; data[o + 10] = py + ay + by; data[o + 11] = pz + az + bz;  // top-right
            colour(o, tile, r, g, b, a, er, eg, eb);
            pull(o, px, py, pz);
        }

        @Override
        public void ribbon(double ax, double ay, double az, double bx, double by, double bz, float width, int tile, float r, float g, float b, float a, float er, float eg, float eb) {
            float p0x = (float) (ax - cx), p0y = (float) (ay - cy), p0z = (float) (az - cz);
            float p1x = (float) (bx - cx), p1y = (float) (by - cy), p1z = (float) (bz - cz);
            float dx = p1x - p0x, dy = p1y - p0y, dz = p1z - p0z;
            float mx = (p0x + p1x) * 0.5f, my = (p0y + p1y) * 0.5f, mz = (p0z + p1z) * 0.5f;
            // side = along x toCamera, so the ribbon always faces the viewer
            float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-6f) return;
            float k = width * 0.5f / sl;
            sx *= k; sy *= k; sz *= k;
            float d = Math.max(p0x * fx + p0y * fy + p0z * fz, p1x * fx + p1y * fy + p1z * fz);
            if (d < 0) return;
            int o = slot(mx * fx + my * fy + mz * fz);
            data[o] = p0x + sx; data[o + 1] = p0y + sy; data[o + 2] = p0z + sz;
            data[o + 3] = p0x - sx; data[o + 4] = p0y - sy; data[o + 5] = p0z - sz;
            data[o + 6] = p1x - sx; data[o + 7] = p1y - sy; data[o + 8] = p1z - sz;
            data[o + 9] = p1x + sx; data[o + 10] = p1y + sy; data[o + 11] = p1z + sz;
            colour(o, tile, r, g, b, a, er, eg, eb);
            pull(o, mx, my, mz);
        }

        @Override
        public void ground(double x, double y, double z, float size, float yaw, int tile, float r, float g, float b, float a, float er, float eg, float eb) {
            float px = (float) (x - cx), py = (float) (y - cy), pz = (float) (z - cz);
            float h = size * 0.5f;
            float c = (float) Math.cos(yaw) * h, s = (float) Math.sin(yaw) * h;
            int o = slot(px * fx + py * fy + pz * fz);
            data[o] = px - c + s; data[o + 1] = py; data[o + 2] = pz - s - c;
            data[o + 3] = px - c - s; data[o + 4] = py; data[o + 5] = pz - s + c;
            data[o + 6] = px + c - s; data[o + 7] = py; data[o + 8] = pz + s + c;
            data[o + 9] = px + c + s; data[o + 10] = py; data[o + 11] = pz + s - c;
            colour(o, tile, r, g, b, a, er, eg, eb);
            pull(o, px, py, pz);
        }

        void draw(VertexConsumer vc) {
            for (int i = 0; i < count; i++) order[i] = i;
            sort(0, count - 1);
            float span = FxAtlas.span();
            for (int n = 0; n < count; n++) {
                int o = order[n] * STRIDE;
                float u0 = data[o + 12], v0 = data[o + 13], u1 = u0 + span, v1 = v0 + span;
                float r = data[o + 14], g = data[o + 15], b = data[o + 16], a = data[o + 17];
                vc.addVertex(data[o], data[o + 1], data[o + 2]).setUv(u0, v0).setColor(r, g, b, a);
                vc.addVertex(data[o + 3], data[o + 4], data[o + 5]).setUv(u0, v1).setColor(r, g, b, a);
                vc.addVertex(data[o + 6], data[o + 7], data[o + 8]).setUv(u1, v1).setColor(r, g, b, a);
                vc.addVertex(data[o + 9], data[o + 10], data[o + 11]).setUv(u1, v0).setColor(r, g, b, a);
            }
        }

        /** Quicksort of {@code order} by descending depth (far first), without allocating. */
        private void sort(int lo, int hi) {
            while (lo < hi) {
                float pivot = depth[order[(lo + hi) >>> 1]];
                int i = lo, j = hi;
                while (i <= j) {
                    while (depth[order[i]] > pivot) i++;
                    while (depth[order[j]] < pivot) j--;
                    if (i <= j) { int t = order[i]; order[i] = order[j]; order[j] = t; i++; j--; }
                }
                if (j - lo < hi - i) { sort(lo, j); lo = i; } else { sort(i, hi); hi = j; }
            }
        }
    }
}
