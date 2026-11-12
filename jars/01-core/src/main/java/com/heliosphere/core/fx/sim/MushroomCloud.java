package com.heliosphere.core.fx.sim;

import static com.heliosphere.core.fx.sim.Rng.clamp01;
import static com.heliosphere.core.fx.sim.Rng.lerp;
import static com.heliosphere.core.fx.sim.Rng.smooth;
import static com.heliosphere.core.fx.sim.Rng.window;

/**
 * A nuclear detonation as a function of time. Nothing is simulated step by step: every puff has a fixed
 * identity and its place at time {@code t} is computed directly, so the cloud looks the same for a player
 * who arrives late, and the offline preview and the game draw the same thing.
 *
 * <p>The anatomy, in order of appearance: the flash and fireball; the blast wave running out along the
 * ground with a condensation shell behind it; the fireball lifting off, cooling and rolling into a vortex
 * ring (the cap); the stem of dirt drawn up underneath it; the base surge spreading along the ground; a
 * condensation skirt around the stem; then a slow drift and fade.
 *
 * <p>Lengths are in blocks at {@code scale} 1 (a 72-block crater), times in seconds.
 */
public final class MushroomCloud {
    private MushroomCloud() {}

    /** Seconds until the last wisp has faded. */
    public static final float DURATION = 95f;
    public static final float SHOCK_SPEED = 62f;

    /** What the surroundings contribute. */
    public static final class Env {
        /** 0 at midnight, 1 at noon. */
        public float daylight = 1f;
        /** Wind, blocks per second. */
        public float windX = 0.9f, windZ = 0.35f;
        /** Multiplies the number of puffs (0.35 .. 1). */
        public float detail = 1f;
        /** Ground dust colour (the stem and base surge are made of whatever was on the ground). */
        public float dustR = 0.52f, dustG = 0.44f, dustB = 0.34f;
    }

    // ---- shape over time ----------------------------------------------------------------------------------------

    static float fireballRadius(float t) { return 46f * (1 - (float) Math.exp(-t / 0.42f)); }

    /** Height of the cap's centre above ground zero. */
    static float capHeight(float t) {
        float early = 10f + 16f * smooth(0f, 2.2f, t);
        float rise = 172f * (1 - (float) Math.exp(-Math.max(0, t - 1.2f) / 8.5f));
        return early + rise;
    }

    static float capRadius(float t) { return 46f + 50f * (1 - (float) Math.exp(-Math.max(0, t - 1.5f) / 11f)) + 0.25f * Math.max(0, t - 30); }

    /** Heat of the fireball, 1 at detonation, falling towards 0 as it cools into smoke. */
    static float heat(float t) { return (float) Math.exp(-t / 5.2f); }

    public static float shockRadius(float t) { return SHOCK_SPEED * t; }

    /** How bright the whole-screen flash is at time t for a viewer with a clear view (0..1). */
    public static float flash(float t) {
        return t < 0.08f ? 1f : (float) Math.exp(-(t - 0.08f) / 0.55f);
    }

    // ---- colour -------------------------------------------------------------------------------------------------

    /** Incandescence for a heat in 0..1: nothing, deep red, orange, yellow, white. Writes rgb into out. */
    public static void glow(float h, float[] out) {
        h = clamp01(h);
        float r = smooth(0.02f, 0.38f, h);
        float g = smooth(0.22f, 0.78f, h);
        float b = smooth(0.55f, 1.0f, h);
        out[0] = r * (0.55f + 0.45f * h); out[1] = g * 0.92f * h + g * 0.05f; out[2] = b * 0.8f * h;
    }

    /** Direction to the sun used to model the smoke (fixed: a high sun, slightly to one side). */
    private static final float SUN_X = -0.48f, SUN_Y = 0.78f, SUN_Z = -0.40f;

    /** Wrapped diffuse light for a surface normal: 0 in deep shadow, 1 facing the sun. */
    static float sun(float nx, float ny, float nz) {
        float d = nx * SUN_X + ny * SUN_Y + nz * SUN_Z;
        return clamp01(d * 0.62f + 0.42f);
    }

    private static final ThreadLocal<float[]> TMP = ThreadLocal.withInitial(() -> new float[3]);

    // ---- emission -----------------------------------------------------------------------------------------------

