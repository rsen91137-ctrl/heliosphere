package com.heliosphere.core.fx.sim;

import static com.heliosphere.core.fx.sim.Rng.clamp01;
import static com.heliosphere.core.fx.sim.Rng.lerp;
import static com.heliosphere.core.fx.sim.Rng.smooth;
import static com.heliosphere.core.fx.sim.Rng.window;

/**
 * A large asteroid striking the ground, as a function of time since contact. Built like
 * {@link MushroomCloud}, but at the scale of kilometres: what a player sees is mostly the sky.
 *
 * <ol>
 *   <li>The entry trail: the ionised column the body punched through the atmosphere, glowing for a few
 *       seconds after contact, then hanging as a smoke trail that drifts and sags.</li>
 *   <li>The flash and the fireball: a hemisphere of vapourised rock that balloons, rises and cools.</li>
 *   <li>The ejecta curtain: an inverted cone of debris thrown out at about 45 degrees, spreading and
 *       falling back as a ring of dust.</li>
 *   <li>Incandescent ejecta: thousands of tonnes of molten rock on ballistic arcs, each streak burning
 *       as it falls kilometres away.</li>
 *   <li>The plume: a column of vapour and dust that climbs to the stratosphere and spreads into a dark
 *       umbrella over the whole sky, lit red from beneath for as long as the crater glows.</li>
 *   <li>The ground wave: a wall of dust racing out at the speed the server's blast wave travels.</li>
 * </ol>
 *
 * <p>Lengths in blocks, times in seconds. {@code k} scales every length (1 = a primary impact).
 */
public final class AsteroidImpact {
    private AsteroidImpact() {}

    /** Seconds of atmospheric entry drawn before contact. */
    public static final float ENTRY = 3f;
    public static final float DURATION = 330f + ENTRY;
    /** Ground wave speed, blocks per second (the server's blast front: 8 blocks a tick). */
    public static final float WAVE_SPEED = 160f;
    public static final float WAVE_REACH = 10000f;

    private static final ThreadLocal<float[]> TMP = ThreadLocal.withInitial(() -> new float[3]);

    /** Flash brightness for a viewer at distance d with a clear line of sight. */
    public static float flash(float t, float distance, float k) {
        float near = clamp01(1.15f - distance / (24000f * k));
        if (t < 0) {                                                       // the sky brightening as it comes in
            float u = clamp01(1 + t / ENTRY);
            return near * 0.45f * u * u;
        }
        return near * (t < 0.15f ? 1f : (float) Math.exp(-(t - 0.15f) / 1.1f));
    }

    /**
     * Emits the impact. {@code ax, az} is the horizontal direction the body came from (unit vector) and
     * {@code elevation} the angle of its path above the horizon.
     */
    public static void emit(SpriteSink s, double x, double y, double z, float t, float k, int seed,
                            float ax, float az, float elevation, MushroomCloud.Env env) {
        if (t < 0 || t > DURATION) return;
        if (t < ENTRY) { entry(s, x, y, z, t, k, seed, ax, az, elevation); return; }
        t -= ENTRY;                                                       // from here on, seconds since contact
        float day = 0.18f + 0.82f * env.daylight;
        float[] c = TMP.get();
        float heat = (float) Math.exp(-t / 55f);                         // the crater and fireball cooling
        entryTrail(s, x, y, z, t, k, seed, ax, az, elevation, day, c);
        fireball(s, x, y, z, t, k, seed, day, heat, c, env.detail);
        curtain(s, x, y, z, t, k, seed, day, env.detail);
        ejecta(s, x, y, z, t, k, seed, c, env.detail);
        plume(s, x, y, z, t, k, seed, day, heat, env);
        groundWave(s, x, y, z, t, k, seed, day, env);
        // the crater itself glowing through the dust for minutes
        if (heat > 0.02f) {
            MushroomCloud.glow(0.35f + 0.6f * heat, c);
            float g = heat * 0.9f;
            s.ground(x, y + 3, z, 900f * k, 0, FxAtlas.GLOW, 0, 0, 0, 0, c[0] * g, c[1] * g, c[2] * g);
            s.billboard(x, y + 60 * k, z, 1500f * k, 0, FxAtlas.GLOW, 0, 0, 0, 0, c[0] * g * 0.45f, c[1] * g * 0.35f, c[2] * g * 0.3f);
        }
    }

