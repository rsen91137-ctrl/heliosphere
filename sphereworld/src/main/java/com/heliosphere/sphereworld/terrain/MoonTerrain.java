package com.heliosphere.sphereworld.terrain;

/**
 * The Moon at 1/100 scale with 3x vertical exaggeration. Maria, basins and named craters sit at
 * their real selenographic coordinates (east longitude positive).
 */
public final class MoonTerrain implements PlanetTerrain {
    public static final double RADIUS_KM = 1737.4;
    private static final double R_BLOCKS = RADIUS_KM * 10;
    private static final double V = V_BLOCKS_PER_KM;

    /** lat, lon, radius km, high-titanium */
    private record Mare(Region r, boolean highTi) {}

    private static final Mare[] MARIA = {
            new Mare(new Region("Mare Imbrium", 32.8, -15.6, 560), false),
            new Mare(new Region("Mare Serenitatis", 28.0, 17.5, 350), false),
            new Mare(new Region("Mare Tranquillitatis", 8.5, 31.4, 430), true),
            new Mare(new Region("Mare Crisium", 17.0, 59.1, 275), false),
            new Mare(new Region("Mare Fecunditatis", -7.8, 51.3, 440), false),
            new Mare(new Region("Mare Nectaris", -15.2, 35.5, 166), false),
            new Mare(new Region("Mare Nubium", -21.3, -16.6, 350), false),
            new Mare(new Region("Mare Humorum", -24.4, -38.6, 194), false),
            new Mare(new Region("Mare Vaporum", 13.3, 3.6, 120), false),
            new Mare(new Region("Mare Cognitum", -10.0, -23.1, 190), false),
            new Mare(new Region("Oceanus Procellarum N", 28, -56, 650), true),
            new Mare(new Region("Oceanus Procellarum C", 8, -52, 600), true),
            new Mare(new Region("Oceanus Procellarum S", -6, -40, 420), true),
            new Mare(new Region("Mare Frigoris W", 56, -35, 190), false),
            new Mare(new Region("Mare Frigoris C", 56, -8, 190), false),
            new Mare(new Region("Mare Frigoris E", 57, 20, 170), false),
            new Mare(new Region("Mare Moscoviense", 27.3, 147.9, 138), false),
    };

    /** lat, lon, diameter km, depth km */
    private record Crater(Region r, double depthKm, boolean flooded) {}

    private static final Crater[] CRATERS = {
            new Crater(new Region("Tycho", -43.31, -11.36, 42.5), 4.8, false),
            new Crater(new Region("Copernicus", 9.62, -20.08, 46.5), 3.8, false),
            new Crater(new Region("Clavius", -58.62, -14.73, 112), 3.5, false),
            new Crater(new Region("Plato", 51.62, -9.38, 50.5), 2.0, true),
            new Crater(new Region("Aristarchus", 23.73, -47.49, 20), 3.0, false),
            new Crater(new Region("Kepler", 8.12, -38.01, 15.5), 2.6, false),
            new Crater(new Region("Theophilus", -11.45, 26.28, 49.5), 4.2, false),
            new Crater(new Region("Langrenus", -8.86, 61.04, 66), 4.4, false),
            new Crater(new Region("Ptolemaeus", -9.16, -1.84, 76.5), 2.4, true),
            new Crater(new Region("Shackleton", -89.67, 129.78, 10.5), 4.2, false),
            new Crater(new Region("Peary", 88.63, 33.0, 36.5), 2.5, false),
    };

    private static final Region SPA = new Region("South Pole-Aitken", -53, -169, 1250);
    private static final Region ORIENTALE = new Region("Mare Orientale", -19.4, -92.8, 465);
    private static final Region PKT = new Region("Procellarum KREEP Terrane", 25, -30, 1500);

    private final SimplexNoise noise;
    private final SimplexNoise detail;
    private final CraterField craters;

    public MoonTerrain(long seed) {
        noise = new SimplexNoise(seed ^ 0x4D4F4F4EL);
        detail = new SimplexNoise(seed * 31 + 7);
        craters = new CraterField(seed ^ 0xC2A7E2L, R_BLOCKS,
                new double[]{1600, 760, 360, 170, 80, 38, 18, 8},
                new double[]{0.30, 0.45, 0.55, 0.62, 0.66, 0.70, 0.72, 0.72}, 1.0);
    }

    @Override public int datumY() { return 200; }

