package com.heliosphere.core.fx.sim;

/**
 * The effect texture atlas, painted procedurally so the mod ships no image for it and the offline preview
 * uses exactly the pixels the game does. Every tile is premultiplied: alpha is the shape, rgb is the shape
 * times a shading term (so smoke carries its own lumps and fire its own filaments).
 */
public final class FxAtlas {
    private FxAtlas() {}

    public static final int GRID = 8, TILE = 128, SIZE = GRID * TILE;

    public static final int GLOW = 0, CORE = 1, RING = 2, FLARE = 3, STREAK = 4, SPARK = 5, SHOCK = 6, DEBRIS = 7;
    public static final int SMOKE = 8, SMOKE_COUNT = 8;
    public static final int FIRE = 16, FIRE_COUNT = 4;
    public static final int CASING = 20, BEAM = 21, BOLT = 22, DOT = 23, WISP = 24, SCORCH = 25, DUST = 26, DUST_COUNT = 2;

    private static float[] cache;

    /** RGBA floats, row-major, SIZE x SIZE, premultiplied. */
    public static synchronized float[] pixels() {
        if (cache != null) return cache;
        float[] px = new float[SIZE * SIZE * 4];
        for (int tile = 0; tile < GRID * GRID; tile++) {
            int ox = (tile % GRID) * TILE, oy = (tile / GRID) * TILE;
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    // one-texel transparent gutter so linear filtering never bleeds between tiles
                    boolean edge = x == 0 || y == 0 || x == TILE - 1 || y == TILE - 1;
                    float u = (x + 0.5f) / TILE * 2 - 1, v = (y + 0.5f) / TILE * 2 - 1;
                    long sa = edge ? 0 : shade(tile, u, v);
                    float shade = Float.intBitsToFloat((int) (sa >>> 32)), alpha = Float.intBitsToFloat((int) sa);
                    int i = ((oy + y) * SIZE + ox + x) * 4;
                    px[i] = px[i + 1] = px[i + 2] = alpha * shade;
                    px[i + 3] = alpha;
                }
            }
        }
        return cache = px;
    }

    public static float u0(int tile) { return (tile % GRID) / (float) GRID; }
    public static float v0(int tile) { return (tile / GRID) / (float) GRID; }
    public static float span() { return 1f / GRID; }

    private static long pack(float shade, float alpha) {
        return ((long) Float.floatToIntBits(Rng.clamp01(shade)) << 32) | (Float.floatToIntBits(Rng.clamp01(alpha)) & 0xffffffffL);
    }

    /** (shade, alpha) of a tile at u,v in [-1,1]; v points down. */
    private static long shade(int tile, float u, float v) {
        float r = (float) Math.sqrt(u * u + v * v);
        switch (tile) {
            case GLOW: {
                float g = Math.max(0, 1 - r);
                return pack(1, g * g * g * (1 + 3 * r) * 0.999f);
            }
            case CORE:
                return pack(1, 1 - Rng.smooth(0.62f, 0.98f, r));
            case RING: {
                float d = (r - 0.8f) / 0.075f;
                return pack(1, (float) Math.exp(-d * d) * (1 - Rng.smooth(0.94f, 1f, r)));
            }
            case FLARE: {
                float edge = 1 - Rng.smooth(0.75f, 1f, Math.max(Math.abs(u), Math.abs(v)));
                float h = (float) Math.exp(-v * v * 900) * (float) Math.pow(Math.max(0, 1 - Math.abs(u)), 2.2);
                float w = (float) Math.exp(-u * u * 900) * (float) Math.pow(Math.max(0, 1 - Math.abs(v)), 2.2) * 0.75f;
                float d1 = (u + v) * 0.7071f, d2 = (u - v) * 0.7071f;
                float x = ((float) Math.exp(-d1 * d1 * 1500) + (float) Math.exp(-d2 * d2 * 1500)) * (float) Math.pow(Math.max(0, 1 - r), 3) * 0.35f;
                float core = (float) Math.exp(-r * r * 40);
                return pack(1, Math.min(1, h + w + x + core) * edge);
            }
            case STREAK: {
                // u: tail (-1) to head (+1); a hot head and a long thinning tail
                float along = Rng.smooth(-1f, -0.2f, u) * (1 - Rng.smooth(0.82f, 1f, u));
                float width = 0.22f + 0.5f * Rng.smooth(-1f, 0.9f, u);
                float across = (float) Math.exp(-(v * v) / (width * width * 0.18f));
                return pack(1, along * across * (1 - Rng.smooth(0.8f, 1f, Math.abs(v))));
            }
            case SPARK: {
                float along = Rng.smooth(-1f, 0.5f, u) * (1 - Rng.smooth(0.7f, 1f, u));
                float across = (float) Math.exp(-(v * v) / 0.012f);
                return pack(1, along * across);
            }
            case SHOCK: {
                float a = (float) Math.atan2(v, u);
                float n = Rng.fbm(a * 3f + 10, r * 4f, 0.5f, 77, 4);
                float d = (r - 0.78f - (n - 0.5f) * 0.1f) / 0.09f;
                float body = (float) Math.exp(-d * d);
                float inner = Rng.smooth(0.3f, 0.78f, r) * (1 - Rng.smooth(0.78f, 0.8f, r)) * 0.25f;
                return pack(0.75f + 0.25f * n, Math.min(1, body + inner) * (1 - Rng.smooth(0.93f, 1f, r)));
            }
            case DEBRIS: {
                float n = Rng.fbm(u * 2.2f + 5, v * 2.2f + 9, 0.3f, 91, 4);
                float a = 1 - Rng.smooth(0.42f, 0.6f, r + (n - 0.5f) * 0.6f);
                return pack(0.45f + 0.55f * Rng.fbm(u * 4f, v * 4f, 2f, 93, 3), a);
            }
            case CASING: {
                float a = (Math.abs(u) < 0.34f && Math.abs(v) < 0.86f) ? 1 : 0;
                float s = 0.55f + 0.45f * (float) Math.cos(u * 4.2f);
                if (v > 0.66f) s *= 0.55f;          // rim
                if (v < -0.7f) s *= 0.7f;           // mouth
                return pack(s, a);
            }
            case BEAM: {
                float across = (float) Math.exp(-(v * v) / 0.05f) * (1 - Rng.smooth(0.8f, 1f, Math.abs(v)));
                return pack(1, across);
            }
            case BOLT: {
                float off = (Rng.fbm(u * 3f + 3, 0.5f, 0.5f, 41, 4) - 0.5f) * 0.9f;
                float d = (v - off) / 0.07f;
                float halo = (float) Math.exp(-(v - off) * (v - off) / 0.06f) * 0.35f;
                return pack(1, Math.min(1, (float) Math.exp(-d * d) + halo) * (1 - Rng.smooth(0.8f, 1f, Math.abs(v))));
            }
            case DOT:
                return pack(1, (float) Math.exp(-r * r * 9) * (1 - Rng.smooth(0.85f, 1f, r)));
            case WISP: {
                float n = Rng.fbm(u * 1.6f + 20, v * 3.2f + 7, 0.2f, 55, 5);
                float body = 1 - Rng.smooth(0.25f, 0.95f, (float) Math.sqrt(u * u * 0.55f + v * v) + (n - 0.5f) * 0.9f);
                return pack(0.7f + 0.3f * n, body * body);
            }
            case SCORCH: {
                float a = (float) Math.atan2(v, u);
                float n = Rng.fbm(a * 2.5f + 3, r * 3f, 1.5f, 61, 5);
                float body = 1 - Rng.smooth(0.25f, 0.95f, r + (n - 0.5f) * 0.55f);
                return pack(0.5f + 0.5f * n, body);
            }
            default:
                if (tile >= SMOKE && tile < SMOKE + SMOKE_COUNT) return puff(u, v, r, 100 + tile * 17, 0.55f, 0.50f, 0.30f);
                if (tile >= FIRE && tile < FIRE + FIRE_COUNT) return puff(u, v, r, 300 + tile * 29, 0.95f, 0.15f, 0.0f);
                if (tile >= DUST && tile < DUST + DUST_COUNT) return puff(u, v, r, 500 + tile * 13, 0.35f, 0.72f, 0.12f);
                return 0;
        }
    }

    /**
     * A billowing puff: a soft disc whose edge is pushed in and out by noise, with lumpy internal shading.
     * {@code rough} is how ragged the outline is, {@code floor} the darkest shade, {@code toplight} how much
     * brighter the top is than the bottom (smoke lit from the sky).
     */
    private static long puff(float u, float v, float r, int seed, float rough, float floor, float toplight) {
        float warpX = (Rng.fbm(u * 1.4f + 3, v * 1.4f + 8, 0.7f, seed + 5, 3) - 0.5f) * 0.5f;
        float warpY = (Rng.fbm(u * 1.4f + 31, v * 1.4f + 2, 0.7f, seed + 9, 3) - 0.5f) * 0.5f;
        float n = Rng.fbm((u + warpX) * 2.1f + 11, (v + warpY) * 2.1f + 4, 0.3f, seed, 5);
        float billow = 1 - Math.abs(2 * Rng.fbm((u + warpX) * 3.3f, (v + warpY) * 3.3f, 1.3f, seed + 3, 4) - 1);   // ridged: cauliflower lobes
        float edge = r + (n - 0.5f) * rough + (billow - 0.6f) * rough * 0.35f;
        float alpha = 1 - Rng.smooth(0.42f, 0.93f, edge);
        alpha *= 1 - Rng.smooth(0.86f, 1f, r);
        float shade = floor + (1 - floor) * (0.25f + 0.75f * billow) * (0.6f + 0.4f * n);
        shade *= 1 - toplight * 0.5f * (v + 0.2f);          // v grows downward: the underside falls into shadow
        return pack(shade, alpha);
    }
}
