package com.heliosphere.sphereworld.terrain;

/** Deterministic terrain of one planet, sampled by latitude/longitude so the surface wraps seamlessly. */
public interface PlanetTerrain {
    /** Vertical exaggeration: blocks per real kilometre of relief (horizontal scale is 10 blocks/km). */
    double V_BLOCKS_PER_KM = 30.0;

    void sample(double latDeg, double lonDeg, Column out);

    /** Y level of the planet's reference surface (datum). */
    int datumY();

    default int minY() { return -256; }
    default int genDepth() { return 1280; }
    /** Material used for the bulk interior (debug columns). */
    default Mat stone() { return Mat.ANORTHOSITE; }
    /** Ore veins: pick a vein material for this column, or null. depth[0..1] = min/max depth below surface. */
    default Mat vein(Column c, net.minecraft.util.RandomSource r, int[] depth) { return null; }

    /** Shade multiplier in [0.5, 1.2] used when baking orbital maps; default derives from height. */
    default double mapShade(Column c) { return 1.0; }
}