    /**
     * The last seconds before contact: a body kilometres across tearing through the atmosphere at tens of
     * kilometres a second. Its shock-heated head outshines the sun and drags a white-hot column of plasma behind
     * it, shedding burning fragments.
     */
    private static void entry(SpriteSink s, double x, double y, double z, float t, float k, int seed, float ax, float az, float elevation) {
        float ce = (float) Math.cos(elevation), se = (float) Math.sin(elevation);
        float length = 9000f * k;
        float u = t / ENTRY;
        float along = 1 - (float) Math.pow(u, 1.25);                      // fraction of the path still to go
        double hx = x + ax * ce * length * along, hy = y + se * length * along, hz = z + az * ce * length * along;
        double sx = x + ax * ce * length, sy = y + se * length, sz = z + az * ce * length;
        float heat = 0.55f + 0.45f * u;                                   // brighter as the air thickens
        // the plasma column it leaves
        s.ribbon(sx, sy, sz, hx, hy, hz, 900f * k, FxAtlas.BEAM, 0, 0, 0, 0, 0.55f * heat, 0.32f * heat, 0.12f * heat);
        s.ribbon(sx, sy, sz, hx, hy, hz, 320f * k, FxAtlas.BEAM, 0, 0, 0, 0, heat, 0.86f * heat, 0.6f * heat);
        s.ribbon(sx, sy, sz, hx, hy, hz, 90f * k, FxAtlas.BEAM, 0, 0, 0, 0, 1, 1, 0.95f);
        // the head
        s.billboard(hx, hy, hz, 9000f * k * heat, 0, FxAtlas.GLOW, 0, 0, 0, 0, 0.8f * heat, 0.62f * heat, 0.4f * heat);
        s.billboard(hx, hy, hz, 2600f * k, 0, FxAtlas.GLOW, 0, 0, 0, 0, 1, 0.97f, 0.9f);
        s.billboard(hx, hy, hz, 900f * k, 0, FxAtlas.CORE, 0, 0, 0, 0, 1, 1, 1);
        s.billboard(hx, hy, hz, 14000f * k * heat, t * 0.4f, FxAtlas.FLARE, 0, 0, 0, 0, heat, heat * 0.95f, heat * 0.85f);
        // fragments shed from the head, burning out behind it
        float[] c = TMP.get();
        for (int i = 0; i < 40; i++) {
            float shed = Rng.f(seed, i, 400) * ENTRY;
            float age = t - shed;
            if (age < 0 || age > 2.2f) continue;
            float a0 = 1 - (float) Math.pow(shed / ENTRY, 1.25);
            double px = x + ax * ce * length * a0 + Rng.s(seed, i, 401) * 180 * k * age;
            double py = y + se * length * a0 - 90 * age * age + Rng.s(seed, i, 402) * 120 * k * age;
            double pz = z + az * ce * length * a0 + Rng.s(seed, i, 403) * 180 * k * age;
            float e = 1 - age / 2.2f;
            MushroomCloud.glow(0.5f + 0.5f * e, c);
            s.billboard(px, py, pz, 180f * k * e + 40f * k, 0, FxAtlas.GLOW, 0, 0, 0, 0, c[0] * e, c[1] * e, c[2] * e);
        }
    }

