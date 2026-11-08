package com.heliosphere.sphereworld.terrain;

/**
 * Mars at 1/100 scale with 3x vertical exaggeration. Tharsis, Olympus Mons, Valles Marineris, Hellas,
 * the polar caps, Jezero and Gale sit at their real areographic coordinates (east longitude positive).
 */
public final class MarsTerrain implements PlanetTerrain {
    public static final double RADIUS_KM = 3389.5;
    private static final double R_BLOCKS = RADIUS_KM * 10;
    private static final double V = V_BLOCKS_PER_KM;

    private record Volcano(Region r, double heightKm, double calderaKm, double calderaDepthKm, double scarp) {}

    private static final Volcano[] VOLCANOES = {
            new Volcano(new Region("Olympus Mons", 18.65, -133.8, 300), 21.9, 40, 3.0, 0.25),
            new Volcano(new Region("Ascraeus Mons", 11.92, -104.08, 230), 11.0, 30, 2.5, 0),
            new Volcano(new Region("Pavonis Mons", 1.48, -113.0, 190), 7.0, 22, 2.0, 0),
            new Volcano(new Region("Arsia Mons", -8.26, -120.1, 220), 9.5, 55, 1.5, 0),
            new Volcano(new Region("Elysium Mons", 25.02, 147.21, 120), 12.6, 7, 0.8, 0),
            new Volcano(new Region("Alba Mons", 40.47, -109.6, 750), 6.8, 60, 0.5, 0),
    };
    private static final Region THARSIS = new Region("Tharsis", 2, -108, 2600);

    private record Basin(Region r, double depthKm, double rimKm) {}

    private static final Basin[] BASINS = {
            new Basin(new Region("Hellas Planitia", -42.4, 70.5, 1150), 7.0, 2.0),
            new Basin(new Region("Argyre Planitia", -49.7, -44.0, 900), 5.0, 2.0),
            new Basin(new Region("Isidis Planitia", 12.9, 87.0, 750), 3.0, 1.0),
            new Basin(new Region("Utopia Planitia", 49.7, 118.0, 1650), 2.0, 0.5),
    };

    private record Crater(Region r, double depthKm, double peakKm) {}

    private static final Crater[] CRATERS = {
            new Crater(new Region("Gale", -5.4, 137.8, 77), 4.5, 5.5),
            new Crater(new Region("Jezero", 18.38, 77.58, 22.5), 0.6, 0),
            new Crater(new Region("Huygens", -14.0, 55.6, 233), 2.0, 0),
            new Crater(new Region("Schiaparelli", -2.7, 16.7, 230), 2.0, 0),
            new Crater(new Region("Endeavour", -2.3, -5.2, 11), 0.3, 0),
            new Crater(new Region("Korolev", 72.8, 164.5, 41), 2.0, 0),
    };

    /** Valles Marineris centre line (lat, lon) with local half-width km and depth km. */
    private static final double[][] VALLES = {
            {-7.0, -102, 45, 3.0}, {-6.0, -90, 55, 5.0}, {-7.5, -84, 60, 6.0}, {-10.0, -73, 100, 7.0},
            {-13.0, -62, 70, 7.0}, {-13.5, -55, 60, 6.0}, {-12.0, -45, 80, 4.5}, {-8.0, -38, 90, 3.0}};
    private static final double[][] VALLES_XYZ = new double[VALLES.length][3];

    static {
        for (int i = 0; i < VALLES.length; i++) {
            double la = Math.toRadians(VALLES[i][0]), lo = Math.toRadians(VALLES[i][1]), c = Math.cos(la);
            VALLES_XYZ[i][0] = c * Math.cos(lo); VALLES_XYZ[i][1] = Math.sin(la); VALLES_XYZ[i][2] = -c * Math.sin(lo);
        }
    }

    private static final Region NORTH_CAP = new Region("Planum Boreum", 87.0, 0, 560);
    private static final Region SOUTH_CAP = new Region("Planum Australe", -86.5, -30, 480);
    private static final Region OLYMPIA_UNDAE = new Region("Olympia Undae", 81.0, -178, 320);
    private static final Region MERIDIANI = new Region("Meridiani Planum", -1.0, -5.5, 260);
    private static final Region JEZERO_DELTA = new Region("Jezero delta", 18.5, 77.35, 9);
    private static final Region MAWRTH = new Region("Mawrth Vallis", 24.0, -19.0, 200);
    private static final Region NILI = new Region("Nili Fossae", 22.0, 75.0, 220);

    private final SimplexNoise noise;
    private final SimplexNoise detail;
    private final CraterField craters;

