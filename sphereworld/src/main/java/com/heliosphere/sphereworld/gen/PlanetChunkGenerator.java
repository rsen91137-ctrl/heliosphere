package com.heliosphere.sphereworld.gen;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.world.PlanetGeometry;
import com.heliosphere.sphereworld.TerrainBlocks;
import com.heliosphere.sphereworld.terrain.Column;
import com.heliosphere.sphereworld.terrain.Mat;
import com.heliosphere.sphereworld.terrain.MarsTerrain;
import com.heliosphere.sphereworld.terrain.MoonTerrain;
import com.heliosphere.sphereworld.terrain.PlanetTerrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Generates a planet's surface from its lat/lon terrain model. Because terrain is sampled on the sphere,
 * the east-west seam and the poles join without discontinuities.
 */
public class PlanetChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PlanetChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.STRING.fieldOf("planet").forGetter(g -> g.planet)
    ).apply(i, i.stable(PlanetChunkGenerator::new)));

    private final String planet;
    private final CelestialBody body;
    private final PlanetGeometry geo;
    private volatile PlanetTerrain terrain;
    private volatile long terrainSeed = Long.MIN_VALUE;

    public PlanetChunkGenerator(BiomeSource biomeSource, String planet) {
        super(biomeSource);
        this.planet = planet;
        this.body = SolarSystem.get(planet);
        this.geo = PlanetGeometry.of(body);
    }

    public CelestialBody body() { return body; }

    public static PlanetTerrain createTerrain(String planet, long seed) {
        return switch (planet) {
            case "mars" -> new MarsTerrain(seed);
            default -> new MoonTerrain(seed);
        };
    }

    public PlanetTerrain terrain(RandomState rs) {
        long seed = rs.getOrCreateRandomFactory(Heliosphere.id("terrain")).at(0, 0, 0).nextLong();
        PlanetTerrain t = terrain;
        if (t == null || seed != terrainSeed) {
            synchronized (this) {
                if (terrain == null || seed != terrainSeed) {
                    terrain = createTerrain(planet, seed);
                    terrainSeed = seed;
                }
                t = terrain;
            }
        }
        return t;
    }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState rs, StructureManager sm, ChunkAccess chunk) {
        PlanetTerrain t = terrain(rs);
        ChunkPos cp = chunk.getPos();
        int minY = chunk.getMinY(), maxY = chunk.getMaxY();
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Column col = new Column();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        RandomSource rand = RandomSource.create(cp.toLong() ^ terrainSeed);
        int[] tops = new int[256];
        Column[] cols = new Column[256];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = cp.getMinBlockX() + lx, wz = cp.getMinBlockZ() + lz;
                double[] ll = geo.latLon(wx + 0.5, wz + 0.5);
                t.sample(ll[0], ll[1], col);
                int top = Mth.clamp(t.datumY() + (int) Math.round(col.height), minY + 6, maxY - 2);
                tops[lx * 16 + lz] = top;
                Column copy = new Column();
                copyInto(col, copy);
                cols[lx * 16 + lz] = copy;
                BlockState topS = TerrainBlocks.state(col.top), subS = TerrainBlocks.state(col.sub), rockS = TerrainBlocks.state(col.rock);
                BlockState layerS = col.layer == null ? null : TerrainBlocks.state(col.layer);
                int bedrockTop = minY + rand.nextInt(4);
                LevelChunkSection section = null;
                int sectionIdx = -1;
                for (int y = minY; y <= top; y++) {
                    int idx = chunk.getSectionIndex(y);
                    if (idx != sectionIdx) { section = chunk.getSection(idx); sectionIdx = idx; }
                    int depth = top - y;
                    BlockState s;
                    if (y <= bedrockTop) s = bedrock;
                    else if (layerS != null && depth >= col.layerDepth && depth < col.layerDepth + col.layerThickness) s = layerS;
                    else if (depth < col.topDepth) s = topS;
                    else if (depth < col.topDepth + col.subDepth) s = subS;
                    else s = rockS;
                    section.setBlockState(lx, y & 15, lz, s, false);
                }
                BlockState topState = TerrainBlocks.state(col.top);
                ocean.update(lx, top, lz, topState);
                surface.update(lx, top, lz, topState);
            }
        }
        placeVeins(chunk, cp, tops, cols, rand);
        return CompletableFuture.completedFuture(chunk);
    }

    private static void copyInto(Column a, Column b) {
        b.height = a.height; b.top = a.top; b.sub = a.sub; b.rock = a.rock; b.kreep = a.kreep; b.highTi = a.highTi;
        b.iron = a.iron; b.ice = a.ice; b.topDepth = a.topDepth; b.subDepth = a.subDepth;
    }

    /** Ore veins whose type follows the region: KREEP in the Procellarum terrane, meteoric iron at fresh craters, etc. */
    private void placeVeins(ChunkAccess chunk, ChunkPos cp, int[] tops, Column[] cols, RandomSource rand) {
        int veins = 6;
        for (int v = 0; v < veins; v++) {
            int lx = rand.nextInt(16), lz = rand.nextInt(16);
            Column c = cols[lx * 16 + lz];
            int top = tops[lx * 16 + lz];
            Mat ore = null;
            int depthMin = 4, depthMax = 50;
            if (planet.equals("moon")) {
                if (c.kreep > 0.3f && rand.nextFloat() < c.kreep) ore = Mat.KREEP;
                else if (c.iron > 0.2f) { ore = Mat.METEORIC_IRON; depthMin = 1; depthMax = 6; }
                else if (c.highTi > 0.3f) { ore = Mat.HIGH_TI_BASALT; depthMax = 25; }
            } else {
                float r = rand.nextFloat();
                if (r < 0.25f) { ore = Mat.HEMATITE; depthMax = 30; }
                else if (r < 0.4f) { ore = Mat.OLIVINE; }
                else if (r < 0.55f) { ore = Mat.GYPSUM; depthMax = 20; }
                else if (r < 0.65f && c.ice > 0) { ore = Mat.MARS_ICE; depthMin = 2; depthMax = 12; }
            }
            if (ore == null) continue;
            int y = top - depthMin - rand.nextInt(Math.max(1, depthMax - depthMin));
            BlockState s = TerrainBlocks.state(ore);
            int size = 4 + rand.nextInt(8);
            int x = lx, z = lz;
            for (int i = 0; i < size; i++) {
                if (y > chunk.getMinY() + 4 && y < top) {
                    chunk.setBlockState(new BlockPos(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z), s);
                }
                x = Mth.clamp(x + rand.nextInt(3) - 1, 0, 15);
                z = Mth.clamp(z + rand.nextInt(3) - 1, 0, 15);
                y += rand.nextInt(3) - 1;
            }
        }
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor h, RandomState rs) {
        PlanetTerrain t = terrain(rs);
        Column c = new Column();
        double[] ll = geo.latLon(x + 0.5, z + 0.5);
        t.sample(ll[0], ll[1], c);
        return Mth.clamp(t.datumY() + (int) Math.round(c.height), h.getMinY() + 6, h.getMaxY() - 2) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor h, RandomState rs) {
        int top = getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, h, rs);
        BlockState[] states = new BlockState[h.getHeight()];
        BlockState stone = TerrainBlocks.state(planet.equals("mars") ? Mat.MARS_ROCK : Mat.ANORTHOSITE);
        for (int i = 0; i < states.length; i++) states[i] = h.getMinY() + i < top ? stone : Blocks.AIR.defaultBlockState();
        return new NoiseColumn(h.getMinY(), states);
    }

    @Override public void buildSurface(WorldGenRegion region, StructureManager sm, RandomState rs, ChunkAccess chunk) {}
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState rs, BiomeManager bm, StructureManager sm, ChunkAccess chunk) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public int getGenDepth() { return planet.equals("mars") ? 1088 : 768; }
    @Override public int getSeaLevel() { return getMinY(); }
    @Override public int getMinY() { return planet.equals("mars") ? -128 : -256; }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState rs, BlockPos pos) {
        double[] ll = geo.latLon(pos.getX(), pos.getZ());
        list.add(String.format("Heliosphere %s  lat %.3f  lon %.3f", body.nameEn, ll[0], ll[1]));
    }
}
