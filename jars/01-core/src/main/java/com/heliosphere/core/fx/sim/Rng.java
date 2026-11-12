package com.heliosphere.core.fx.sim;

/** Stateless hashing: the same (seed, index, channel) always gives the same number, on any machine. */
public final class Rng {
    private Rng() {}

    public static int hash(int x) {
        x ^= x >>> 16; x *= 0x7feb352d; x ^= x >>> 15; x *= 0x846ca68b; x ^= x >>> 16;
        return x;
    }

    /** Uniform in [0,1). */
    public static float f(int seed, int i, int channel) {
        return (hash(seed * 0x9E3779B1 + hash(i * 0x85EBCA6B + channel * 0xC2B2AE35)) >>> 8) / 16777216f;
    }

    /** Uniform in [-1,1). */
    public static float s(int seed, int i, int channel) { return f(seed, i, channel) * 2f - 1f; }

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public static float clamp01(float v) { return v < 0 ? 0 : Math.min(v, 1); }

    public static float smooth(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }

    /** 0 outside [in0, out1], 1 inside [in1, out0], smooth ramps between. */
    public static float window(float in0, float in1, float out0, float out1, float x) {
        return smooth(in0, in1, x) * (1 - smooth(out0, out1, x));
    }

    // ---- value noise --------------------------------------------------------------------------------------------

    private static float lattice(int x, int y, int z, int seed) {
        return (hash(x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ seed * 0x27d4eb2f) >>> 8) / 16777216f;
    }

    public static float noise(float x, float y, float z, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        float fx = x - xi, fy = y - yi, fz = z - zi;
        float ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy), uz = fz * fz * (3 - 2 * fz);
        float a = lerp(lerp(lattice(xi, yi, zi, seed), lattice(xi + 1, yi, zi, seed), ux),
                lerp(lattice(xi, yi + 1, zi, seed), lattice(xi + 1, yi + 1, zi, seed), ux), uy);
        float b = lerp(lerp(lattice(xi, yi, zi + 1, seed), lattice(xi + 1, yi, zi + 1, seed), ux),
                lerp(lattice(xi, yi + 1, zi + 1, seed), lattice(xi + 1, yi + 1, zi + 1, seed), ux), uy);
        return lerp(a, b, uz);
    }

    public static float fbm(float x, float y, float z, int seed, int octaves) {
        float sum = 0, amp = 0.5f, norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise(x, y, z, seed + o * 131);
            norm += amp; amp *= 0.5f; x *= 2.03f; y *= 2.03f; z *= 2.03f;
        }
        return sum / norm;
    }
}