    public static void emit(SpriteSink s, double cx, double cy, double cz, float t, float scale, int seed, Env env) {
        if (t < 0 || t > DURATION) return;
        float fade = 1 - smooth(DURATION - 38f, DURATION, t);            // the long dissolve
        float day = 0.22f + 0.78f * env.daylight;
        double driftX = env.windX * Math.max(0, t - 6) * 0.8, driftZ = env.windZ * Math.max(0, t - 6) * 0.8;
        float[] c = TMP.get();

        lights(s, cx, cy, cz, t, scale, c);
        shock(s, cx, cy, cz, t, scale, seed, env, day);
        wilson(s, cx, cy, cz, t, scale, seed, day);
        baseSurge(s, cx, cy, cz, t, scale, seed, env, day, fade);
        stem(s, cx + driftX * 0.35 * scale, cy, cz + driftZ * 0.35 * scale, driftX * 0.65 * scale, driftZ * 0.65 * scale, t, scale, seed, env, day, fade, c);
        cap(s, cx + driftX * scale, cy, cz + driftZ * scale, t, scale, seed, env, day, fade, c);
        skirt(s, cx + driftX * 0.7 * scale, cy, cz + driftZ * 0.7 * scale, t, scale, seed, day, fade);
        embers(s, cx, cy, cz, t, scale, seed, c);
    }

    /** The light itself: the initial flash ball, the fireball's core and the glow it casts on the ground. */
    private static void lights(SpriteSink s, double cx, double cy, double cz, float t, float k, float[] c) {
        float h = heat(t);
        double fy = cy + capHeight(t) * k;
        if (t < 2.2f) {
            float f = flash(t);
            float size = (420f + 500f * t) * k;
            s.billboard(cx, cy + 8 * k, cz, size, 0, FxAtlas.GLOW, 0, 0, 0, 0, 1.0f * f, 0.96f * f, 0.88f * f);
            s.billboard(cx, cy + 8 * k, cz, size * 0.42f, 0, FxAtlas.GLOW, 0, 0, 0, 0, f, f, f);
            float star = f * (1 - smooth(0.5f, 1.6f, t));
            s.billboard(cx, cy + 8 * k, cz, 900f * k, 0, FxAtlas.FLARE, 0, 0, 0, 0, star, star * 0.95f, star * 0.8f);
        }
        if (h > 0.02f) {
            glow(Math.min(1, h * 1.25f), c);
            float core = fireballRadius(t) * 2.6f * k * (0.75f + 0.25f * h);
            float i = Math.min(1, h * 1.3f);
            s.billboard(cx, fy, cz, core, 0, FxAtlas.GLOW, 0, 0, 0, 0, c[0] * i, c[1] * i, c[2] * i);
            s.billboard(cx, fy, cz, core * 2.4f, 0, FxAtlas.GLOW, 0, 0, 0, 0, c[0] * i * 0.35f, c[1] * i * 0.3f, c[2] * i * 0.25f);
            // firelight pooling on the ground under the cloud
            float g = h * 0.5f;
            s.ground(cx, cy + 0.6, cz, 260f * k, 0, FxAtlas.GLOW, 0, 0, 0, 0, 1.0f * g, 0.5f * g, 0.15f * g);
        }
    }

