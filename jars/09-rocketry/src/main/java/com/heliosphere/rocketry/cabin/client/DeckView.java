package com.heliosphere.rocketry.cabin.client;

import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.rocketry.cabin.DeckGround;
import com.heliosphere.rocketry.cabin.FlightRoom;
import com.heliosphere.rocketry.cabin.FlightTelemetry;
import com.heliosphere.rocketry.client.DeckSky;
import com.heliosphere.rocketry.entity.RocketEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * What the crew sees and hears on the flight deck. The rocket's position arrives every tick and is carried on
 * smoothly between ticks; from it the ground snapshot is drawn under the room, as far down as the rocket is up
 * (it falls away at liftoff and comes up to meet you on landing), the planet's scene is published to the sky, the
 * sky darkens with height, and the engines rumble while they burn.
 */
public final class DeckView {
    private DeckView() {}

    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("heliosphere", "textures/misc/deck_white.png");

    private static volatile DeckGround ground;
    private static Vec3 p0 = Vec3.ZERO, p1 = Vec3.ZERO;
    private static long t0, t1;
    private static FlightTelemetry last = FlightTelemetry.OFF;
    private static int lastPhase = -1;
    private static Engine engine;

    static void ground(DeckGround g) { ground = g; }

    /** A new telemetry sample: the rocket's position for smoothing, sounds for phase changes. */
    static void sample(FlightTelemetry t) {
        long now = System.nanoTime();
        Vec3 p = new Vec3(t.x(), t.y(), t.z());
        if (!last.on() || p.distanceTo(p1) > 2000) { p0 = p; t0 = now - 50_000_000L; }
        else { p0 = p1; t0 = t1; }
        p1 = p; t1 = now;
        if (t.on() && t.phase() != lastPhase) {
            if (t.phase() == RocketEntity.ASCENT && lastPhase == RocketEntity.COUNTDOWN) play("rocket_ignition_refined", 1f);
            lastPhase = t.phase();
        }
        last = t;
        if (!t.on()) { ground = null; lastPhase = -1; }
    }

