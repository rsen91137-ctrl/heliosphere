package com.heliosphere.sphereworld.gen;

import com.heliosphere.core.Heliosphere;
import com.heliosphere.core.celestial.CelestialBody;
import com.heliosphere.core.celestial.SolarSystem;
import com.heliosphere.core.world.PlanetGeometry;
import com.heliosphere.sphereworld.TerrainBlocks;
import com.heliosphere.sphereworld.terrain.Column;
import com.heliosphere.sphereworld.terrain.Mat;
import com.heliosphere.sphereworld.terrain.GasGiantTerrain;
import com.heliosphere.sphereworld.terrain.MarsTerrain;
import com.heliosphere.sphereworld.terrain.ProfileTerrain;
import com.heliosphere.sphereworld.terrain.WorldProfiles;
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
        CelestialBody b = SolarSystem.get(planet);
        if (planet.equals("mars")) return new MarsTerrain(seed);
        if (planet.equals("moon")) return new MoonTerrain(seed);
        if (b != null && b.gasGiant) return new GasGiantTerrain(planet, seed);
        ProfileTerrain.Profile p = WorldProfiles.get(planet);
        return p != null ? new ProfileTerrain(p, seed) : new MoonTerrain(seed);
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
        if (t instanceof GasGiantTerrain g) return CompletableFuture.completedFuture(fillGas(g, chunk));
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
                if (col.liquid != null) {
                    int level = Mth.clamp(t.datumY() + (int) Math.round(col.liquidLevel), minY + 6, maxY - 2);
                    BlockState liq = TerrainBlocks.state(col.liquid);
                    for (int y = top + 1; y <= level; y++) chunk.setBlockState(new BlockPos(wx, y, wz), liq);
                    if (level > top) surface.update(lx, level, lz, liq);
                }
            }
        }
        placeVeins(t, chunk, cp, tops, cols, rand);
        return CompletableFuture.completedFuture(chunk);
    }

    private static void copyInto(Column a, Column b) {
        b.height = a.height; b.top = a.top; b.sub = a.sub; b.rock = a.rock; b.kreep = a.kreep; b.highTi = a.highTi;
        b.iron = a.iron; b.ice = a.ice; b.topDepth = a.topDepth; b.subDepth = a.subDepth;
        b.liquid = a.liquid; b.liquidLevel = a.liquidLevel; b.layer = a.layer; b.layerDepth = a.layerDepth; b.layerThickness = a.layerThickness;
    }

    /** Ore veins whose type follows the region: KREEP in the Procellarum terrane, meteoric iron at fresh craters, etc. */
    private ChunkAccess fillGas(GasGiantTerrain g, ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        int minY = chunk.getMinY(), maxY = chunk.getMaxY();
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        double[][] pads = GasGiantTerrain.platforms(planet);
        BlockState deck = TerrainBlocks.state(Mat.AEROSTAT_DECK);
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int wx = cp.getMinBlockX() + lx, wz = cp.getMinBlockZ() + lz;
            double[] ll = geo.latLon(wx + 0.5, wz + 0.5);
            for (int y = Math.max(minY, 160); y <= Math.min(maxY, 340); y++) {
                Mat m = g.cloud(ll[0], ll[1], wx, y, wz);
                if (m != null) chunk.setBlockState(new BlockPos(wx, y, wz), TerrainBlocks.state(m));
            }
            for (double[] pad : pads) {
                double px = geo.lonToX(pad[1]), pz = geo.latToZ(pad[0]);
                double dx = Math.abs(wx + 0.5 - px), dz = Math.abs(wz + 0.5 - pz);
                int H = GasGiantTerrain.PLATFORM_HALF;
                if (dx <= H + 0.5 && dz <= H + 0.5) {
                    for (int y = GasGiantTerrain.DECK_Y - 1; y <= GasGiantTerrain.DECK_Y + 30; y++) chunk.setBlockState(new BlockPos(wx, y, wz), Blocks.AIR.defaultBlockState());
                    chunk.setBlockState(new BlockPos(wx, GasGiantTerrain.DECK_Y, wz), deck);
                    boolean rim = dx > H - 0.5 || dz > H - 0.5;
                    boolean gate = dx < 1.5 || dz < 1.5;
                    if (rim && !gate) chunk.setBlockState(new BlockPos(wx, GasGiantTerrain.DECK_Y + 1, wz), Blocks.IRON_BARS.defaultBlockState());
                    if (dx < 1 && dz < 1) chunk.setBlockState(new BlockPos(wx, GasGiantTerrain.DECK_Y - 1, wz), Blocks.SEA_LANTERN.defaultBlockState());
                    surface.update(lx, GasGiantTerrain.DECK_Y, lz, deck);
                    ocean.update(lx, GasGiantTerrain.DECK_Y, lz, deck);
                }
            }
        }
        return chunk;
    }

    /** Nearest aerostat deck column, used for spawn heights on the giants. */
    private boolean onPlatform(int x, int z) {
        for (double[] pad : GasGiantTerrain.platforms(planet)) {
            if (Math.abs(x + 0.5 - geo.lonToX(pad[1])) <= GasGiantTerrain.PLATFORM_HALF + 0.5
                    && Math.abs(z + 0.5 - geo.latToZ(pad[0])) <= GasGiantTerrain.PLATFORM_HALF + 0.5) return true;
        }
        return false;
    }

    private void placeVeins(PlanetTerrain t, ChunkAccess chunk, ChunkPos cp, int[] tops, Column[] cols, RandomSource rand) {
        int veins = 6;
        for (int v = 0; v < veins; v++) {
            int lx = rand.nextInt(16), lz = rand.nextInt(16);
            Column c = cols[lx * 16 + lz];
            int top = tops[lx * 16 + lz];
            Mat ore = null;
            int depthMin = 4, depthMax = 50;
            if (!planet.equals("moon") && !planet.equals("mars")) {
                int[] d = {depthMin, depthMax};
                ore = t.vein(c, rand, d);
                depthMin = d[0]; depthMax = d[1];
            } else if (planet.equals("moon")) {
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
        if (t instanceof GasGiantTerrain) return onPlatform(x, z) ? GasGiantTerrain.DECK_Y + 1 : h.getMinY();
        Column c = new Column();
        double[] ll = geo.latLon(x + 0.5, z + 0.5);
        t.sample(ll[0], ll[1], c);
        return Mth.clamp(t.datumY() + (int) Math.round(c.height), h.getMinY() + 6, h.getMaxY() - 2) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor h, RandomState rs) {
        int top = getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, h, rs);
        BlockState[] states = new BlockState[h.getHeight()];
        Mat sm = terrain(rs).stone();
        BlockState stone = TerrainBlocks.state(planet.equals("mars") ? Mat.MARS_ROCK : sm == null ? Mat.ANORTHOSITE : sm);
        for (int i = 0; i < states.length; i++) states[i] = h.getMinY() + i < top ? stone : Blocks.AIR.defaultBlockState();
        return new NoiseColumn(h.getMinY(), states);
    }

    @Override public void buildSurface(WorldGenRegion region, StructureManager sm, RandomState rs, ChunkAccess chunk) {}
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState rs, BiomeManager bm, StructureManager sm, ChunkAccess chunk) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public int getGenDepth() { return planet.equals("mars") ? 1088 : planet.equals("moon") ? 768 : body != null && body.gasGiant ? 512 : 1280; }
    @Override public int getSeaLevel() { return getMinY(); }
    @Override public int getMinY() { return planet.equals("mars") ? -128 : body != null && body.gasGiant ? -64 : -256; }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState rs, BlockPos pos) {
        double[] ll = geo.latLon(pos.getX(), pos.getZ());
        list.add(String.format("Heliosphere %s  lat %.3f  lon %.3f", body.nameEn, ll[0], ll[1]));
    }
}