    /** The blast wave: a wall of dust racing outward along the ground, leaving a haze that settles. */
    /**
     * The blast wave, visible: a wall of dust rising along the front as it races outward, a faint bright shell
     * (the compressed air bending the light) arching over it, and the dust it leaves hanging behind.
     */
    private static void shock(SpriteSink s, double cx, double cy, double cz, float t, float k, int seed, Env env, float day) {
        float front = shockRadius(t);
        float reach = 380f;
        if (front > reach + 300) return;
        float alive = 1 - smooth(reach * 0.75f, reach + 40, front);              // the wave weakens as it spreads
        if (alive > 0.01f) {
            // the shell: a dome of thin rings at the front, faint and bright
            for (int i = 0; i < 6; i++) {
                float el = (i + 0.5f) / 6 * 1.35f;
                float rr = front * (float) Math.cos(el), hy = front * (float) Math.sin(el) * 0.55f;
                float e = alive * 0.10f * (1 - i / 6f);
                s.ground(cx, cy + 2 + hy, cz, rr * 2 * k / 0.8f, i * 0.6f, FxAtlas.RING, 0, 0, 0, 0, e, e, e * 1.05f);
            }
            s.ground(cx, cy + 1.2, cz, front * 2 * k / 0.78f, 0, FxAtlas.SHOCK, 0.92f * day, 0.9f * day, 0.86f * day, alive * 0.45f,
                    0.22f * alive, 0.21f * alive, 0.2f * alive);
            // the wall: tall dust right at the front
            int wall = (int) (140 * env.detail);
            for (int i = 0; i < wall; i++) {
                float th = (i + Rng.f(seed, i, 50)) / wall * 6.2832f;
                float rr = front - 6f * Rng.f(seed, i, 51);
                float h = (4f + 10f * Rng.f(seed, i, 52)) * (0.5f + 0.5f * alive);
                float size = (10f + 10f * Rng.f(seed, i, 53)) * (1 + front / 300f);
                float l = day * (0.7f + 0.2f * Rng.f(seed, i, 54));
                s.billboard(cx + Math.cos(th) * rr * k, cy + h * 0.6 * k, cz + Math.sin(th) * rr * k, size * k, Rng.s(seed, i, 55) * 0.6f,
                        FxAtlas.DUST + (i & 1), env.dustR * l * 1.1f, env.dustG * l * 1.1f, env.dustB * l * 1.1f, 0.55f * alive, 0, 0, 0);
            }
        }
        int n = (int) (220 * env.detail);
        for (int i = 0; i < n; i++) {
            float ring = 12f + (reach - 12f) * (float) Math.sqrt(Rng.f(seed, i, 40));     // where this puff is kicked up
            float born = ring / SHOCK_SPEED;
            float age = t - born;
            if (age < 0 || age > 9f) continue;
            float th = Rng.f(seed, i, 41) * 6.2832f;
            float push = ring + 12f * (1 - (float) Math.exp(-age * 1.6f));                // shoved outward, then stalls
            float y = 1.5f + (3f + 6f * Rng.f(seed, i, 42)) * (1 - (float) Math.exp(-age * 1.2f));
            float size = (8f + 10f * Rng.f(seed, i, 43)) * (0.5f + 0.5f * smooth(0, 1.2f, age)) * (1 + age * 0.12f);
            float a = window(0f, 0.25f, 2.5f, 9f, age) * 0.42f * (1 - 0.6f * ring / reach);
            float l = day * (0.8f + 0.2f * Rng.f(seed, i, 44));
            s.billboard(cx + Math.cos(th) * push * k, cy + y * k, cz + Math.sin(th) * push * k, size * k, Rng.s(seed, i, 45) * 0.5f,
                    FxAtlas.DUST + (i & 1), env.dustR * l * 1.15f, env.dustG * l * 1.15f, env.dustB * l * 1.15f, a, 0, 0, 0);
        }
    }

    /** The Wilson cloud: humid air flashing to mist in the low pressure behind the blast wave. */
    private static void wilson(SpriteSink s, double cx, double cy, double cz, float t, float k, int seed, float day) {
        if (t < 0.25f || t > 3.0f) return;
        float strength = window(0.25f, 0.7f, 1.3f, 3.0f, t);
        float radius = shockRadius(t) * 0.62f + 8f;
        int n = 90;
        for (int i = 0; i < n; i++) {
            float th = (i + Rng.f(seed, i, 60)) / n * 6.2832f * 3;
            float el = 0.08f + 0.95f * Rng.f(seed, i, 61);                                // elevation on the dome
            float rr = radius * (float) Math.cos(el), y = radius * (float) Math.sin(el) * 0.7f;
            float size = radius * (0.32f + 0.2f * Rng.f(seed, i, 62));
            float a = strength * 0.16f * (0.6f + 0.4f * Rng.f(seed, i, 63));
            float w = day * 0.97f;
            s.billboard(cx + Math.cos(th) * rr * k, cy + (4 + y) * k, cz + Math.sin(th) * rr * k, size * k, Rng.s(seed, i, 64) * 3f,
                    FxAtlas.SMOKE + (i & 7), w, w, w * 1.02f, a, 0, 0, 0);
        }
    }