    private static void entryTrail(SpriteSink s, double x, double y, double z, float t, float k, int seed,
                                   float ax, float az, float elevation, float day, float[] c) {
        float ce = (float) Math.cos(elevation), se = (float) Math.sin(elevation);
        float length = 9000f * k;
        int n = 46;
        float hot = (float) Math.exp(-t / 4.5f);
        float smoke = window(0.6f, 4f, 90f, 200f, t);
        for (int i = 0; i < n; i++) {
            float u0 = (float) i / n, u1 = (float) (i + 1) / n;
            // the trail sags and is torn by the wind as it ages
            float sag = t * 0.6f * u0, shear = t * 2.2f * u0 * u0;
            double x0 = x + ax * ce * length * u0 + shear, y0 = y + se * length * u0 - sag * 30, z0 = z + az * ce * length * u0;
            double x1 = x + ax * ce * length * u1 + shear, y1 = y + se * length * u1 - sag * 30, z1 = z + az * ce * length * u1;
            float w = (220f + 520f * u0) * k * (1 + t * 0.012f);
            if (hot > 0.01f) {
                float e = hot * (1 - u0 * 0.7f);
                MushroomCloud.glow(0.55f + 0.45f * e, c);
                s.ribbon(x0, y0, z0, x1, y1, z1, w * 0.55f, FxAtlas.BEAM, 0, 0, 0, 0, c[0] * e, c[1] * e, c[2] * e);
            }
            if (smoke > 0.01f) {
                for (int j = 0; j < 3; j++) {
                    float u = lerp(u0, u1, Rng.f(seed, i * 3 + j, 300));
                    double px = x + ax * ce * length * u + t * 2.2f * u * u + Rng.s(seed, i * 3 + j, 301) * w * 0.25f;
                    double py = y + se * length * u - t * 0.6f * u * 30;
                    double pz = z + az * ce * length * u + Rng.s(seed, i * 3 + j, 302) * w * 0.25f;
                    float l = day * (0.55f + 0.25f * Rng.f(seed, i * 3 + j, 303));
                    s.billboard(px, py, pz, w * (1.3f + 0.8f * Rng.f(seed, i * 3 + j, 304)), Rng.f(seed, i * 3 + j, 305) * 6.28f,
                            FxAtlas.SMOKE + ((i + j) & 7), l, l * 0.97f, l * 0.95f, smoke * 0.28f * (1 - 0.5f * u), 0, 0, 0);
                }
            }
        }
    }

    private static void fireball(SpriteSink s, double x, double y, double z, float t, float k, int seed, float day, float heat,
                                 float[] c, float detail) {
        if (t > 120) return;
        float flash = t < 0.15f ? 1f : (float) Math.exp(-(t - 0.15f) / 0.8f);
        if (flash > 0.01f) {
            s.billboard(x, y + 80 * k, z, 9000f * k, 0, FxAtlas.GLOW, 0, 0, 0, 0, flash, flash * 0.95f, flash * 0.85f);
            s.billboard(x, y + 80 * k, z, 14000f * k, 0.3f, FxAtlas.FLARE, 0, 0, 0, 0, flash, flash, flash * 0.9f);
        }
        float R = 1500f * k * (1 - (float) Math.exp(-t / 2.4f));
        float rise = 700f * k * (1 - (float) Math.exp(-t / 16f));
        int n = (int) (260 * detail);
        float fade = 1 - smooth(60f, 120f, t);
        for (int i = 0; i < n; i++) {
            float th = Rng.f(seed, i, 1) * 6.2832f;
            float cp = (float) Math.sqrt(Rng.f(seed, i, 2));                     // hemisphere, biased up
            float sp = (float) Math.sqrt(1 - cp * cp);
            float q = 0.4f + 0.6f * (float) Math.cbrt(Rng.f(seed, i, 3));
            float rr = R * q;
            double px = x + Math.cos(th) * sp * rr, pz = z + Math.sin(th) * sp * rr, py = y + cp * rr * 0.8f + rise * (0.3f + 0.7f * cp);
            float local = heat * (1.5f - q * 0.7f) + 0.1f * Rng.s(seed, i, 4);
            if (t < 2f) local = Math.max(local, 1.3f - t * 0.35f);
            MushroomCloud.glow(local, c);
            float e = clamp01(local * 1.4f) * (0.55f + 0.45f * Rng.f(seed, i, 5));
            float smokeA = smooth(0.95f, 0.3f, local);
            float l = day * (0.18f + 0.22f * Rng.f(seed, i, 6) + 0.25f * cp);
            float size = R * (0.42f + 0.3f * Rng.f(seed, i, 7)) + 60 * k;
            s.billboard(px, py, pz, size, Rng.f(seed, i, 8) * 6.28f + t * 0.05f * Rng.s(seed, i, 9),
                    local > 0.5f ? FxAtlas.FIRE + (i & 3) : FxAtlas.SMOKE + (i & 7),
                    l, l * 0.92f, l * 0.86f, (0.25f + 0.55f * smokeA) * fade * smooth(0f, 0.2f, t), c[0] * e, c[1] * e, c[2] * e);
        }
    }