    public MarsTerrain(long seed) {
        noise = new SimplexNoise(seed ^ 0x4D415253L);
        detail = new SimplexNoise(seed * 17 + 3);
        craters = new CraterField(seed ^ 0x7E5A11L, R_BLOCKS,
                new double[]{1300, 600, 280, 130, 60, 28, 12},
                new double[]{0.30, 0.40, 0.48, 0.52, 0.52, 0.45, 0.35}, 0.6);
    }

    @Override public int datumY() { return 256; }

    @Override
    public void sample(double lat, double lon, Column c) {
        c.reset();
        CraterField.Hit hit = new CraterField.Hit();
        double la = Math.toRadians(lat), lo = Math.toRadians(lon), cl = Math.cos(la);
        double px = cl * Math.cos(lo), py = Math.sin(la), pz = -cl * Math.sin(lo);

        double h = noise.fbm(px, py, pz, 2.5, 5, 0.55) * 1.6 * V;
        h += detail.fbm(px, py, pz, 140.0, 4, 0.5) * 0.18 * V;

        // Crustal dichotomy: smooth northern lowlands vs cratered southern highlands
        double boundary = 22 + 14 * Math.sin(lo + 1.2) + noise.fbm(px, py, pz, 6, 3, 0.5) * 6;
        double north = Region.smoothstep(boundary - 8, boundary + 8, lat);
        h += (1.5 - 6.0 * north) * V;

        // Tharsis bulge and shield volcanoes
        double tx = THARSIS.x(px, py, pz, RADIUS_KM);
        h += 6.5 * V * Math.exp(-tx * tx * 2.2);
        for (Volcano v : VOLCANOES) {
            double x = v.r.x(px, py, pz, RADIUS_KM);
            if (x >= 1.15) continue;
            double H = v.heightKm * V;
            double cone = x < 1 ? H * Math.pow(1 - x, 1.35) : 0;
            if (v.scarp > 0) cone += H * v.scarp * (1 - Region.smoothstep(0.93, 1.0, x)) * (x < 1 ? 1 : 0);
            double cx = x * v.r.radiusKm / v.calderaKm;
            if (cx < 1.2) cone -= v.calderaDepthKm * V * (1 - Region.smoothstep(0.85, 1.05, cx));
            h += cone;
        }

        // Impact basins
        for (Basin b : BASINS) {
            double x = b.r.x(px, py, pz, RADIUS_KM);
            if (x < 1) h += -b.depthKm * V * (1 - x * x) + b.rimKm * V * Region.smoothstep(0.75, 1.0, x);
            else if (x < 1.5) h += b.rimKm * V * (1 - Region.smoothstep(1.0, 1.5, x));
        }

        // Valles Marineris
        double canyon = vallesDepth(px, py, pz);
        h -= canyon;

        // Polar layered deposits
        double ncap = NORTH_CAP.x(px, py, pz, RADIUS_KM);
        double scap = SOUTH_CAP.x(px, py, pz, RADIUS_KM);
        double nIce = 1 - Region.smoothstep(0.8, 1.0, ncap + detail.fbm(px, py, pz, 30, 3, 0.5) * 0.1);
        double sIce = 1 - Region.smoothstep(0.8, 1.0, scap + detail.fbm(px, py, pz, 30, 3, 0.5) * 0.1);
        if (nIce > 0) {
            double spiral = Math.sin(lo * 3 + (90 - lat) * 0.35);
            double trough = Math.max(0, spiral - 0.75) * 4;
            h += nIce * (3.0 * V * Math.sqrt(Math.max(0, 1 - ncap * ncap)) - trough * 0.4 * V);
        }
        if (sIce > 0) h += sIce * 3.0 * V * Math.sqrt(Math.max(0, 1 - scap * scap));

        // Craters: dense in the old south, sparse in the northern plains
        craters.sample(px, py, pz, 1 - 0.65 * north, 1e9, hit);
        h += hit.delta * (1 - Math.max(nIce, sIce));
        for (Crater cr : CRATERS) {
            double x = cr.r.x(px, py, pz, RADIUS_KM);
            if (x > 2.2) continue;
            double depth = cr.depthKm * V, rim = depth * 0.25, f;
            if (x < 1) {
                double t = Math.max(0, (x - 0.35) / 0.65);
                f = -depth + (depth + rim) * t * t * (3 - 2 * t);
                if (cr.peakKm > 0) f += cr.peakKm * V * Math.exp(-(x / 0.28) * (x / 0.28));
            } else f = rim / (x * x * x);
            h += f;
        }
        c.height = h;

        // ---------------- materials
        c.top = Mat.MARS_REGOLITH;
        c.topDepth = 3;
        c.sub = Mat.MARS_ROCK;
        c.subDepth = 25;
        c.rock = Mat.MARS_BASALT;
        c.color = north > 0.5 ? 0x9C4E2C : 0xB5592E;
        double dune = detail.ridged(px * 3, py * 3, pz * 3, 400, 2);
        if ((north > 0.5 || hit.insideDepth > 15) && dune > 0.55) { c.top = Mat.MARS_SAND; c.topDepth = 4; c.color = 0x5E3C30; }
        if (canyon > 20) { c.color = 0x8E4630; if (detail.noise(px * 300, py * 300, pz * 300) > 0.2) c.top = Mat.GYPSUM; }

        double perch = detail.noise(px * 2200, py * 2200, pz * 2200);
        if (perch > 0.62 - 0.25 * Region.smoothstep(40, 75, Math.abs(lat))) c.top = Mat.PERCHLORATE;
        if (MERIDIANI.x(px, py, pz, RADIUS_KM) < 1 && detail.noise(px * 1800, py * 1800, pz * 1800) > -0.1) {
            c.top = Mat.HEMATITE; c.color = 0x6E4A44;
        }
        if (JEZERO_DELTA.x(px, py, pz, RADIUS_KM) < 1 || MAWRTH.x(px, py, pz, RADIUS_KM) < 1) {
            c.sub = Mat.CLAY; c.subDepth = 8; c.color = 0xA87A5A;
            if (JEZERO_DELTA.x(px, py, pz, RADIUS_KM) < 1) { c.top = Mat.CLAY; c.height += 0.15 * V; }
        }
        if (NILI.x(px, py, pz, RADIUS_KM) < 1) { c.sub = detail.noise(px * 500, py * 500, pz * 500) > 0 ? Mat.OLIVINE : Mat.CLAY; c.color = 0x7A6A4A; }
        if (OLYMPIA_UNDAE.x(px, py, pz, RADIUS_KM) < 1 && nIce < 0.5) { c.top = Mat.GYPSUM; c.topDepth = 4; c.color = 0x6C5040; }

        // Mid- and high-latitude ground ice (deuterium enriched)
        double groundIce = Region.smoothstep(42, 60, Math.abs(lat));
        if (groundIce > 0 && nIce < 0.5 && sIce < 0.5) {
            c.ice = (float) groundIce;
            c.layer = Mat.MARS_ICE;
            c.layerDepth = 2 + (int) ((1 - groundIce) * 3);
            c.layerThickness = 2 + (int) (groundIce * 4);
        }
        if (nIce > 0.5) { c.top = Mat.MARS_ICE; c.topDepth = 6; c.sub = Mat.MARS_ICE; c.subDepth = 60; c.color = 0xEDE8E0; }
        if (sIce > 0.5) { c.top = Mat.DRY_ICE; c.topDepth = 4; c.sub = Mat.MARS_ICE; c.subDepth = 60; c.color = 0xF2F0EC; }
    }

