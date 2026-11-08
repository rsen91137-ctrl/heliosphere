package com.heliosphere.sphereworld.terrain;

import java.util.Random;

/** Seeded 3D simplex noise (after Stefan Gustavson's public-domain reference). Output roughly in [-1, 1]. */
public final class SimplexNoise {
    private static final int[][] GRAD = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};
    private static final double F3 = 1.0 / 3.0, G3 = 1.0 / 6.0;
    private final short[] perm = new short[512];
    private final short[] permMod12 = new short[512];

    public SimplexNoise(long seed) {
        short[] p = new short[256];
        for (short i = 0; i < 256; i++) p[i] = i;
        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            short t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) { perm[i] = p[i & 255]; permMod12[i] = (short) (perm[i] % 12); }
    }

    private static int fastFloor(double x) { int xi = (int) x; return x < xi ? xi - 1 : xi; }

    public double noise(double xin, double yin, double zin) {
        double n0, n1, n2, n3;
        double s = (xin + yin + zin) * F3;
        int i = fastFloor(xin + s), j = fastFloor(yin + s), k = fastFloor(zin + s);
        double t = (i + j + k) * G3;
        double x0 = xin - (i - t), y0 = yin - (j - t), z0 = zin - (k - t);
        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
            else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
            else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
        } else {
            if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
            else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
            else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
        }
        double x1 = x0 - i1 + G3, y1 = y0 - j1 + G3, z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0 * G3, y2 = y0 - j2 + 2.0 * G3, z2 = z0 - k2 + 2.0 * G3;
        double x3 = x0 - 1.0 + 3.0 * G3, y3 = y0 - 1.0 + 3.0 * G3, z3 = z0 - 1.0 + 3.0 * G3;
        int ii = i & 255, jj = j & 255, kk = k & 255;
        int gi0 = permMod12[ii + perm[jj + perm[kk]]];
        int gi1 = permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]];
        int gi2 = permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]];
        int gi3 = permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]];
        double t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 < 0) n0 = 0; else { t0 *= t0; n0 = t0 * t0 * dot(GRAD[gi0], x0, y0, z0); }
        double t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 < 0) n1 = 0; else { t1 *= t1; n1 = t1 * t1 * dot(GRAD[gi1], x1, y1, z1); }
        double t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2;
        if (t2 < 0) n2 = 0; else { t2 *= t2; n2 = t2 * t2 * dot(GRAD[gi2], x2, y2, z2); }
        double t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3;
        if (t3 < 0) n3 = 0; else { t3 *= t3; n3 = t3 * t3 * dot(GRAD[gi3], x3, y3, z3); }
        return 32.0 * (n0 + n1 + n2 + n3);
    }

    private static double dot(int[] g, double x, double y, double z) { return g[0] * x + g[1] * y + g[2] * z; }

    /** Fractal sum; frequency is in "cycles per unit" of the input coordinates. */
    public double fbm(double x, double y, double z, double freq, int octaves, double gain) {
        double sum = 0, amp = 1, norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise(x * freq, y * freq, z * freq);
            norm += amp;
            amp *= gain;
            freq *= 2.03;
        }
        return sum / norm;
    }

    /** Ridged fractal in [0, 1]. */
    public double ridged(double x, double y, double z, double freq, int octaves) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int o = 0; o < octaves; o++) {
            double n = 1 - Math.abs(noise(x * freq, y * freq, z * freq));
            sum += amp * n * n;
            norm += amp;
            amp *= 0.5;
            freq *= 2.1;
        }
        return sum / norm;
    }
}