    private static void curtain(SpriteSink s, double x, double y, double z, float t, float k, int seed, float day, float detail) {
        if (t < 0.3f || t > 160f) return;
        int n = (int) (420 * detail);
        for (int i = 0; i < n; i++) {
            float th = (i + Rng.f(seed, i, 20)) / n * 6.2832f;
            float launch = 0.3f + 6f * Rng.f(seed, i, 21);                     // when this part of the curtain left the ground
            float age = t - launch;
            if (age < 0) continue;
            float v = (260f + 200f * Rng.f(seed, i, 22)) * k;                  // 45 degree throw
            float g = 9f;
            float drag = (1 - (float) Math.exp(-age * 0.06f)) / 0.06f;
            float h = v * 0.7071f * drag - 0.5f * g * age * age * 0.25f;
            float out = 900f * k + v * 0.7071f * drag;
            boolean grounded = h < 0;
            if (grounded) h = 20f * k + 15f * k * Rng.f(seed, i, 23);           // settled into a dust ring
            float a = (grounded ? 0.32f : 0.45f) * window(0f, 1.5f, 70f, 160f, age);
            float l = day * (0.36f + 0.2f * Rng.f(seed, i, 24));
            float size = (380f + 300f * Rng.f(seed, i, 25)) * k * (1 + age * 0.025f);
            s.billboard(x + Math.cos(th) * out, y + h, z + Math.sin(th) * out, size, Rng.f(seed, i, 26) * 6.28f,
                    FxAtlas.SMOKE + (i & 7), l * 1.05f, l * 0.92f, l * 0.78f, a, 0, 0, 0);
        }
    }

    private static void ejecta(SpriteSink s, double x, double y, double z, float t, float k, int seed, float[] c, float detail) {
        if (t > 110f) return;
        int n = (int) (260 * detail);
        for (int i = 0; i < n; i++) {
            float th = Rng.f(seed, i, 40) * 6.2832f;
            float el = 0.45f + 0.75f * Rng.f(seed, i, 41);
            float v = (220f + 520f * Rng.f(seed, i, 42) * Rng.f(seed, i, 43)) * (float) Math.sqrt(k);
            float g = 9.8f * 1.6f;
            float flight = 2 * v * (float) Math.sin(el) / g;
            float vh = v * (float) Math.cos(el), vv = v * (float) Math.sin(el);
            for (int tr = 0; tr < 6; tr++) {
                float tt = t - tr * 0.18f;
                if (tt < 0 || tt > flight) continue;
                float px = vh * tt, py = vv * tt - 0.5f * g * tt * tt;
                float life = 1 - tt / Math.max(1, flight) * 0.6f;
                float e = life * (1 - tr / 6f);
                MushroomCloud.glow(0.5f + 0.45f * e, c);
                float size = (tr == 0 ? 55f : 38f) * (float) Math.sqrt(k) * (0.7f + 0.6f * Rng.f(seed, i, 44));
                s.billboard(x + Math.cos(th) * px, y + py, z + Math.sin(th) * px, size, 0, FxAtlas.GLOW, 0, 0, 0, 0, c[0] * e, c[1] * e, c[2] * e);
            }
        }
    }