    @Override
    public double mapShade(Column c) { return 0.8 + Math.max(-0.25, Math.min(0.35, c.height / (12 * V))); }

    private double vallesDepth(double px, double py, double pz) {
        double best = 0;
        for (int i = 0; i + 1 < VALLES.length; i++) {
            double[] a = VALLES_XYZ[i], b = VALLES_XYZ[i + 1];
            // segment parameter via great-circle projection
            double nx = a[1] * b[2] - a[2] * b[1], ny = a[2] * b[0] - a[0] * b[2], nz = a[0] * b[1] - a[1] * b[0];
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= nl; ny /= nl; nz /= nl;
            double off = px * nx + py * ny + pz * nz;
            double qx = px - off * nx, qy = py - off * ny, qz = pz - off * nz;
            double ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (ql < 1e-9) continue;
            qx /= ql; qy /= ql; qz /= ql;
            double segAng = Math.acos(clamp(a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
            double ta = Math.acos(clamp(a[0] * qx + a[1] * qy + a[2] * qz));
            double tb = Math.acos(clamp(b[0] * qx + b[1] * qy + b[2] * qz));
            if (ta > segAng + 1e-6 || tb > segAng + 1e-6) continue;
            double t = ta / segAng;
            double distKm = Math.asin(Math.min(1, Math.abs(off))) * RADIUS_KM;
            double half = VALLES[i][2] + (VALLES[i + 1][2] - VALLES[i][2]) * t;
            double depthKm = VALLES[i][3] + (VALLES[i + 1][3] - VALLES[i][3]) * t;
            half *= 1 + detail.fbm(px, py, pz, 40, 3, 0.5) * 0.35;
            if (distKm > half * 1.25) continue;
            double x = distKm / half;
            // terraced walls
            double wall = 1 - Region.smoothstep(0.55, 1.0, x);
            double terr = Math.floor(wall * 5) / 5 * 0.35 + wall * 0.65;
            best = Math.max(best, depthKm * V * terr);
        }
        return best;
    }

    private static double clamp(double v) { return Math.max(-1, Math.min(1, v)); }
}
