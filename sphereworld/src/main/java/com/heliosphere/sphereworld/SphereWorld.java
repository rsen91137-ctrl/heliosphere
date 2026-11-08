package com.heliosphere.sphereworld;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.env.Planets;
import com.heliosphere.core.net.HelioNetwork;
import com.heliosphere.core.world.PlanetGeometry;
import com.heliosphere.sphereworld.gen.EarthBiomeSource;
import com.heliosphere.sphereworld.gen.PlanetChunkGenerator;
import com.heliosphere.sphereworld.terrain.Column;
import com.heliosphere.sphereworld.terrain.PlanetTerrain;
import com.heliosphere.sphereworld.terrain.SimplexNoise;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Spherical worlds: the finite Earth preset, the Moon and Mars generators and their orbital maps. */
public final class SphereWorld implements ModInitializer {
    @Override
    public void onInitialize() {
        com.heliosphere.core.Heliosphere.bootstrap();
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Heliosphere.id("planet"), PlanetChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Heliosphere.id("earth"), EarthBiomeSource.CODEC);
        TerrainBlocks.init();
        Planets.setEarthSphereCheck(SphereWorld::isSphericalEarth);
        HelioNetwork.registerMapBaker("moon", s -> bakePlanet(s, Planets.MOON));
        HelioNetwork.registerMapBaker("mars", s -> bakePlanet(s, Planets.MARS));
        HelioNetwork.registerMapBaker("earth", SphereWorld::bakeEarth);
    }

    public static boolean isSphericalEarth(Level level) {
        return level instanceof ServerLevel sl && level.dimension() == Level.OVERWORLD
                && sl.getChunkSource().getGenerator().getBiomeSource() instanceof EarthBiomeSource;
    }

    private static int[] bakePlanet(MinecraftServer server, net.minecraft.resources.ResourceKey<Level> dim) {
        ServerLevel level = server.getLevel(dim);
        if (level == null || !(level.getChunkSource().getGenerator() instanceof PlanetChunkGenerator gen)) throw new IllegalStateException("no " + dim);
        PlanetTerrain t = gen.terrain(level.getChunkSource().randomState());
        int w = HelioNetwork.MAP_W, h = HelioNetwork.MAP_H;
        int[] out = new int[w * h];
        Column c = new Column();
        for (int y = 0; y < h; y++) {
            double lat = 90 - (y + 0.5) * 180.0 / h;
            for (int x = 0; x < w; x++) {
                double lon = -180 + (x + 0.5) * 360.0 / w;
                t.sample(lat, lon, c);
                out[y * w + x] = shade(c.color, t.mapShade(c));
            }
        }
        return out;
    }

    private static int[] bakeEarth(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        BiomeSource src = gen.getBiomeSource();
        RandomState rs = level.getChunkSource().randomState();
        PlanetGeometry geo = PlanetGeometry.of(SolarSystem.EARTH);
        SimplexNoise clouds = new SimplexNoise(level.getSeed() ^ 0xC10D5L);
        int w = HelioNetwork.MAP_W, h = HelioNetwork.MAP_H;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            double lat = 90 - (y + 0.5) * 180.0 / h;
            for (int x = 0; x < w; x++) {
                double lon = -180 + (x + 0.5) * 360.0 / w;
                int bx = (int) geo.lonToX(lon), bz = (int) geo.latToZ(lat);
                Holder<Biome> b = src.getNoiseBiome(bx >> 2, 63 >> 2, bz >> 2, rs.sampler());
                int col = biomeColor(b);
                double la = Math.toRadians(lat), lo = Math.toRadians(lon);
                double cx = Math.cos(la) * Math.cos(lo), cy = Math.sin(la), cz = Math.cos(la) * Math.sin(lo);
                double cl = clouds.fbm(cx, cy, cz, 3.0, 5, 0.55);
                if (cl > 0.12) col = mix(col, 0xF4F6F8, Math.min(0.85, (cl - 0.12) * 3));
                out[y * w + x] = col;
            }
        }
        return out;
    }

    private static int biomeColor(Holder<Biome> b) {
        String id = b.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
        if (id.contains("deep") && id.contains("ocean")) return 0x1A3D78;
        if (id.contains("frozen") && id.contains("ocean")) return 0x8FA8C8;
        if (id.contains("ocean") || id.contains("river")) return 0x2557A0;
        if (id.contains("ice") || id.contains("snowy") || id.contains("frozen") || id.contains("peaks")) return 0xE8EEF2;
        if (id.contains("desert") || id.contains("beach")) return 0xD8C48A;
        if (id.contains("badlands")) return 0xB8643A;
        if (id.contains("jungle") || id.contains("bamboo")) return 0x2E6B24;
        if (id.contains("savanna")) return 0x9E9A4A;
        if (id.contains("swamp") || id.contains("mangrove")) return 0x4A5E32;
        if (id.contains("taiga") || id.contains("grove")) return 0x3A5A3E;
        if (id.contains("mushroom")) return 0x7A5A80;
        if (id.contains("stony") || id.contains("windswept") || id.contains("slopes")) return 0x7A7A72;
        if (id.contains("forest") || id.contains("birch")) return 0x3E7A34;
        return 0x6A9A48;
    }

    private static int shade(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), g = (int) Math.min(255, ((rgb >> 8) & 255) * f), b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