    private static void plume(SpriteSink s, double x, double y, double z, float t, float k, int seed, float day, float heat,
                              MushroomCloud.Env env) {
        if (t < 1.5f) return;
        float top = 3600f * k * (1 - (float) Math.exp(-(t - 1.5f) / 55f));      // the column's top
        float spread = 600f * k + 26f * k * Math.max(0, t - 20);                  // the umbrella widens for minutes
        float fade = 1 - smooth(DURATION - 90f, DURATION, t);
        int nCol = (int) (180 * env.detail), nCap = (int) (300 * env.detail);
        double driftX = env.windX * t * 3, driftZ = env.windZ * t * 3;
        for (int i = 0; i < nCol; i++) {
            float u = (Rng.f(seed, i, 60) + t * 0.004f) % 1f;
            float th = Rng.f(seed, i, 61) * 6.2832f;
            float width = (380f + 260f * u) * k;
            float rr = width * (float) Math.sqrt(Rng.f(seed, i, 62));
            double px = x + Math.cos(th) * rr + driftX * u, pz = z + Math.sin(th) * rr + driftZ * u, py = y + u * top;
            float under = heat * (1 - u) * 1.4f;
            float l = day * (0.2f + 0.18f * Rng.f(seed, i, 63)) + under * 0.25f;
            float e = under * 0.55f;
            s.billboard(px, py, pz, (300f + 260f * Rng.f(seed, i, 64)) * k, Rng.f(seed, i, 65) * 6.28f, FxAtlas.SMOKE + (i & 7),
                    l, l * 0.9f, l * 0.84f, 0.42f * fade * smooth(1.5f, 8f, t), e, e * 0.35f, e * 0.08f);
        }
        if (t < 15f) return;
        float capAge = t - 15f;
        for (int i = 0; i < nCap; i++) {
            float th = Rng.f(seed, i, 70) * 6.2832f;
            float rr = spread * (float) Math.sqrt(Rng.f(seed, i, 71));
            float lobe = 1 + 0.12f * (float) Math.sin(th * 5 + seed);
            double px = x + Math.cos(th) * rr * lobe + driftX, pz = z + Math.sin(th) * rr * lobe + driftZ;
            double py = y + top * (0.92f + 0.08f * Rng.f(seed, i, 72)) - rr * 0.06f;
            float edge = rr / spread;
            float l = day * (0.26f + 0.3f * (1 - edge) * Rng.f(seed, i, 73));
            float e = heat * 0.25f * (1 - edge);
            s.billboard(px, py, pz, (520f + 420f * Rng.f(seed, i, 74)) * k * (1 + capAge * 0.004f), Rng.f(seed, i, 75) * 6.28f,
                    FxAtlas.SMOKE + (i & 7), l, l * 0.92f, l * 0.88f, 0.5f * fade * smooth(0f, 25f, capAge), e, e * 0.3f, e * 0.1f);
        }
    }

    private static void groundWave(SpriteSink s, double x, double y, double z, float t, float k, int seed, float day,
                                   MushroomCloud.Env env) {
        float front = WAVE_SPEED * t;
        if (front > WAVE_REACH * k + 600) return;
        int n = (int) (220 * env.detail);
        for (int i = 0; i < n; i++) {
            float th = (i + Rng.f(seed, i, 80)) / n * 6.2832f;
            float rr = front - 40f * Rng.f(seed, i, 81) * (1 + front / 2000f);
            if (rr < 0) continue;
            float h = (25f + 60f * Rng.f(seed, i, 82)) * (0.6f + 0.4f * clamp01(1 - front / (WAVE_REACH * k)));
            float a = 0.4f * clamp01(1.1f - front / (WAVE_REACH * k));
            float l = day * (0.5f + 0.15f * Rng.f(seed, i, 83));
            float size = (80f + 70f * Rng.f(seed, i, 84)) * (1 + front / 3000f);
            s.billboard(x + Math.cos(th) * rr, y + h * 0.5f, z + Math.sin(th) * rr, size, Rng.f(seed, i, 85) * 6.28f,
                    FxAtlas.DUST + (i & 1), env.dustR * l * 1.2f, env.dustG * l * 1.2f, env.dustB * l * 1.2f, a, 0, 0, 0);
        }
    }
}