    /** The base surge: the dust skirt rolling out along the ground under the stem. */
    private static void baseSurge(SpriteSink s, double cx, double cy, double cz, float t, float k, int seed, Env env, float day, float fade) {
        if (t < 0.4f) return;
        float grow = 1 - (float) Math.exp(-(t - 0.4f) / 7f);
        float outer = 34f + 96f * grow;
        int n = (int) (190 * env.detail);
        float heatGlow = heat(t) * smooth(0.4f, 1.2f, t);
        for (int i = 0; i < n; i++) {
            float u = Rng.f(seed, i, 70);
            float th = Rng.f(seed, i, 71) * 6.2832f + t * 0.012f * Rng.s(seed, i, 72);
            float radial = outer * (0.18f + 0.82f * (float) Math.sqrt(u));
            float lobes = 1 + 0.14f * (float) Math.sin(th * 5 + seed) + 0.08f * (float) Math.sin(th * 11 + 2.1f);
            radial *= lobes;
            float roll = t * 0.35f + Rng.f(seed, i, 73) * 6.2832f;
            float height = (5f + 22f * Rng.f(seed, i, 74) * (1.2f - (float) Math.sqrt(u))) * (0.55f + 0.45f * grow) + 1.6f * (float) Math.sin(roll);
            float size = (17f + 15f * Rng.f(seed, i, 75)) * (0.6f + 0.6f * grow);
            float a = smooth(0.4f, 2.5f, t) * 0.5f * fade * (0.65f + 0.35f * Rng.f(seed, i, 76));
            float edge = (float) Math.sqrt(u);
            float l = day * (0.45f + 0.75f * sun((float) Math.cos(th) * 0.6f, 0.7f, (float) Math.sin(th) * 0.6f)) * (0.8f + 0.3f * Rng.f(seed, i, 77));
            float e = heatGlow * (1 - edge) * 0.5f;
            s.billboard(cx + Math.cos(th) * radial * k, cy + height * k, cz + Math.sin(th) * radial * k, size * k, Rng.s(seed, i, 78) * 0.6f + (float) Math.sin(roll) * 0.1f,
                    FxAtlas.SMOKE + (i & 7), lerp(env.dustR, 0.7f, 0.3f) * l, lerp(env.dustG, 0.66f, 0.3f) * l, lerp(env.dustB, 0.6f, 0.3f) * l, a, e, e * 0.42f, e * 0.1f);
        }
    }

    /** The stem: ground debris drawn up the axis into the rising cap. */
    private static void stem(SpriteSink s, double cx, double cy, double cz, double leanX, double leanZ, float t, float k, int seed, Env env,
                             float day, float fade, float[] c) {
        if (t < 0.7f) return;
        float top = capHeight(t) - capRadius(t) * 0.16f;
        float appear = smooth(0.7f, 2.4f, t);
        float waist = 13f + 7f * smooth(2, 20, t) + 0.12f * Math.max(0, t - 30);
        float h = heat(t);
        int n = (int) (260 * env.detail);
        float speed = 0.085f * (0.35f + 0.65f * (float) Math.exp(-t / 18f));              // the updraft slackens as the cloud stabilises
        for (int i = 0; i < n; i++) {
            float u0 = Rng.f(seed, i, 80);
            float u = u0 + t * speed * (0.8f + 0.4f * Rng.f(seed, i, 81));
            u -= (float) Math.floor(u);                                                    // endlessly rising
            float th = Rng.f(seed, i, 82) * 6.2832f + u * 2.2f + t * 0.05f;               // a slow twist
            float flareBase = 1.9f * (float) Math.exp(-u * 7f);
            float flareTop = 1.5f * smooth(0.66f, 1f, u);
            float pinch = 1 - 0.18f * (float) Math.sin(u * 3.14159f);
            float radius = waist * (pinch + flareBase + flareTop) * (0.25f + 0.75f * (float) Math.sqrt(Rng.f(seed, i, 83)));
            float wob = 1.6f * (float) Math.sin(u * 9 + Rng.f(seed, i, 84) * 6.2832f + t * 0.4f);
            float y = u * top;
            float size = (13f + 9f * Rng.f(seed, i, 85)) * (0.8f + 0.5f * flareBase * 0.5f + 0.35f * flareTop) * (1 + 0.004f * t * t * 0.1f);
            float a = appear * window(0f, 0.05f, 0.9f, 1f, u) * 0.62f * fade;
            float depth = radius / (waist * 1.6f);                                         // 0 on the axis
            float shade = day * (0.30f + 0.75f * sun((float) Math.cos(th), 0.15f, (float) Math.sin(th))) * (0.85f + 0.3f * Rng.f(seed, i, 86));
            float inner = clamp01(h * 1.5f - 0.15f) * (1 - clamp01(depth)) * (0.35f + 0.65f * u);
            glow(inner * 0.75f, c);
            double lx = leanX * u * u, lz = leanZ * u * u;
            s.billboard(cx + lx + (Math.cos(th) * radius + wob) * k, cy + y * k, cz + lz + (Math.sin(th) * radius + wob * 0.6) * k, size * k,
                    Rng.s(seed, i, 87) * 0.5f, FxAtlas.SMOKE + ((i * 3) & 7),
                    lerp(env.dustR, 0.62f, 0.45f) * shade, lerp(env.dustG, 0.58f, 0.45f) * shade, lerp(env.dustB, 0.55f, 0.45f) * shade, a,
                    c[0] * 0.9f, c[1] * 0.9f, c[2] * 0.9f);
        }
    }

