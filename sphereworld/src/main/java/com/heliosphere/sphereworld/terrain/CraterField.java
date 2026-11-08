package com.heliosphere.sphereworld.terrain;

/**
 * Procedural impact craters scattered uniformly on a sphere. Each octave hashes a 3D grid of cells
 * around the unit sphere; every occupied cell holds one crater whose centre is projected onto the sphere,
 * so the field is seamless across the map's east-west seam and the poles.
 */
public final class CraterField {
    private final long seed;
    private final double radiusBlocks;
    private final double[] diameters;   // blocks
    private final double[] chance;      // occupancy per cell
    private final double depthFactor;   // 1 = fresh lunar craters, <1 = degraded

    public CraterField(long seed, double radiusBlocks, double[] diameters, double[] chance, double depthFactor) {
        this.seed = seed;
        this.radiusBlocks = radiusBlocks;
        this.diameters = diameters;
        this.chance = chance;
        this.depthFactor = depthFactor;
    }

    /** Result of {@link #sample}: height delta and whether the point lies inside a crater bowl. */
    public static final class Hit {
        public double delta;
        public double insideDepth;   // max depth below rim of any bowl containing the point (blocks)
        public double freshCentre;   // >0 near the centre of a mid-sized crater (meteoric iron)
        public double maxDiameter;   // largest crater affecting this point
        void reset() { delta = 0; insideDepth = 0; freshCentre = 0; maxDiameter = 0; }
    }

    /** @param density per-octave multiplier in [0,1] applied to occupancy (e.g. young maria) */
    public void sample(double px, double py, double pz, double density, double maxDiameterForDensity, Hit hit) {
        hit.reset();
        for (int o = 0; o < diameters.length; o++) {
            double d = diameters[o];
            double occ = chance[o] * (d > maxDiameterForDensity ? density * 0.15 : density);
            if (occ <= 0.001) continue;
            double cell = d * 2.4 / radiusBlocks;           // cell size on the unit sphere
            int cx = floor(px / cell), cy = floor(py / cell), cz = floor(pz / cell);
            for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) for (int k = -1; k <= 1; k++) {
                long h = hash(cx + i, cy + j, cz + k, o);
                if (unit(h) > occ) continue;
                double ox = (cx + i + unit(h * 31 + 7)) * cell;
                double oy = (cy + j + unit(h * 131 + 17)) * cell;
                double oz = (cz + k + unit(h * 1031 + 29)) * cell;
                double len = Math.sqrt(ox * ox + oy * oy + oz * oz);
                if (len < 1e-9) continue;
                // reject centres far from the sphere surface so density stays uniform
                if (Math.abs(len - 1) > cell * 0.75) continue;
                ox /= len; oy /= len; oz /= len;
                double dx = px - ox, dy = py - oy, dz = pz - oz;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz) * radiusBlocks;
                double r = d * 0.5 * (0.55 + 0.45 * unit(h * 8191 + 3));
                double x = dist / r;
                if (x > 2.6) continue;
                double diam = r * 2;
                apply(x, diam, h, hit);
            }
        }
    }

    private void apply(double x, double diam, long h, Hit hit) {
        double dKm = diam / 10.0;
        double depthKm = dKm < 15 ? 0.2 * dKm : 1.044 * Math.pow(dKm, 0.301);
        double depth = Math.min(depthKm * PlanetTerrain.V_BLOCKS_PER_KM, 0.32 * diam) * depthFactor;
        depth *= 0.75 + 0.5 * unit(h * 77 + 5); // age variation
        double rim = depth * 0.22;
        double f;
        if (x < 1) {
            if (dKm > 15) {
                double t = Math.max(0, (x - 0.35) / 0.65);
                double s = t * t * t * (t * (t * 6 - 15) + 10);
                f = -depth + (depth + rim) * s;
                if (dKm > 30) f += depth * 0.55 * Math.exp(-(x / 0.12) * (x / 0.12));
            } else {
                f = -depth + (depth + rim) * x * x;
            }
            hit.insideDepth = Math.max(hit.insideDepth, (rim - f));
            if (x < 0.2 && diam > 20 && diam < 400) hit.freshCentre = Math.max(hit.freshCentre, 1 - x / 0.2);
        } else {
            double e = 1 / x;
            f = rim * e * e * e * (1 - smooth((x - 2.0) / 0.6));
        }
        hit.delta += f;
        hit.maxDiameter = Math.max(hit.maxDiameter, diam);
    }

    private static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    private static int floor(double v) { int i = (int) v; return v < i ? i - 1 : i; }

    private long hash(int x, int y, int z, int o) {
        long h = seed ^ 0x9E3779B97F4A7C15L;
        h ^= x * 0xBF58476D1CE4E5B9L; h = mix(h);
        h ^= y * 0x94D049BB133111EBL; h = mix(h);
        h ^= z * 0x2545F4914F6CDD1DL; h = mix(h);
        h ^= o * 0x632BE59BD9B4E019L; return mix(h);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double unit(long h) { return ((mix(h) >>> 11) * 0x1.0p-53); }
}
