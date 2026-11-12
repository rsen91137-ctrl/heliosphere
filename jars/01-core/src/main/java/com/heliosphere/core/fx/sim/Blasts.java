package com.heliosphere.core.fx.sim;

import static com.heliosphere.core.fx.sim.Rng.clamp01;
import static com.heliosphere.core.fx.sim.Rng.lerp;
import static com.heliosphere.core.fx.sim.Rng.smooth;
import static com.heliosphere.core.fx.sim.Rng.window;

/**
 * The small effects of combat, each a closed-form function of its age like {@link MushroomCloud}: muzzle
 * flashes, tracers and beams, impacts, shell casings, and the explosions of the various warheads.
 * Every method returns whether the effect is still alive at {@code t}.
 */
public final class Blasts {
    private Blasts() {}

    private static float red(int rgb) { return ((rgb >> 16) & 255) / 255f; }
    private static float green(int rgb) { return ((rgb >> 8) & 255) / 255f; }
    private static float blue(int rgb) { return (rgb & 255) / 255f; }

    private static final ThreadLocal<float[]> TMP = ThreadLocal.withInitial(() -> new float[3]);

    // ---- guns ---------------------------------------------------------------------------------------------------

    /** How fast a tracer round visibly travels, blocks per second. */
    public static final float BULLET_SPEED = 420f;