    /** The cap: the fireball rolled into a vortex ring, incandescent inside, cooling to smoke from the outside in. */
    private static void cap(SpriteSink s, double cx, double cy, double cz, float t, float k, int seed, Env env, float day, float fade, float[] c) {
        float zc = capHeight(t), rc = capRadius(t), rf = fireballRadius(t);
        float h = heat(t);
        float torus = smooth(0.9f, 4.5f, t);                                               // sphere -> ring
        float major = rc * 0.52f, minor = rc * 0.50f;
        float flat = lerp(1f, 0.62f, smooth(3, 22, t));                                    // flattens under the inversion
        float spin = 2.6f * (1 - (float) Math.exp(-t / 3.5f)) + 0.045f * t;                // total roll so far, radians
        int n = (int) (640 * env.detail);
        int fill = n * 2 / 5;                                                              // big soft puffs give the body, small ones the cauliflower skin
        float puff = (17f + 0.1f * t) * (0.55f + 0.45f * smooth(0, 3, t)) * (rc / 62f + 0.3f);
        for (int i = 0; i < n; i++) {
            boolean skin = i >= fill;
            float q = skin ? 0.82f + 0.22f * Rng.f(seed, i, 1) : 0.25f + 0.6f * (float) Math.cbrt(Rng.f(seed, i, 1));
            float th = Rng.f(seed, i, 2) * 6.2832f;
            float phi0 = Rng.f(seed, i, 3) * 6.2832f;

            // as a fireball: a point in a sphere
            float cosPol = Rng.s(seed, i, 4), sinPol = (float) Math.sqrt(1 - cosPol * cosPol);
            float sx = rf * q * sinPol, sy = rf * q * cosPol;                              // (horizontal, vertical) in the meridian plane

            // as a vortex ring: rolling around the ring's core, up the inside and down the outside
            float phi = phi0 - spin * (1.15f - 0.45f * q);
            float lump = 1 + 0.15f * (float) Math.sin(th * 4 + phi0 * 2 + seed) + 0.10f * (float) Math.sin(th * 9 - phi * 2)
                    + 0.07f * (float) Math.sin(th * 17 + phi * 3 + 1.3f);
            float rr = minor * q * lump;
            float cph = (float) Math.cos(phi), sph = (float) Math.sin(phi);
            float tx = major + rr * cph;
            float ty = rr * sph * flat + minor * 0.2f * flat;                              // sits a little above the ring plane: a dome, not a doughnut
            if (tx < 0) tx = -tx * 0.5f;

            float px = lerp(sx, tx, torus), py = lerp(sy, ty, torus);
            float cth = (float) Math.cos(th), sth = (float) Math.sin(th);
            double x = cx + cth * px * k, z = cz + sth * px * k, y = cy + (zc + py) * k;

            // surface normal, sphere early and ring later
            float nh = lerp(sinPol, cph, torus), nv = lerp(cosPol, sph, torus);
            float light = sun(cth * nh, nv, sth * nh);

            // heat: the inside and the underside stay incandescent longest
            float under = 0.5f - 0.5f * sph;                                               // 1 underneath
            float inside = 1 - Math.min(1, q);
            float local = h * (0.55f + 0.9f * inside + 0.55f * under * torus) + 0.25f * h * Rng.s(seed, i, 5);
            if (t < 1.2f) local = Math.max(local, 1.1f - t * 0.25f);
            glow(local, c);
            float e = clamp01(local * 1.5f) * (0.5f + 0.5f * Rng.f(seed, i, 6));
            float smoke = smooth(0.95f, 0.35f, local);                                     // opaque once it has cooled
            float var = 0.86f + 0.28f * Rng.f(seed, i, 7);
            // sunlit crown: pale grey-white; shadowed underside: warm brown, lit from within while it is hot
            float lr = day * lerp(0.20f, 0.93f, light) * var + h * 0.40f * under;
            float lg = day * lerp(0.15f, 0.90f, light) * var + h * 0.17f * under;
            float lb = day * lerp(0.12f, 0.88f, light) * var + h * 0.05f * under;
            float a = (skin ? 0.30f + 0.50f * smoke : 0.22f + 0.48f * smoke) * fade * smooth(0f, 0.25f, t);
            float size = puff * (skin ? 0.42f + 0.34f * Rng.f(seed, i, 8) : 0.95f + 0.5f * Rng.f(seed, i, 8));
            int tile = local > 0.5f ? FxAtlas.FIRE + (i & 3) : FxAtlas.SMOKE + (i & 7);
            float roll = Rng.s(seed, i, 9) * 0.45f + (local > 0.5f ? t * 0.4f * Rng.s(seed, i, 10) : 0);
            s.billboard(x, y, z, size * k, roll, tile, lr, lg, lb, a, c[0] * e, c[1] * e, c[2] * e);
        }
    }

