package com.heliosphere.sphereworld.terrain;

import com.heliosphere.sphereworld.TerrainBlocks;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Data-driven terrain for the smaller worlds: fractal relief, scaled crater fields, real named features
 * (basins, volcanoes, plateaus, scarps) at their true coordinates, polar caps, lineae, dunes, seas and
 * subsurface oceans. One {@link Profile} per body in {@link WorldProfiles}.
 */
public final class ProfileTerrain implements PlanetTerrain {
    public enum Shape { BASIN, DOME, PLATEAU, CLIFF, PATCH }

    public record Feature(Region r, Shape shape, double km, Mat top, Mat liquid, double liquidKm) {
        public static Feature of(String name, double lat, double lon, double radiusKm, Shape s, double km, Mat top) {
            return new Feature(new Region(name, lat, lon, radiusKm), s, km, top, null, 0);
        }
        public Feature lake(Mat liquid, double levelKm) { return new Feature(r, shape, km, top, liquid, levelKm); }
    }

    public record Ore(Mat mat, float chance, int minDepth, int maxDepth) {}

    public static final class Profile {
        public final String id;
        public final double radiusKm;
        double reliefKm = 1.5, hillsKm = 0.25, freq = 3, craters = 0.6, craterDepth = 1.0;
        Mat top, sub, rock;
        int topDepth = 3, subDepth = 20;
        Mat polar; double polarLat = 90;
        Mat lineae; double lineaeFreq = 7, lineaeKm = 0.15;
        Mat dunes; double duneLat = 0;
        Mat sea; double seaKm = -1e9;
        Mat ocean; int oceanDepth, oceanThickness;
        Mat craterFloor;
        int datum = 300;
        final List<Feature> features = new ArrayList<>();
        final List<Ore> ores = new ArrayList<>();

        public Profile(String id, double radiusKm, Mat top, Mat sub, Mat rock) {
            this.id = id; this.radiusKm = radiusKm; this.top = top; this.sub = sub; this.rock = rock;
        }
        public Profile relief(double km, double hills, double freq) { reliefKm = km; hillsKm = hills; this.freq = freq; return this; }
        public Profile craters(double density, double depth) { craters = density; craterDepth = depth; return this; }
        public Profile depths(int top, int sub) { topDepth = top; subDepth = sub; return this; }
        public Profile polar(Mat m, double lat) { polar = m; polarLat = lat; return this; }
        public Profile lineae(Mat m, double freq, double km) { lineae = m; lineaeFreq = freq; lineaeKm = km; return this; }
        public Profile dunes(Mat m, double lat) { dunes = m; duneLat = lat; return this; }
        public Profile sea(Mat m, double km) { sea = m; seaKm = km; return this; }
        public Profile ocean(Mat m, int depth, int thickness) { ocean = m; oceanDepth = depth; oceanThickness = thickness; return this; }
        public Profile craterFloor(Mat m) { craterFloor = m; return this; }
        public Profile feature(Feature f) { features.add(f); return this; }
        public Profile ore(Mat m, float chance, int minDepth, int maxDepth) { ores.add(new Ore(m, chance, minDepth, maxDepth)); return this; }
    }

    private final Profile p;
    private final SimplexNoise noise, detail, lin, dune;
    private final CraterField craters;
    private final double rBlocks;

    public ProfileTerrain(Profile profile, long seed) {
        this.p = profile;
        long s = seed ^ profile.id.hashCode() * 0x9E3779B97F4A7C15L;
        noise = new SimplexNoise(s);
        detail = new SimplexNoise(s * 31 + 7);
        lin = new SimplexNoise(s * 131 + 11);
        dune = new SimplexNoise(s * 1031 + 13);
        rBlocks = profile.radiusKm * 10;
        // lunar crater size-frequency, scaled to the body and cut below 6 blocks
        double[] base = {1600, 760, 360, 170, 80, 38, 18, 8};
        double[] chance = {0.30, 0.45, 0.55, 0.62, 0.66, 0.70, 0.72, 0.72};
        List<double[]> keep = new ArrayList<>();
        double scale = Math.min(1.0, Math.max(0.05, rBlocks / 17374.0));
        for (int i = 0; i < base.length; i++) {
            double d = base[i] * scale;
            if (d >= 6 && d < rBlocks * 1.2) keep.add(new double[]{d, chance[i]});
        }
        double[] ds = new double[keep.size()], cs = new double[keep.size()];
        for (int i = 0; i < ds.length; i++) { ds[i] = keep.get(i)[0]; cs[i] = keep.get(i)[1]; }
        craters = new CraterField(s ^ 0xC2A7E2L, rBlocks, ds, cs, profile.craterDepth);
    }

    @Override public int datumY() { return p.datum; }
    @Override public Mat stone() { return p.rock; }

