package com.heliosphere.sphereworld.terrain;

/**
 * Gas and ice giants have no surface. The world is an open sky of cloud decks: ammonia ice on top, ammonium
 * hydrosulfide (or methane for the ice giants) below, and crushing depths under y = 40. Floating aerostat
 * platforms sit at the landing sites. Jupiter's Great Red Spot is a dense, deep-red storm.
 */
public final class GasGiantTerrain implements PlanetTerrain {
    public static final int DECK_Y = 250, PLATFORM_HALF = 8;

    /** Aerostat platforms (lat, lon) per planet; the first one is the default arrival point. */
    public static double[][] platforms(String id) {
        return switch (id) {
            case "jupiter" -> new double[][]{{0, 0}, {-22, 60}, {-15, 75}, {40, -100}};
            case "saturn" -> new double[][]{{0, 0}, {78, 0}, {-35, 120}};
            case "uranus" -> new double[][]{{0, 0}, {-70, 40}};
            default -> new double[][]{{0, 0}, {-22, -120}};
        };
    }

    public final String id;
    public final boolean ice;
    public final Region greatSpot;
    private final SimplexNoise noise;

    public GasGiantTerrain(String id, long seed) {
        this.id = id;
        this.ice = id.equals("uranus") || id.equals("neptune");
        this.greatSpot = switch (id) {
            case "jupiter" -> new Region("Great Red Spot", -22, 60, 8000);
            case "neptune" -> new Region("Great Dark Spot", -22, -120, 4000);
            default -> null;
        };
        this.noise = new SimplexNoise(seed ^ id.hashCode());
    }

    @Override public int datumY() { return DECK_Y; }
    @Override public int minY() { return -64; }
    @Override public int genDepth() { return 512; }
    @Override public Mat stone() { return Mat.AIR; }

    /** 0..1 storm strength at a point (bands plus the great spot). */
    public double storm(double lat, double lon) {
        double s = 0.4 + 0.3 * Math.abs(Math.sin(Math.toRadians(lat) * 7));
        if (greatSpot != null) {
            double la = Math.toRadians(lat), lo = Math.toRadians(lon), c = Math.cos(la);
            double x = greatSpot.x(c * Math.cos(lo), Math.sin(la), -c * Math.sin(lo), radiusKm());
            if (x < 1.3) s = Math.max(s, 1.0 - Math.max(0, x - 1) * 2);
        }
        return Math.min(1, s);
    }

    private double radiusKm() {
        return switch (id) { case "jupiter" -> 69911; case "saturn" -> 58232; case "uranus" -> 25362; default -> 24622; };
    }

    /** Cloud material at a block, or null for open sky. */
    public Mat cloud(double lat, double lon, int x, int y, int z) {
        double band = Math.sin(Math.toRadians(lat) * (ice ? 9 : 16));
        boolean spot = greatSpot != null && storm(lat, lon) > 0.95;
        if (y >= 300 && y <= 336) {
            double cov = 0.15 + 0.25 * band + (spot ? 0.35 : 0);
            double puff = noise.fbm(x / 40.0, y / 14.0, z / 40.0, 1, 3, 0.5);
            if (puff + cov > 0.55) return ice ? Mat.METHANE_CLOUD : (spot ? Mat.NH4SH_CLOUD : Mat.AMMONIA_CLOUD);
        }
        if (y >= 170 && y <= 230) {
            double cov = 0.25 - 0.2 * band + (spot ? 0.4 : 0);
            double puff = noise.fbm(x / 32.0 + 100, y / 12.0, z / 32.0, 1, 3, 0.5);
            if (puff + cov > 0.5) return ice ? Mat.METHANE_CLOUD : Mat.NH4SH_CLOUD;
        }
        return null;
    }

    @Override
    public void sample(double lat, double lon, Column c) {
        c.reset();
        c.height = 0;
        c.top = ice ? Mat.METHANE_CLOUD : Mat.AMMONIA_CLOUD;
        c.color = ice ? (id.equals("uranus") ? 0x9FD7E0 : 0x3E66F0) : (Math.sin(Math.toRadians(lat) * 16) > 0 ? 0xE0CFA8 : 0xB08060);
    }
}