    /** A tracer round: a short bright streak racing down the line of fire, with a hint of vapour behind it. */
    public static boolean tracer(SpriteSink s, float t, double ax, double ay, double az, double bx, double by, double bz, int rgb, float calibre) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01f) return false;
        float flight = len / BULLET_SPEED;
        if (t > flight + 0.35f) return false;
        float r = red(rgb), g = green(rgb), b = blue(rgb);
        float head = Math.min(len, t * BULLET_SPEED);
        float streak = Math.min(head, 5.5f + 4f * calibre);
        if (t <= flight + 0.02f) {
            float h0 = (head - streak) / len, h1 = head / len;
            float w = 0.055f + 0.05f * calibre;
            s.ribbon(ax + dx * h0, ay + dy * h0, az + dz * h0, ax + dx * h1, ay + dy * h1, az + dz * h1, w * 3.2f, FxAtlas.STREAK, 0, 0, 0, 0, r * 0.7f, g * 0.7f, b * 0.7f);
            s.ribbon(ax + dx * h0, ay + dy * h0, az + dz * h0, ax + dx * h1, ay + dy * h1, az + dz * h1, w, FxAtlas.STREAK, 0, 0, 0, 0, 1f, 0.97f, 0.9f);
            s.billboard(ax + dx * h1, ay + dy * h1, az + dz * h1, w * 7f, 0, FxAtlas.GLOW, 0, 0, 0, 0, r, g, b);
        }
        // the disturbed air along the path
        float trail = (1 - smooth(0f, flight + 0.35f, t)) * 0.07f * (0.6f + calibre);
        float done = head / len;
        if (trail > 0.004f && done > 0.02f)
            s.ribbon(ax, ay, az, ax + dx * done, ay + dy * done, az + dz * done, 0.05f + 0.06f * calibre, FxAtlas.BEAM, 0.85f, 0.85f, 0.85f, trail, 0, 0, 0);
        return true;
    }

    /** An energy beam: instantaneous, a white-hot core in a coloured sheath that crackles as it dies. */
    public static boolean beam(SpriteSink s, float t, double ax, double ay, double az, double bx, double by, double bz, int rgb, float width, int seed) {
        float life = 0.26f;
        if (t > life) return false;
        float r = red(rgb), g = green(rgb), b = blue(rgb);
        float k = 1 - t / life, hot = k * k;
        float w = width * (0.6f + 0.4f * k);
        s.ribbon(ax, ay, az, bx, by, bz, w * 5.5f, FxAtlas.BEAM, 0, 0, 0, 0, r * 0.45f * k, g * 0.45f * k, b * 0.45f * k);
        s.ribbon(ax, ay, az, bx, by, bz, w * 2.2f, FxAtlas.BEAM, 0, 0, 0, 0, r * hot, g * hot, b * hot);
        s.ribbon(ax, ay, az, bx, by, bz, w * 0.8f, FxAtlas.BEAM, 0, 0, 0, 0, hot, hot, hot);
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int arcs = Math.min(10, 1 + (int) (len / 9));
        int frame = (int) (t * 40);
        for (int i = 0; i < arcs; i++) {                                    // crackle crawling along the beam
            float u0 = Rng.f(seed + frame, i, 1) * 0.86f, u1 = u0 + 0.08f + 0.06f * Rng.f(seed + frame, i, 2);
            s.ribbon(ax + dx * u0, ay + dy * u0, az + dz * u0, ax + dx * u1, ay + dy * u1, az + dz * u1, w * 9f, FxAtlas.BOLT, 0, 0, 0, 0, r * k, g * k, b * k);
        }
        s.billboard(ax, ay, az, w * 16f * k, 0, FxAtlas.GLOW, 0, 0, 0, 0, r * k, g * k, b * k);
        s.billboard(bx, by, bz, w * 26f * k, t * 9f, FxAtlas.FLARE, 0, 0, 0, 0, hot, hot, hot);
        s.billboard(bx, by, bz, w * 22f * k, 0, FxAtlas.GLOW, 0, 0, 0, 0, r * k, g * k, b * k);
        return true;
    }

    /** Muzzle blast: a few frames of flame along the bore, a star of light, then drifting powder smoke. */
    public static boolean muzzle(SpriteSink s, float t, double x, double y, double z, double dx, double dy, double dz, int rgb, float size, boolean energy, int seed) {
        float life = energy ? 0.35f : 1.5f;
        if (t > life) return false;
        float r = red(rgb), g = green(rgb), b = blue(rgb);
        float flash = 1 - smooth(0f, 0.075f, t);
        if (flash > 0.01f) {
            float f = flash * flash;
            for (int i = 0; i < 4; i++) {
                float d = (0.12f + 0.22f * i) * size * (0.6f + 2.2f * Math.min(1, t / 0.03f + 0.5f));
                float w = size * (0.55f - 0.09f * i) * (0.8f + 0.4f * Rng.f(seed, i, 1));
                s.billboard(x + dx * d, y + dy * d, z + dz * d, w, Rng.f(seed, i, 2) * 6.28f, energy ? FxAtlas.GLOW : FxAtlas.FIRE + (i & 3), 0, 0, 0, 0, r * f, g * f, b * f);
            }
            s.billboard(x + dx * 0.1 * size, y + dy * 0.1 * size, z + dz * 0.1 * size, size * 2.4f, Rng.f(seed, 0, 3) * 1.57f, FxAtlas.FLARE, 0, 0, 0, 0, f, f * 0.94f, f * 0.8f);
            s.billboard(x, y, z, size * 3.4f, 0, FxAtlas.GLOW, 0, 0, 0, 0, r * f * 0.55f, g * f * 0.55f, b * f * 0.55f);
        }
        if (energy) {
            float k = 1 - t / life;
            s.billboard(x + dx * 0.15, y + dy * 0.15, z + dz * 0.15, size * (0.6f + t * 5f), t * 4f, FxAtlas.RING, 0, 0, 0, 0, r * k * 0.8f, g * k * 0.8f, b * k * 0.8f);
            return true;
        }
        for (int i = 0; i < 3; i++) {                                       // powder smoke, carried forward then rising
            float st = t - i * 0.02f;
            if (st < 0) continue;
            float d = (0.25f + 0.2f * i) * size + 1.3f * (1 - (float) Math.exp(-st * 5f)) * (0.6f + 0.3f * i);
            float a = window(0f, 0.06f, 0.25f, life, st) * 0.20f;
            float w = size * (0.55f + 1.5f * st);
            float g0 = 0.78f;
            s.billboard(x + dx * d + Rng.s(seed, i, 5) * 0.12 * st, y + dy * d + st * 0.45, z + dz * d + Rng.s(seed, i, 6) * 0.12 * st, w, Rng.f(seed, i, 7) * 6.28f + st, FxAtlas.WISP, g0, g0, g0, a, 0, 0, 0);
        }
        return true;
    }

    /** A spent case: tumbles out to the side, bounces once and lies there a moment. {@code floor} is the ground height under the shooter. */
    public static boolean casing(SpriteSink s, float t, double x, double y, double z, double vx, double vy, double vz, double floor, int seed) {
        float life = 3.2f;
        if (t > life) return false;
        final double g = 18.0;
        double drop = y - floor - 0.03;
        double t1 = drop <= 0 ? 0 : (vy + Math.sqrt(vy * vy + 2 * g * drop)) / g;       // first touchdown
        double px, py, pz;
        float spin;
        if (t < t1) {
            px = x + vx * t; pz = z + vz * t; py = y + vy * t - 0.5 * g * t * t;
            spin = t * 26f;
        } else {
            double x1 = x + vx * t1, z1 = z + vz * t1;
            double v1 = Math.abs(vy - g * t1) * 0.32, t2 = 2 * v1 / g, tb = t - t1;
            if (tb < t2) {
                px = x1 + vx * 0.4 * tb; pz = z1 + vz * 0.4 * tb; py = floor + 0.03 + v1 * tb - 0.5 * g * tb * tb;
                spin = (float) (t1 * 26 + tb * 14);
            } else {
                double roll = Math.min(0.35, tb - t2);
                px = x1 + vx * 0.4 * t2 + vx * 0.15 * roll; pz = z1 + vz * 0.4 * t2 + vz * 0.15 * roll; py = floor + 0.03;
                spin = (float) (t1 * 26 + t2 * 14) + Rng.f(seed, 0, 1);
            }
        }
        float a = 1 - smooth(life - 0.6f, life, t);
        float hot = 1 - smooth(0f, 0.5f, t);
        s.billboard(px, py, pz, 0.11f, spin, FxAtlas.CASING, 0.86f, 0.66f, 0.24f, a, 0.25f * hot, 0.12f * hot, 0.02f * hot);
        return true;
    }

    /** A round striking a surface: sparks thrown back off the normal, a puff of the material, a wink of light. */
    public static boolean impact(SpriteSink s, float t, double x, double y, double z, double nx, double ny, double nz, int dust, float size, boolean energy, int rgb, int seed) {
        float life = energy ? 0.7f : 1.2f;
        if (t > life) return false;
        float flash = 1 - smooth(0f, energy ? 0.14f : 0.07f, t);
        float er = energy ? red(rgb) : 1f, eg = energy ? green(rgb) : 0.85f, eb = energy ? blue(rgb) : 0.55f;
        if (flash > 0.01f) {
            s.billboard(x + nx * 0.06, y + ny * 0.06, z + nz * 0.06, size * 1.5f, 0, FxAtlas.GLOW, 0, 0, 0, 0, er * flash, eg * flash, eb * flash);
            s.billboard(x + nx * 0.06, y + ny * 0.06, z + nz * 0.06, size * 1.2f, seed, FxAtlas.FLARE, 0, 0, 0, 0, flash, flash, flash * 0.9f);
        }
        int sparks = energy ? 6 : 9;
        for (int i = 0; i < sparks; i++) {
            float sl = 0.18f + 0.3f * Rng.f(seed, i, 1);
            if (t > sl) continue;
            float sp = (5f + 9f * Rng.f(seed, i, 2)) * (0.6f + 0.4f * size);
            double vx = nx * sp * 0.7 + Rng.s(seed, i, 3) * sp * 0.7, vy = ny * sp * 0.7 + Rng.s(seed, i, 4) * sp * 0.7, vz = nz * sp * 0.7 + Rng.s(seed, i, 5) * sp * 0.7;
            double t0 = Math.max(0, t - 0.035);
            double ax = x + vx * t0, ay = y + vy * t0 - 9 * t0 * t0, az = z + vz * t0;
            double bx = x + vx * t, by = y + vy * t - 9 * t * t, bz = z + vz * t;
            float k = 1 - t / sl;
            s.ribbon(ax, ay, az, bx, by, bz, 0.035f * size + 0.02f, FxAtlas.SPARK, 0, 0, 0, 0, er * k, eg * k * (energy ? 1 : 0.8f), eb * k * (energy ? 1 : 0.5f));
        }
        if (energy) {
            float k = 1 - t / life;
            s.billboard(x + nx * 0.03, y + ny * 0.03, z + nz * 0.03, size * (0.3f + 1.6f * t), 0, FxAtlas.RING, 0, 0, 0, 0, er * k * 0.7f, eg * k * 0.7f, eb * k * 0.7f);
            s.billboard(x + nx * 0.02, y + ny * 0.02, z + nz * 0.02, size * 0.45f, seed, FxAtlas.SCORCH, 0, 0, 0, 0, er * k * 0.5f, eg * k * 0.3f, eb * k * 0.3f);
            return true;
        }
        float dr = red(dust) * 0.85f + 0.1f, dg = green(dust) * 0.85f + 0.1f, db = blue(dust) * 0.85f + 0.1f;
        for (int i = 0; i < 3; i++) {
            float d = 0.12f + (0.5f + 0.25f * i) * (1 - (float) Math.exp(-t * 6f));
            float a = window(0f, 0.03f, 0.2f, life, t) * 0.34f;
            s.billboard(x + nx * d + Rng.s(seed, i, 7) * 0.12, y + ny * d + t * 0.25 + Rng.s(seed, i, 8) * 0.1, z + nz * d + Rng.s(seed, i, 9) * 0.12,
                    size * (0.35f + 1.1f * t + 0.15f * i), Rng.f(seed, i, 10) * 6.28f, FxAtlas.DUST + (i & 1), dr, dg, db, a, 0, 0, 0);
        }
        return true;
    }

    /** A pressure pulse rolling out along the ground (sonic turrets, shock rounds). */
    public static boolean ring(SpriteSink s, float t, double x, double y, double z, int rgb, float radius) {
        float life = 0.7f;
        if (t > life) return false;
        float k = 1 - t / life, grow = 1 - (float) Math.exp(-t * 6f);
        float r = red(rgb), g = green(rgb), b = blue(rgb);
        for (int i = 0; i < 3; i++) {
            float rr = radius * grow * (1 - i * 0.18f);
            if (rr <= 0.05f) continue;
            float e = k * (0.7f - 0.2f * i);
            s.ground(x, y + 0.08 + i * 0.25, z, rr * 2 / 0.8f, i * 1.1f, FxAtlas.RING, 0, 0, 0, 0, r * e, g * e, b * e);
        }
        s.ground(x, y + 0.06, z, radius * grow * 2.2f, 0.4f, FxAtlas.SHOCK, 0.95f, 0.95f, 1f, k * 0.25f, r * k * 0.2f, g * k * 0.2f, b * k * 0.2f);
        s.billboard(x, y + 0.5, z, radius * 0.9f * k, 0, FxAtlas.GLOW, 0, 0, 0, 0, r * k * 0.5f, g * k * 0.5f, b * k * 0.5f);
        return true;
    }

    // ---- explosions ---------------------------------------------------------------------------------------------

    /**
     * A high-explosive burst of vanilla explosion power {@code power} (a grenade is about 2.6): flash, a ball of
     * flame that blackens into a rising knot of smoke, sparks, flung debris and a dust ring along the ground.
     */
    public static boolean explosion(SpriteSink s, float t, double x, double y, double z, float power, int seed, float day) {
        float life = 3.2f + power * 0.35f;
        if (t > life) return false;
        float R = 0.9f + power * 1.25f;
        float[] c = TMP.get();
        float flash = 1 - smooth(0f, 0.16f, t);
        if (flash > 0.01f) {
            float f = flash * flash;
            s.billboard(x, y, z, R * 9f, 0, FxAtlas.GLOW, 0, 0, 0, 0, f, f * 0.9f, f * 0.7f);
            s.billboard(x, y, z, R * 7f, seed, FxAtlas.FLARE, 0, 0, 0, 0, f, f, f * 0.9f);
        }
        float glowT = (float) Math.exp(-t / 0.5f);
        if (glowT > 0.02f) s.ground(x, y + 0.08, z, R * 7f, 0, FxAtlas.GLOW, 0, 0, 0, 0, glowT * 0.9f, glowT * 0.42f, glowT * 0.1f);

        // the fireball, cooling into smoke that lifts away
        int n = 12 + (int) (power * 4);
        float expand = 1 - (float) Math.exp(-t / 0.11f);
        float heat = (float) Math.exp(-t / 0.34f);
        for (int i = 0; i < n; i++) {
            float th = Rng.f(seed, i, 1) * 6.2832f, cp = Rng.s(seed, i, 2) * 0.9f + 0.1f, sp = (float) Math.sqrt(Math.max(0, 1 - cp * cp));
            float q = 0.35f + 0.65f * Rng.f(seed, i, 3);
            float rad = R * q * expand * (1 + 0.25f * t);
            float rise = (0.9f + 1.6f * Rng.f(seed, i, 4)) * t * (1 + 0.3f * power) / (1 + t * 0.35f);
            float local = heat * (1.35f - q * 0.6f) + 0.12f * Rng.s(seed, i, 5) * heat;
            MushroomCloud.glow(local, c);
            float e = clamp01(local * 1.6f);
            float smoke = smooth(0.9f, 0.3f, local);
            float a = (0.3f + 0.45f * smoke) * (1 - smooth(life * 0.35f, life, t)) * smooth(0, 0.03f, t);
            float lit = day * (0.16f + 0.2f * Rng.f(seed, i, 6)) + 0.25f * heat;
            float size = R * (0.85f + 0.6f * Rng.f(seed, i, 7)) * (0.55f + 0.45f * expand) * (1 + 0.35f * t);
            s.billboard(x + Math.cos(th) * sp * rad, y + cp * rad * 0.8f + rise, z + Math.sin(th) * sp * rad, size,
                    Rng.f(seed, i, 8) * 6.28f + t * Rng.s(seed, i, 9), local > 0.45f ? FxAtlas.FIRE + (i & 3) : FxAtlas.SMOKE + (i & 7),
                    lit, lit * 0.95f, lit * 0.9f, a, c[0] * e, c[1] * e, c[2] * e);
        }
        // sparks
        int sparks = 18 + (int) (power * 5);
        for (int i = 0; i < sparks; i++) {
            float sl = 0.45f + 0.9f * Rng.f(seed, i, 20);
            if (t > sl) continue;
            float th = Rng.f(seed, i, 21) * 6.2832f, el = Rng.f(seed, i, 22) * 1.45f;
            float v = (9f + 15f * Rng.f(seed, i, 23)) * (0.7f + 0.2f * power);
            float vh = v * (float) Math.cos(el), vv = v * (float) Math.sin(el);
            float t0 = Math.max(0, t - 0.045f);
            float d0 = (1 - (float) Math.exp(-t0 * 1.6f)) / 1.6f, d1 = (1 - (float) Math.exp(-t * 1.6f)) / 1.6f;
            float k = 1 - t / sl;
            s.ribbon(x + Math.cos(th) * vh * d0, y + vv * d0 - 7 * t0 * t0, z + Math.sin(th) * vh * d0,
                    x + Math.cos(th) * vh * d1, y + vv * d1 - 7 * t * t, z + Math.sin(th) * vh * d1,
                    0.06f + 0.02f * power, FxAtlas.SPARK, 0, 0, 0, 0, k, k * 0.7f, k * 0.3f);
        }
        // debris: dark chunks on ballistic arcs
        int chunks = 6 + (int) (power * 2);
        for (int i = 0; i < chunks; i++) {
            float sl = 1.0f + 0.8f * Rng.f(seed, i, 30);
            if (t > sl) continue;
            float th = Rng.f(seed, i, 31) * 6.2832f, el = 0.5f + Rng.f(seed, i, 32) * 0.9f;
            float v = (6f + 8f * Rng.f(seed, i, 33)) * (0.75f + 0.15f * power);
            float px = v * (float) Math.cos(el) * t, py = v * (float) Math.sin(el) * t - 9f * t * t;
            if (py < -0.3f) continue;
            float a = 1 - smooth(sl * 0.7f, sl, t);
            s.billboard(x + Math.cos(th) * px, y + py, z + Math.sin(th) * px, 0.22f + 0.2f * Rng.f(seed, i, 34) + 0.04f * power, t * 7f * Rng.s(seed, i, 35),
                    FxAtlas.DEBRIS, 0.2f * day, 0.17f * day, 0.14f * day, a, 0, 0, 0);
        }
        // ground: shock ring and kicked-up dust
        float ringT = t / 0.4f;
        if (ringT < 1) {
            float k = 1 - ringT;
            s.ground(x, y + 0.1, z, R * 5.5f * (0.15f + 0.85f * ringT), seed, FxAtlas.SHOCK, 0.9f * day, 0.88f * day, 0.84f * day, k * 0.45f, k * 0.3f, k * 0.25f, k * 0.2f);
        }
        int dust = 8 + (int) (power * 2);
        for (int i = 0; i < dust; i++) {
            float th = (i + Rng.f(seed, i, 40)) / dust * 6.2832f;
            float d = R * (0.6f + 1.9f * (1 - (float) Math.exp(-t * 3.2f)));
            float a = window(0f, 0.1f, 0.5f, life * 0.8f, t) * 0.3f;
            float l = day * (0.5f + 0.2f * Rng.f(seed, i, 41));
            s.billboard(x + Math.cos(th) * d, y + 0.3 + 0.5 * t, z + Math.sin(th) * d, R * (0.7f + 0.8f * t), Rng.f(seed, i, 42) * 6.28f,
                    FxAtlas.DUST + (i & 1), l, l * 0.92f, l * 0.82f, a, 0, 0, 0);
        }
        return true;
    }

    /** A plasma bolt bursting: a searing ball of the bolt's colour, arcs of discharge and a shell of ionised air. */
    public static boolean plasma(SpriteSink s, float t, double x, double y, double z, float power, int rgb, int seed) {
        float life = 1.1f;
        if (t > life) return false;
        float R = 0.8f + power * 1.0f;
        float r = red(rgb), g = green(rgb), b = blue(rgb);
        float k = 1 - t / life, hot = (float) Math.exp(-t / 0.18f);
        float grow = 1 - (float) Math.exp(-t / 0.07f);
        s.billboard(x, y, z, R * 8f * (0.5f + 0.5f * grow), 0, FxAtlas.GLOW, 0, 0, 0, 0, r * hot, g * hot, b * hot);
        s.billboard(x, y, z, R * 2.6f * grow * (0.4f + 0.6f * k), 0, FxAtlas.CORE, 0, 0, 0, 0, hot, hot, hot);
        s.billboard(x, y, z, R * 6f, t * 3f, FxAtlas.FLARE, 0, 0, 0, 0, hot, hot, hot);
        float shell = R * (0.6f + 3.2f * (1 - (float) Math.exp(-t * 5f)));
        s.billboard(x, y, z, shell * 2 / 0.8f, 0, FxAtlas.RING, 0, 0, 0, 0, r * k * 0.8f, g * k * 0.8f, b * k * 0.8f);
        s.ground(x, y + 0.08, z, shell * 2.2f / 0.8f, seed, FxAtlas.RING, 0, 0, 0, 0, r * k * 0.5f, g * k * 0.5f, b * k * 0.5f);
        int frame = (int) (t * 30);
        for (int i = 0; i < 9; i++) {                                       // discharge arcs, re-rolled every frame
            if (t > 0.5f) break;
            float th = Rng.f(seed + frame, i, 1) * 6.2832f, cp = Rng.s(seed + frame, i, 2), sp = (float) Math.sqrt(1 - cp * cp);
            float len = R * (1.2f + 2.4f * Rng.f(seed + frame, i, 3));
            float e = (1 - t / 0.5f);
            s.ribbon(x, y, z, x + Math.cos(th) * sp * len, y + cp * len, z + Math.sin(th) * sp * len, R * 0.9f, FxAtlas.BOLT, 0, 0, 0, 0, r * e, g * e, b * e);
        }
        for (int i = 0; i < 16; i++) {                                      // glowing droplets
            float sl = 0.4f + 0.6f * Rng.f(seed, i, 10);
            if (t > sl) continue;
            float th = Rng.f(seed, i, 11) * 6.2832f, cp = Rng.s(seed, i, 12), sp = (float) Math.sqrt(1 - cp * cp);
            float d = R * (1 + 3.5f * Rng.f(seed, i, 13)) * (1 - (float) Math.exp(-t * 4f));
            float e = 1 - t / sl;
            s.billboard(x + Math.cos(th) * sp * d, y + cp * d - 2 * t * t, z + Math.sin(th) * sp * d, 0.28f * R, 0, FxAtlas.DOT, 0, 0, 0, 0, r * e + 0.3f * e, g * e + 0.3f * e, b * e + 0.3f * e);
        }
        return true;
    }

    /** Matter meeting antimatter: an instant of total white, a razor-thin shell of gamma light, violet embers. */
    public static boolean antimatter(SpriteSink s, float t, double x, double y, double z, float power, int seed) {
        float life = 1.5f;
        if (t > life) return false;
        float R = 1.2f + power * 1.1f;
        float white = (float) Math.exp(-t / 0.09f), k = 1 - t / life;
        s.billboard(x, y, z, R * 14f, 0, FxAtlas.GLOW, 0, 0, 0, 0, white, white, white);
        s.billboard(x, y, z, R * 3.2f * (0.4f + 0.6f * white), 0, FxAtlas.CORE, 0, 0, 0, 0, white, white, white);
        s.billboard(x, y, z, R * 12f, 0.4f, FxAtlas.FLARE, 0, 0, 0, 0, white, white * 0.9f, white);
        float shell = R * 7.5f * (1 - (float) Math.exp(-t * 7f));
        float e = k * k;
        s.billboard(x, y, z, shell * 2 / 0.8f, 0, FxAtlas.RING, 0, 0, 0, 0, 0.75f * e, 0.45f * e, 1.0f * e);
        s.ground(x, y + 0.08, z, shell * 2 / 0.8f, 0, FxAtlas.RING, 0, 0, 0, 0, 0.6f * e, 0.35f * e, 0.9f * e);
        s.billboard(x, y, z, shell * 1.2f / 0.8f, 1f, FxAtlas.RING, 0, 0, 0, 0, 0.9f * e, 0.8f * e, 1.0f * e);
        for (int i = 0; i < 26; i++) {
            float th = Rng.f(seed, i, 1) * 6.2832f, cp = Rng.s(seed, i, 2), sp = (float) Math.sqrt(1 - cp * cp);
            float d = R * (0.8f + 4.5f * Rng.f(seed, i, 3)) * (1 - (float) Math.exp(-t * 3f));
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 30 + i);
            s.billboard(x + Math.cos(th) * sp * d, y + cp * d + 0.5f * t, z + Math.sin(th) * sp * d, 0.25f * R * k, 0, FxAtlas.DOT, 0, 0, 0, 0, 0.7f * k * tw, 0.3f * k * tw, 1.0f * k * tw);
        }
        return true;
    }

    /** How long a singularity stays open, seconds. */
    public static final float SINGULARITY_LIFE = 2.2f;

    /** A singularity: a sphere of nothing ringed with bent light, matter spiralling in, and a white collapse. */
    public static boolean singularity(SpriteSink s, float t, double x, double y, double z, float radius, int seed) {
        float life = SINGULARITY_LIFE;
        if (t > life + 0.5f) return false;
        if (t > life) {                                                     // collapse
            float k = 1 - (t - life) / 0.5f, f = k * k;
            s.billboard(x, y, z, radius * 5f * f + 1, 0, FxAtlas.GLOW, 0, 0, 0, 0, f, f * 0.85f, f);
            s.billboard(x, y, z, radius * 9f, 0.3f, FxAtlas.FLARE, 0, 0, 0, 0, f, f, f);
            s.billboard(x, y, z, radius * 4.5f * (1 - k) / 0.8f, 0, FxAtlas.RING, 0, 0, 0, 0, 0.7f * k, 0.4f * k, 1f * k);
            return true;
        }
        float open = smooth(0f, 0.25f, t) * (1 - smooth(life - 0.15f, life, t) * 0.6f);
        float hole = radius * 0.34f * open * (1 + 0.04f * (float) Math.sin(t * 23));
        // matter falling in: spiral arms in a tilted disc
        int n = 90;
        for (int i = 0; i < n; i++) {
            float phase = Rng.f(seed, i, 1);
            float u = (phase + t * (0.5f + 0.4f * Rng.f(seed, i, 2))) % 1f;       // 0 far out -> 1 swallowed
            float d = radius * (1.25f - u * 1.05f);
            float ang = Rng.f(seed, i, 3) * 6.2832f + u * 7.5f + t * 2f;
            float tilt = 0.35f;
            double px = Math.cos(ang) * d, pz = Math.sin(ang) * d, py = Math.sin(ang) * d * tilt + Rng.s(seed, i, 4) * 0.15f * radius * (1 - u);
            float heat = u * u;
            float e = open * smooth(0f, 0.15f, u) * (1 - smooth(0.9f, 1f, u));
            s.billboard(x + px, y + py, z + pz, radius * (0.22f - 0.12f * u), ang, (i & 3) == 0 ? FxAtlas.FIRE + (i & 3) : FxAtlas.GLOW, 0, 0, 0, 0,
                    lerp(0.45f, 1f, heat) * e, lerp(0.15f, 0.75f, heat) * e, lerp(0.9f, 0.6f, heat) * e);
        }
        // the photon ring, then the hole itself in front of everything behind it
        s.billboard(x, y, z, hole * 2.9f / 0.8f, 0, FxAtlas.RING, 0, 0, 0, 0, open, 0.82f * open, 0.6f * open);
        s.billboard(x, y, z, hole * 5.5f, 0, FxAtlas.GLOW, 0, 0, 0, 0, 0.35f * open, 0.15f * open, 0.55f * open);
        s.billboard(x, y, z, hole * 2.3f, 0, FxAtlas.CORE, 0, 0, 0, open, 0, 0, 0);
        return true;
    }

    /** How long one batch of fallout flakes lasts, seconds. Spawn a batch every second or so for a steady fall. */
    public static final float ASH_LIFE = 6f;

    /** Fallout drifting down around a point (the viewer): grey flakes, and now and then a mote that glows. */
    public static boolean ash(SpriteSink s, float t, double x, double y, double z, float intensity, int seed, float day) {
        if (t > ASH_LIFE) return false;
        int n = (int) (10 + 70 * intensity);
        float fade = window(0f, 1.2f, ASH_LIFE - 1.5f, ASH_LIFE, t);
        for (int i = 0; i < n; i++) {
            float fx = Rng.s(seed, i, 1) * 16f, fz = Rng.s(seed, i, 2) * 16f, fy = -3f + Rng.f(seed, i, 3) * 14f;
            float fall = (0.7f + 0.9f * Rng.f(seed, i, 4)) * t;
            float sway = 0.5f * (float) Math.sin(t * (0.8f + Rng.f(seed, i, 5)) + i);
            boolean hot = (i & 15) == 0;
            float size = hot ? 0.16f : 0.07f + 0.07f * Rng.f(seed, i, 6);
            float l = day * (0.62f + 0.2f * Rng.f(seed, i, 7));
            if (hot) s.billboard(x + fx + sway, y + fy - fall, z + fz + sway * 0.6, size, 0, FxAtlas.DOT, 0, 0, 0, 0, 0.35f * fade, 0.9f * fade, 0.2f * fade);
            else s.billboard(x + fx + sway, y + fy - fall, z + fz + sway * 0.6, size, t + i, FxAtlas.DEBRIS, l, l, l * 0.97f, 0.6f * fade, 0, 0, 0);
        }
        return true;
    }
}