    @Override
    public void sample(double lat, double lon, Column c) {
        c.reset();
        double la = Math.toRadians(lat), lo = Math.toRadians(lon), cl = Math.cos(la);
        double px = cl * Math.cos(lo), py = Math.sin(la), pz = -cl * Math.sin(lo);
        final double V = V_BLOCKS_PER_KM;
        double h = noise.fbm(px, py, pz, p.freq, 5, 0.55) * p.reliefKm * V;
        h += detail.fbm(px, py, pz, Math.max(12, rBlocks / 90.0), 4, 0.5) * p.hillsKm * V;
        c.top = p.top; c.topDepth = p.topDepth; c.sub = p.sub; c.subDepth = p.subDepth; c.rock = p.rock;

        if (p.craters > 0) {
            CraterField.Hit hit = new CraterField.Hit();
            craters.sample(px, py, pz, p.craters, 1e9, hit);
            h += hit.delta;
            if (p.craterFloor != null && hit.insideDepth > 6) { c.top = p.craterFloor; c.topDepth = 3; }
            c.iron = (float) hit.freshCentre;
        }
        if (p.lineae != null) {
            double f = p.lineaeFreq;
            double n1 = 1 - Math.abs(lin.noise(px * f, py * f, pz * f));
            double n2 = 1 - Math.abs(lin.noise(px * f * 2.3 + 5, py * f * 2.3, pz * f * 2.3));
            double n = Math.max(n1, n2 * 0.995);
            if (n > 0.965) {
                c.top = p.lineae; c.topDepth = 3;
                double t = (n - 0.965) / 0.035;
                h += p.lineaeKm * V * (t < 0.5 ? t * 2 : 2 - t * 2) - (t > 0.8 ? p.lineaeKm * V * 0.6 : 0);
            }
        }
        if (p.dunes != null && Math.abs(lat) < p.duneLat) {
            double mask = dune.fbm(px, py, pz, 6, 3, 0.5);
            if (mask > -0.1) {
                double phase = lo * rBlocks / 18.0 + dune.noise(px * 40, py * 40, pz * 40) * 2.5;
                h += (0.5 + 0.5 * Math.sin(phase)) * 7 * Math.min(1, (mask + 0.1) * 4);
                c.top = p.dunes; c.topDepth = 5;
            }
        }
        for (Feature f : p.features) {
            double x = f.r().x(px, py, pz, p.radiusKm);
            if (x > 1.5) continue;
            double k = f.km() * V;
            switch (f.shape()) {
                case BASIN -> {
                    h -= k * (1 - Region.smoothstep(0.55, 1.0, x));
                    if (x > 0.85 && x < 1.4) h += k * 0.18 * Math.sin((x - 0.85) / 0.55 * Math.PI);
                }
                case DOME -> {
                    if (x < 1) h += k * Math.pow(1 - x, 1.6);
                    if (x < 0.06) h -= k * 0.12 * (1 - x / 0.06);
                }
                case PLATEAU -> h += k * (1 - Region.smoothstep(0.78, 1.0, x));
                case CLIFF -> h += k * (1 - Region.smoothstep(0.97, 1.0, x));
                case PATCH -> {}
            }
            if (x < 1 && f.top() != null) { c.top = f.top(); c.topDepth = 4; }
            if (x < 1 && f.liquid() != null && h < f.liquidKm() * V) { c.liquid = f.liquid(); c.liquidLevel = f.liquidKm() * V; }
        }
        if (p.polar != null && Math.abs(lat) > p.polarLat) {
            double edge = (Math.abs(lat) - p.polarLat) + detail.noise(px * 30, py * 30, pz * 30) * 1.5;
            if (edge > 0) { c.top = p.polar; c.topDepth = 5; c.ice = 1; }
        }
        if (p.sea != null && h < p.seaKm * V && c.liquid == null) { c.liquid = p.sea; c.liquidLevel = p.seaKm * V; }
        if (p.ocean != null) { c.layer = p.ocean; c.layerDepth = p.oceanDepth; c.layerThickness = p.oceanThickness; }
        c.height = h;
        Integer col = TerrainBlocks.COLOR.get(c.liquid != null ? c.liquid : c.top);
        c.color = col == null ? 0x909090 : col;
    }

    @Override
    public double mapShade(Column c) {
        return Math.max(0.55, Math.min(1.2, 1.0 + c.height / (Math.max(1, p.reliefKm) * V_BLOCKS_PER_KM * 4)));
    }

    @Override
    public Mat vein(Column c, RandomSource r, int[] depth) {
        if (p.ores.isEmpty()) return null;
        Ore o = p.ores.get(r.nextInt(p.ores.size()));
        if (r.nextFloat() > o.chance()) return null;
        depth[0] = o.minDepth(); depth[1] = o.maxDepth();
        return o.mat();
    }
}