    @Override
    public void sample(double lat, double lon, Column c) {
        c.reset();
        CraterField.Hit hit = new CraterField.Hit();
        double la = Math.toRadians(lat), lo = Math.toRadians(lon), cl = Math.cos(la);
        double px = cl * Math.cos(lo), py = Math.sin(la), pz = -cl * Math.sin(lo);

        // Highland base relief and rolling hills
        double h = noise.fbm(px, py, pz, 3.0, 5, 0.55) * 2.2 * V;              // +-2 km broad
        h += detail.fbm(px, py, pz, 180.0, 4, 0.5) * 0.25 * V;                  // hummocks
        h += 1.2 * V;                                                             // highlands sit above maria

        // Large impact basins
        double spa = SPA.x(px, py, pz, RADIUS_KM);
        h += basin(spa, 6.2 * V, 1.2 * V);
        double ori = ORIENTALE.x(px, py, pz, RADIUS_KM);
        h += basin(ori, 3.0 * V, 1.0 * V);
        if (ori > 1 && ori < 2) h += Math.sin((ori - 1) * Math.PI * 3) * 0.4 * V * (2 - ori); // Cordillera rings

        // Maria: flat dark basalt plains about 2 km below the highlands
        double mare = 0;
        boolean highTi = false;
        for (Mare m : MARIA) {
            double x = m.r.x(px, py, pz, RADIUS_KM);
            double edge = 1 + noise.fbm(px, py, pz, 25, 3, 0.5) * 0.18;
            double f = 1 - Region.smoothstep(edge - 0.12, edge, x);
            if (f > mare) { mare = f; highTi = m.highTi; }
        }
        double mareLevel = -1.6 * V + detail.fbm(px, py, pz, 60, 3, 0.5) * 0.08 * V;
        h = h * (1 - mare) + mareLevel * mare;
        if (mare > 0.3) h += detail.ridged(px, py, pz, 40, 3) * 0.15 * V * mare; // wrinkle ridges

        // Procedural craters (maria are younger: fewer and smaller)
        double density = 1 - 0.7 * mare;
        craters.sample(px, py, pz, density, mare > 0.5 ? 200 : 1e9, hit);
        h += hit.delta;

        // Named craters
        for (Crater cr : CRATERS) {
            double x = cr.r.x(px, py, pz, RADIUS_KM);
            if (x > 2.5) continue;
            double depth = cr.depthKm * V;
            double rim = depth * 0.2;
            double f;
            if (x < 1) {
                double t = Math.max(0, (x - 0.4) / 0.6);
                f = -depth + (depth + rim) * t * t * (3 - 2 * t);
                if (cr.flooded) f = Math.max(f, -depth * 0.6);
                else if (cr.r.radiusKm > 15) f += depth * 0.5 * Math.exp(-(x / 0.13) * (x / 0.13));
                hit.insideDepth = Math.max(hit.insideDepth, rim - f);
            } else f = rim / (x * x * x);
            h += f;
        }

        c.height = h;
        // ---------------- materials
        c.kreep = (float) (1 - Region.smoothstep(0.7, 1.0, PKT.x(px, py, pz, RADIUS_KM)));
        c.highTi = (float) (highTi ? mare : 0);
        c.iron = (float) hit.freshCentre;
        if (mare > 0.5) {
            boolean he3 = highTi && detail.noise(px * 900, py * 900, pz * 900) > 0.15;
            c.top = he3 ? Mat.HE3_REGOLITH : Mat.MARE_REGOLITH;
            c.topDepth = 3;
            c.sub = highTi ? Mat.HIGH_TI_BASALT : Mat.MARE_BASALT;
            c.subDepth = 18 + (int) (detail.noise(px * 50, py * 50, pz * 50) * 6);
            c.rock = Mat.ANORTHOSITE;
            c.color = highTi ? 0x4E4C4A : 0x5C5A57;
        } else {
            c.top = Mat.REGOLITH;
            c.topDepth = 3;
            c.sub = Mat.ANORTHOSITE;
            c.subDepth = 30;
            c.rock = Mat.ANORTHOSITE;
            c.color = 0x9C9A96;
        }
        // Permanently shadowed polar crater floors hold water ice
        double polar = Region.smoothstep(78, 86, Math.abs(lat));
        if (polar > 0 && hit.insideDepth > 6) {
            c.ice = (float) polar;
            c.layer = Mat.MOON_ICE;
            c.layerDepth = 2;
            c.layerThickness = 2 + (int) Math.min(5, hit.insideDepth / 8);
            c.color = 0x7F8A90;
        }
        // Bright ray craters
        if (hit.freshCentre > 0.6) c.color = 0xB8B6B2;
    }

    @Override
    public double mapShade(Column c) { return 0.75 + Math.max(-0.2, Math.min(0.35, c.height / (6 * V))); }

    private static double basin(double x, double depth, double rim) {
        if (x < 1) {
            double t = x * x;
            return -depth * (1 - t) + rim * Region.smoothstep(0.7, 1.0, x);
        }
        if (x < 1.6) return rim * (1 - Region.smoothstep(1.0, 1.6, x));
        return 0;
    }
}