    /** A thin condensation ring hanging around the stem below the cap. */
    private static void skirt(SpriteSink s, double cx, double cy, double cz, float t, float k, int seed, float day, float fade) {
        if (t < 4.5f || t > 30f) return;
        float strength = window(4.5f, 8f, 16f, 30f, t) * fade;
        float y = capHeight(t) * 0.56f, radius = capRadius(t) * 0.62f;
        int n = 70;
        for (int i = 0; i < n; i++) {
            float th = (i + Rng.f(seed, i, 90)) / n * 6.2832f;
            float rr = radius * (0.9f + 0.2f * Rng.f(seed, i, 91));
            float w = day * 0.96f;
            s.billboard(cx + Math.cos(th) * rr * k, cy + (y + 3 * Rng.s(seed, i, 92)) * k, cz + Math.sin(th) * rr * k,
                    (15f + 8f * Rng.f(seed, i, 93)) * k, Rng.s(seed, i, 94), FxAtlas.SMOKE + (i & 7), w, w, w, strength * 0.2f, 0, 0, 0);
        }
    }

    /** Burning fragments thrown out on ballistic arcs, each trailing sparks. */
    private static void embers(SpriteSink s, double cx, double cy, double cz, float t, float k, int seed, float[] c) {
        if (t > 9f) return;
        int n = 46;
        for (int i = 0; i < n; i++) {
            float th = Rng.f(seed, i, 100) * 6.2832f;
            float el = 0.35f + 0.95f * Rng.f(seed, i, 101);
            float v = 34f + 46f * Rng.f(seed, i, 102);
            float vx = v * (float) Math.cos(el), vy = v * (float) Math.sin(el);
            for (int trail = 0; trail < 5; trail++) {
                float tt = t - trail * 0.07f;
                if (tt < 0) break;
                float drag = (1 - (float) Math.exp(-tt * 0.35f)) / 0.35f;
                float px = vx * drag, py = vy * drag - 13f * tt * tt * 0.5f + 6f;
                if (py < 0) break;
                float life = (1 - tt / 9f) * (1 - trail / 5f);
                glow(0.45f + 0.5f * life, c);
                float size = (trail == 0 ? 3.4f : 2.2f) * (0.6f + 0.4f * life);
                s.billboard(cx + Math.cos(th) * px * k, cy + py * k, cz + Math.sin(th) * px * k, size * k, 0, FxAtlas.GLOW,
                        0, 0, 0, 0, c[0] * life, c[1] * life, c[2] * life);
            }
        }
    }
}