    /** Whether the local player is on the flight deck with a rocket to look out from. */
    public static boolean onDeck() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == FlightRoom.DECK && FlightRoomClient.active();
    }

    /** The rocket's position now, carried on from the last two samples (at most a third of a second ahead). */
    static Vec3 rocket() {
        long now = System.nanoTime();
        double span = Math.max(1, t1 - t0);
        double ahead = Math.min(now - t1, 330_000_000L) / span;
        return p1.add(p1.subtract(p0).scale(ahead));
    }

    static double altitude() {
        Vec3 r = rocket();
        return Math.max(0, last.altitude() + (r.y - last.y()));
    }

    static CelestialBody body() {
        CelestialBody b = SolarSystem.get(last.body());
        return b != null ? b : SolarSystem.get("earth");
    }

    /** How dark the sky is (0 a blue day sky, 1 black space): with height in air, always black without air. */
    public static float skyDarken() {
        if (!onDeck()) return 0;
        CelestialBody b = body();
        if (b == null || "earth".equals(b.id)) return (float) smooth((altitude() - 3000) / 45000);
        if (b.pressureBar < 0.001) return 1f;
        return (float) Math.max(0.55, smooth((altitude() - 1500) / 20000));        // thin skies: dim at best
    }

    /** Every frame: the planet's scene for the sky. */
    static void frame() {
        if (!onDeck()) return;
        Vec3 r = rocket();
        DeckSky.publish(body(), altitude(), r.x, r.z);
    }

    // ---- the ground ------------------------------------------------------------------------------------------

    static void render(WorldRenderContext ctx) {
        DeckGround g = ground;
        if (g == null || !onDeck()) return;
        double alt = altitude();
        float alpha = 1f - (float) smooth((alt - 500) / 1100);
        if (alpha < 0.02f) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Vec3 r = rocket();
        int n = g.n(), step = g.step();
        double x0 = Math.floor(g.ox()) - (n - 1) / 2.0 * step, z0 = Math.floor(g.oz()) - (n - 1) / 2.0 * step;
        // world (x, y, z) of the rocket's world -> deck: the rocket's axis is at (ax+.5, ay + 0, az+.5) at deck-floor height
        double dx = g.ax() + 0.5 - r.x + x0 - cam.x, dy = g.ay() - (r.y + g.seat()) - cam.y, dz = g.az() + 0.5 - r.z + z0 - cam.z;
        float light = 0.3f + 0.7f * (1f - mc.level.getSkyDarken() / 11f);   // day and night outside
        int stride = alt > 260 ? 2 : 1;
        PoseStack pose = ctx.matrices();
        pose.pushPose();
        pose.translate(dx, dy, dz);
        VertexConsumer vc = ctx.consumers().getBuffer(RenderTypes.entityTranslucent(WHITE));
        PoseStack.Pose m = pose.last();
        short[] h = g.height();
        int[] c = g.rgb();
        int a = (int) (alpha * 255);
        float cs = step * stride;
        for (int j = 0; j < n; j += stride) for (int i = 0; i < n; i += stride) {
            int k = j * n + i;
            if (h[k] == DeckGround.NONE) continue;
            float x = i * step, z = j * step, y = h[k];
            int hw = height(h, n, i - stride, j, h[k]), he = height(h, n, i + stride, j, h[k]);
            int hn = height(h, n, i, j - stride, h[k]), hs = height(h, n, i, j + stride, h[k]);
            float shade = clamp(1f + 0.07f * ((hw - he) + (hn - hs)) / stride, 0.7f, 1.25f) * light;
            int rgb = c[k];
            int cr = (int) Math.min(255, (rgb >> 16 & 255) * shade), cg = (int) Math.min(255, (rgb >> 8 & 255) * shade), cb = (int) Math.min(255, (rgb & 255) * shade);
            quad(vc, m, x, y, z, x, y, z + cs, x + cs, y, z + cs, x + cs, y, z, cr, cg, cb, a, 0, 1, 0);
            // cliffs and walls down to the lower neighbour (east and south; west and north are drawn by those)
            if (he < y && i + stride < n && h[k + stride] != DeckGround.NONE)
                quad(vc, m, x + cs, y, z, x + cs, y, z + cs, x + cs, he, z + cs, x + cs, he, z, (int) (cr * 0.8f), (int) (cg * 0.8f), (int) (cb * 0.8f), a, 1, 0, 0);
            if (hw < y && i - stride >= 0 && h[k - stride] != DeckGround.NONE)
                quad(vc, m, x, y, z + cs, x, y, z, x, hw, z, x, hw, z + cs, (int) (cr * 0.8f), (int) (cg * 0.8f), (int) (cb * 0.8f), a, -1, 0, 0);
            if (hs < y && j + stride < n && h[k + stride * n] != DeckGround.NONE)
                quad(vc, m, x + cs, y, z + cs, x, y, z + cs, x, hs, z + cs, x + cs, hs, z + cs, (int) (cr * 0.7f), (int) (cg * 0.7f), (int) (cb * 0.7f), a, 0, 0, 1);
            if (hn < y && j - stride >= 0 && h[k - stride * n] != DeckGround.NONE)
                quad(vc, m, x, y, z, x + cs, y, z, x + cs, hn, z, x, hn, z, (int) (cr * 0.7f), (int) (cg * 0.7f), (int) (cb * 0.7f), a, 0, 0, -1);
        }
        pose.popPose();
    }

    private static int height(short[] h, int n, int i, int j, int self) {
        if (i < 0 || j < 0 || i >= n || j >= n) return self;
        short v = h[j * n + i];
        return v == DeckGround.NONE ? self : v;
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose m, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, int r, int g, int b, int a, float nx, float ny, float nz) {
        vertex(vc, m, x1, y1, z1, r, g, b, a, nx, ny, nz);
        vertex(vc, m, x2, y2, z2, r, g, b, a, nx, ny, nz);
        vertex(vc, m, x3, y3, z3, r, g, b, a, nx, ny, nz);
        vertex(vc, m, x4, y4, z4, r, g, b, a, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose m, float x, float y, float z, int r, int g, int b, int a, float nx, float ny, float nz) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(m, nx, ny, nz);
    }

    // ---- sound -----------------------------------------------------------------------------------------------

    /** The engines while they burn: a deep rumble through the hull, quieter as the air thins. */
    static void tick() {
        boolean burn = onDeck() && (last.phase() == RocketEntity.ASCENT || last.phase() == RocketEntity.SUBORBITAL
                || last.phase() == RocketEntity.DESCENT && altitude() < 9000);
        if (burn && (engine == null || engine.isStopped())) {
            SoundEvent e = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.fromNamespaceAndPath("heliosphere", "rocket_engine_cinematic"));
            if (e != null) { engine = new Engine(e); Minecraft.getInstance().getSoundManager().play(engine); }
        }
    }

    private static void play(String id, float volume) {
        SoundEvent e = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.fromNamespaceAndPath("heliosphere", id));
        if (e != null) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, 1f, volume));
    }

    private static final class Engine extends AbstractTickableSoundInstance {
        Engine(SoundEvent e) {
            super(e, SoundSource.AMBIENT, RandomSource.create());
            looping = true; delay = 0; relative = true; attenuation = SoundInstance.Attenuation.NONE; volume = 0.01f;
        }

        @Override
        public void tick() {
            boolean burn = onDeck() && (last.phase() == RocketEntity.ASCENT || last.phase() == RocketEntity.SUBORBITAL
                    || last.phase() == RocketEntity.DESCENT && altitude() < 9000);
            if (!burn) { volume -= 0.05f; if (volume <= 0.01f) stop(); return; }
            double alt = altitude();
            float target = last.phase() == RocketEntity.DESCENT ? 0.7f : (float) (1.0 - 0.6 * smooth(alt / 60000));
            volume += Math.max(-0.05f, Math.min(0.05f, target - volume));
            pitch = 0.9f + 0.1f * (float) smooth(alt / 30000);
        }
    }

    static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
